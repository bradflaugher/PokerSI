// JNI bridge between com.bradflaugher.pokersi.llm.LlamaNative and llama.cpp.
//
// One Engine per loaded model: the model, one context, and the model's chat
// templates. Every generate() call starts from an empty KV cache (each poker
// decision is a fresh, self-contained prompt), formats the messages with the
// model's own chat template (thinking off), and samples with an optional GBNF
// grammar so the reply is always machine-readable.
//
// Calls on one Engine must not overlap; the Kotlin side runs them all on a
// single thread. cancel() is the exception and may be called from anywhere.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "common.h"
#include "chat.h"

#define TAG "PokerSI-llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    common_chat_templates_ptr templates;
    std::atomic<bool> cancelled{false};
};

std::atomic<bool> backends_loaded{false};

void log_callback(ggml_log_level level, const char *text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) {
        __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

std::string to_string(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Java's modified UTF-8 can't take every byte sequence a model emits (a piece
// may end mid-character). Keep only complete, valid UTF-8 sequences.
std::string valid_utf8_prefix(const std::string &s) {
    size_t i = 0, last_good = 0;
    while (i < s.size()) {
        unsigned char c = s[i];
        size_t len = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 0;
        if (len == 0) { i++; last_good = i; continue; }  // stray byte: skip it
        if (i + len > s.size()) break;
        i += len;
        last_good = i;
    }
    return s.substr(0, last_good);
}

jstring new_jstring(JNIEnv *env, const std::string &s) {
    // Build from UTF-16 so 4-byte sequences (emoji) survive.
    std::string clean = valid_utf8_prefix(s);
    std::u16string utf16;
    utf16.reserve(clean.size());
    for (size_t i = 0; i < clean.size();) {
        unsigned char c = clean[i];
        uint32_t cp;
        size_t len;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c >> 5) == 0x6) { cp = c & 0x1F; len = 2; }
        else if ((c >> 4) == 0xE) { cp = c & 0x0F; len = 3; }
        else if ((c >> 3) == 0x1E) { cp = c & 0x07; len = 4; }
        else { i++; continue; }
        for (size_t k = 1; k < len; k++) cp = (cp << 6) | (clean[i + k] & 0x3F);
        i += len;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            utf16.push_back(static_cast<char16_t>(cp));
        }
    }
    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
}

