// JNI bridge between com.bradflaugher.pokersi.llm.LlamaNative and llama.cpp.
// The inference itself lives in pokersi-llm.h (shared with the desktop
// harness in tools/llm-harness); this file only converts between Java and C++.
//
// Calls on one Engine must not overlap; the Kotlin side runs them all on a
// single thread. cancel() is the exception and may be called from anywhere.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>
#include <vector>

#define TAG "PokerSI-llama"
#define POKERSI_LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define POKERSI_LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define POKERSI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#include "ggml-backend.h"
#include "pokersi-llm.h"

using pokersi::Engine;

namespace {

std::atomic<bool> backends_loaded{false};

void log_callback(ggml_log_level level, const char *text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) {
        __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

// Java strings to standard UTF-8. GetStringUTFChars would give "modified UTF-8"
// (emoji as two 3-byte surrogates, NUL as C0 80), which the tokenizer reads as
// garbage, so convert from UTF-16 ourselves.
std::string to_string(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const jsize len = env->GetStringLength(s);
    const jchar *chars = env->GetStringChars(s, nullptr);
    if (chars == nullptr) return {};  // OutOfMemoryError pending
    std::string out;
    out.reserve(static_cast<size_t>(len) + 8);
    for (jsize i = 0; i < len; i++) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[i + 1] - 0xDC00);
            i++;
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;  // lone surrogate
        }
        if (cp < 0x80) {
            out += static_cast<char>(cp);
        } else if (cp < 0x800) {
            out += static_cast<char>(0xC0 | (cp >> 6));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out += static_cast<char>(0xE0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (cp >> 18));
            out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }
    env->ReleaseStringChars(s, chars);
    return out;
}

jstring new_jstring(JNIEnv *env, const std::string &s) {
    // Build from UTF-16 so 4-byte sequences (emoji) survive.
    std::string clean = pokersi::valid_utf8_prefix(s);
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
    POKERSI_LOGI("llama.cpp backends loaded from %s", dir.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_loadModel(JNIEnv *env, jobject, jstring jpath, jint n_threads,
                                                        jint n_ctx) {
    try {
        return reinterpret_cast<jlong>(pokersi::load(to_string(env, jpath), n_threads, n_ctx));
    } catch (const std::exception &e) {
        // Never let a C++ exception unwind through the JNI frame (that aborts the app).
        POKERSI_LOGE("load failed: %s", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    pokersi::free_engine(reinterpret_cast<Engine *>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bradflaugher_pokersi_llm_LlamaNative_describe(JNIEnv *env, jobject, jlong handle) {
    try {
        return new_jstring(env, pokersi::describe(reinterpret_cast<Engine *>(handle)));
    } catch (const std::exception &e) {
        return new_jstring(env, "");
    }
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

    std::vector<common_chat_msg> messages;
    jsize count = env->GetArrayLength(roles);
    if (contents == nullptr || env->GetArrayLength(contents) != count) return new_jstring(env, "");
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

    jmethodID on_token = nullptr;
    if (callback != nullptr) {
        jclass cls = env->GetObjectClass(callback);
        on_token = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cls);
    }

    bool threw = false;
    std::function<bool(const std::string &)> on_text;
    if (on_token != nullptr) {
        on_text = [&](const std::string &piece) {
            jstring jpiece = new_jstring(env, piece);
            if (jpiece == nullptr) {  // OutOfMemoryError pending
                threw = true;
                return false;
            }
            jboolean keep_going = env->CallBooleanMethod(callback, on_token, jpiece);
            env->DeleteLocalRef(jpiece);
            if (env->ExceptionCheck()) {
                threw = true;  // rethrown on the Kotlin side when we return
                return false;
            }
            return keep_going == JNI_TRUE;
        };
    }

    std::string output;
    try {
        output = pokersi::generate(engine, messages, to_string(env, jgrammar), max_tokens, temperature,
                                   static_cast<uint32_t>(seed), on_text);
    } catch (const std::exception &e) {
        // Never let a C++ exception unwind through the JNI frame (that aborts the app).
        POKERSI_LOGE("generate failed: %s", e.what());
    }
    if (threw) return nullptr;
    return new_jstring(env, output);
}