std::string apply_template(Engine *engine, const std::vector<common_chat_msg> &messages) {
    common_chat_templates_inputs inputs;
    inputs.messages = messages;
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = false;
    inputs.use_jinja = true;
    try {
        return common_chat_templates_apply(engine->templates.get(), inputs).prompt;
    } catch (const std::exception &e) {
        LOGW("jinja chat template failed (%s), trying the built-in templates", e.what());
    }
    inputs.use_jinja = false;
    try {
        return common_chat_templates_apply(engine->templates.get(), inputs).prompt;
    } catch (const std::exception &e) {
        LOGW("built-in chat template failed (%s), using ChatML", e.what());
    }
    std::string prompt;
    for (const auto &m : messages) {
        prompt += "<|im_start|>" + m.role + "\n" + m.content + "<|im_end|>\n";
    }
    prompt += "<|im_start|>assistant\n";
    return prompt;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_init(JNIEnv *env, jobject, jstring native_lib_dir) {
    if (backends_loaded.exchange(true)) return;
    llama_log_set(log_callback, nullptr);
    std::string dir = to_string(env, native_lib_dir);
    if (!dir.empty()) {
        ggml_backend_load_all_from_path(dir.c_str());
    } else {
        ggml_backend_load_all();
    }
    llama_backend_init();
    LOGI("llama.cpp backends loaded from %s", dir.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_loadModel(JNIEnv *env, jobject, jstring jpath, jint n_threads,
                                                        jint n_ctx) {
    std::string path = to_string(env, jpath);
    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap = true;
    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("could not load model %s", path.c_str());
        return 0;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("could not create a context for %s", path.c_str());
        llama_model_free(model);
        return 0;
    }
    auto *engine = new Engine();
    engine->model = model;
    engine->ctx = ctx;
    try {
        engine->templates = common_chat_templates_init(model, "");
    } catch (const std::exception &e) {
        LOGW("model chat template unusable (%s), falling back to ChatML", e.what());
        engine->templates = common_chat_templates_init(model, "chatml");
    }
    LOGI("loaded %s", path.c_str());
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    engine->templates.reset();
    if (engine->ctx) llama_free(engine->ctx);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_describe(JNIEnv *env, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return new_jstring(env, "");
    char buf[256];
    llama_model_desc(engine->model, buf, sizeof(buf));
    return new_jstring(env, buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) engine->cancelled.store(true);
}

// roles/contents: parallel arrays of chat messages ("system", "user", "assistant").
// grammar: GBNF with a `root` rule, or null/empty for free text.
// callback: optional TokenCallback; onToken(text) returning false stops generation.
extern "C" JNIEXPORT jstring JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_generate(JNIEnv *env, jobject, jlong handle, jobjectArray roles,
                                                       jobjectArray contents, jstring jgrammar, jint max_tokens,
                                                       jfloat temperature, jint seed, jobject callback) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return new_jstring(env, "");
    engine->cancelled.store(false);

    std::vector<common_chat_msg> messages;
    jsize count = env->GetArrayLength(roles);
    for (jsize i = 0; i < count; i++) {
        auto jrole = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto jcontent = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        common_chat_msg msg;
        msg.role = to_string(env, jrole);
        msg.content = to_string(env, jcontent);
        messages.push_back(msg);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    const llama_vocab *vocab = llama_model_get_vocab(engine->model);
    std::string prompt = apply_template(engine, messages);
    std::vector<llama_token> tokens = common_tokenize(vocab, prompt, /*add_special*/ true, /*parse_special*/ true);

    const int n_ctx = static_cast<int>(llama_n_ctx(engine->ctx));
    if (static_cast<int>(tokens.size()) + max_tokens > n_ctx) {
        // Keep the head (system prompt) and the tail (the question); the middle
        // is table chatter the decision can live without.
        int keep = n_ctx - max_tokens - 8;
        if (keep < 64) {
            LOGE("prompt does not fit: %zu tokens, context %d", tokens.size(), n_ctx);
            return new_jstring(env, "");
        }
        int head = keep / 3;
        std::vector<llama_token> trimmed(tokens.begin(), tokens.begin() + head);
        trimmed.insert(trimmed.end(), tokens.end() - (keep - head), tokens.end());
        tokens.swap(trimmed);
    }

    llama_memory_clear(llama_get_memory(engine->ctx), true);

    const int n_batch = static_cast<int>(llama_n_batch(engine->ctx));
    for (size_t pos = 0; pos < tokens.size(); pos += n_batch) {
        int n = std::min(n_batch, static_cast<int>(tokens.size() - pos));
        llama_batch batch = llama_batch_get_one(tokens.data() + pos, n);
        if (llama_decode(engine->ctx, batch) != 0) {
            LOGE("prompt decode failed");
            return new_jstring(env, "");
        }
        if (engine->cancelled.load()) return new_jstring(env, "");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    std::string grammar = to_string(env, jgrammar);
    if (!grammar.empty()) {
        llama_sampler *g = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (g != nullptr) {
            llama_sampler_chain_add(sampler, g);
        } else {
            LOGW("grammar failed to parse; generating free text");
        }
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));

    jmethodID on_token = nullptr;
    if (callback != nullptr) {
        jclass cls = env->GetObjectClass(callback);
        on_token = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cls);
    }

    std::string output;
    size_t emitted = 0;
    for (int i = 0; i < max_tokens; i++) {
        if (engine->cancelled.load()) break;
        llama_token token = llama_sampler_sample(sampler, engine->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(sampler, token);
        output += common_token_to_piece(vocab, token, /*special*/ false);

        if (on_token != nullptr) {
            std::string ready = valid_utf8_prefix(output);
            if (ready.size() > emitted) {
                jstring piece = new_jstring(env, ready.substr(emitted));
                emitted = ready.size();
                jboolean keep_going = env->CallBooleanMethod(callback, on_token, piece);
                env->DeleteLocalRef(piece);
                if (env->ExceptionCheck()) {
                    llama_sampler_free(sampler);
                    return nullptr;  // rethrown on the Kotlin side
                }
                if (!keep_going) break;
            }
        }

        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(engine->ctx, batch) != 0) {
            LOGE("decode failed after %d tokens", i);
            break;
        }
    }
    llama_sampler_free(sampler);
    return new_jstring(env, output);
}
