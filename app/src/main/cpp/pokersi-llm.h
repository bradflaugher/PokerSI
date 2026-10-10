// PokerSI's llama.cpp glue, free of JNI so the desktop harness
// (tools/llm-harness) runs exactly the code the app does.
//
// One Engine per loaded model: the model, one context, and the model's chat
// templates. Every generate() call starts from an empty KV cache (each poker
// decision is a fresh, self-contained prompt), formats the messages with the
// model's own chat template (thinking off), and samples with an optional GBNF
// grammar so the reply is always machine-readable.
//
// Calls on one Engine must not overlap. cancel() is the exception and may be
// called from any thread.
//
// Define POKERSI_LOGI/LOGW/LOGE before including to route the logs.

#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "llama.h"
#include "common.h"
#include "chat.h"

#ifndef POKERSI_LOGI
#define POKERSI_LOGI(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#define POKERSI_LOGW(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#define POKERSI_LOGE(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace pokersi {

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    common_chat_templates_ptr templates;
    std::atomic<bool> cancelled{false};
};

/** Timings of the last generate() call, for the harness and logs. */
struct GenerateStats {
    int prompt_tokens = 0;
    int generated_tokens = 0;
    double prompt_ms = 0;
    double generate_ms = 0;
    int grammar_resamples = 0;  // tokens where the free pick broke the grammar
};

// Java's modified UTF-8 can't take every byte sequence a model emits (a piece
// may end mid-character). Keep only complete, valid UTF-8 sequences.
inline std::string valid_utf8_prefix(const std::string &s) {
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

inline common_chat_templates_ptr init_templates(llama_model *model) {
    try {
        return common_chat_templates_init(model, "");
    } catch (const std::exception &e) {
        POKERSI_LOGW("model chat template unusable (%s), falling back to ChatML", e.what());
    }
    try {
        return common_chat_templates_init(model, "chatml");
    } catch (const std::exception &e) {
        POKERSI_LOGE("ChatML template failed too (%s)", e.what());
    }
    return nullptr;
}

/** [path] is a filesystem path or "fd:N". Returns null on failure. */
inline Engine *load(const std::string &path, int n_threads, int n_ctx) {
    llama_model_params mparams = llama_model_default_params();
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;  // also works for "fd:N" in the android fork
    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        POKERSI_LOGE("could not load model %s", path.c_str());
        return nullptr;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        POKERSI_LOGE("could not create a context for %s", path.c_str());
        llama_model_free(model);
        return nullptr;
    }
    auto *engine = new Engine();
    engine->model = model;
    engine->ctx = ctx;
    engine->templates = init_templates(model);
    POKERSI_LOGI("loaded %s", path.c_str());
    return engine;
}

inline void free_engine(Engine *engine) {
    if (engine == nullptr) return;
    engine->templates.reset();
    if (engine->ctx) llama_free(engine->ctx);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
}

inline std::string describe(Engine *engine) {
    if (engine == nullptr) return {};
    char buf[256];
    llama_model_desc(engine->model, buf, sizeof(buf));
    return buf;
}

inline std::string apply_template(Engine *engine, const std::vector<common_chat_msg> &messages) {
    if (engine->templates) {
        common_chat_templates_inputs inputs;
        inputs.messages = messages;
        inputs.add_generation_prompt = true;
        inputs.enable_thinking = false;
        inputs.use_jinja = true;
        try {
            return common_chat_templates_apply(engine->templates.get(), inputs).prompt;
        } catch (const std::exception &e) {
            POKERSI_LOGW("jinja chat template failed (%s), trying the built-in templates", e.what());
        }
        inputs.use_jinja = false;
        try {
            return common_chat_templates_apply(engine->templates.get(), inputs).prompt;
        } catch (const std::exception &e) {
            POKERSI_LOGW("built-in chat template failed (%s), using ChatML", e.what());
        }
    }
    std::string prompt;
    for (const auto &m : messages) {
        prompt += "<|im_start|>" + m.role + "\n" + m.content + "<|im_end|>\n";
    }
    prompt += "<|im_start|>assistant\n";
    return prompt;
}

inline double ms_since(const int64_t start_us) { return (ggml_time_us() - start_us) / 1000.0; }

/**
 * Samples one token. Like llama.cpp's common_sampler: sample from the plain
 * chain first and only run the (slow, whole-vocabulary) grammar when the
 * pick breaks it. Accepts the token into both samplers.
 */
inline llama_token sample(llama_context *ctx, llama_sampler *chain, llama_sampler *grammar,
                          std::vector<llama_token_data> &cur, int *resamples = nullptr) {
    const float *logits = llama_get_logits_ith(ctx, -1);
    const int n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(llama_get_model(ctx)));
    auto fill = [&]() {
        cur.resize(n_vocab);
        for (llama_token id = 0; id < n_vocab; id++) cur[id] = llama_token_data{id, logits[id], 0.0f};
    };
    fill();
    llama_token_data_array cur_p = {cur.data(), cur.size(), -1, false};
    llama_sampler_apply(chain, &cur_p);
    llama_token token = cur_p.data[cur_p.selected].id;
    if (grammar != nullptr) {
        llama_token_data single = {token, 1.0f, 0.0f};
        llama_token_data_array single_p = {&single, 1, -1, false};
        llama_sampler_apply(grammar, &single_p);
        if (std::isinf(single_p.data[0].logit)) {
            if (resamples) (*resamples)++;
            fill();
            cur_p = {cur.data(), cur.size(), -1, false};
            llama_sampler_apply(grammar, &cur_p);
            llama_sampler_apply(chain, &cur_p);
            token = cur_p.data[cur_p.selected].id;
        }
        llama_sampler_accept(grammar, token);
    }
    llama_sampler_accept(chain, token);
    return token;
}

/**
 * Generates a reply to [messages]. [grammar] is GBNF with a `root` rule, or
 * empty for free text. [on_text] gets each new chunk of complete UTF-8 text
 * and returns false to stop. Returns the whole reply ("" on failure).
 */
inline std::string generate(Engine *engine, const std::vector<common_chat_msg> &messages, const std::string &grammar,
                            int max_tokens, float temperature, uint32_t seed,
                            const std::function<bool(const std::string &)> &on_text,
                            GenerateStats *stats = nullptr) {
    GenerateStats local_stats;
    if (stats == nullptr) stats = &local_stats;
    *stats = {};
    if (engine == nullptr) return {};
    engine->cancelled.store(false);

    const llama_vocab *vocab = llama_model_get_vocab(engine->model);
    std::string prompt = apply_template(engine, messages);
    std::vector<llama_token> tokens = common_tokenize(vocab, prompt, /*add_special*/ true, /*parse_special*/ true);

    const int n_ctx = static_cast<int>(llama_n_ctx(engine->ctx));
    if (static_cast<int>(tokens.size()) + max_tokens > n_ctx) {
        // Keep the head (system prompt) and the tail (the question); the middle
        // is table chatter the decision can live without.
        int keep = n_ctx - max_tokens - 8;
        if (keep < 64) {
            POKERSI_LOGE("prompt does not fit: %zu tokens, context %d", tokens.size(), n_ctx);
            return {};
        }
        int head = keep / 3;
        std::vector<llama_token> trimmed(tokens.begin(), tokens.begin() + head);
        trimmed.insert(trimmed.end(), tokens.end() - (keep - head), tokens.end());
        tokens.swap(trimmed);
    }
    stats->prompt_tokens = static_cast<int>(tokens.size());

    llama_memory_clear(llama_get_memory(engine->ctx), true);

    int64_t start = ggml_time_us();
    const int n_batch = static_cast<int>(llama_n_batch(engine->ctx));
    for (size_t pos = 0; pos < tokens.size(); pos += n_batch) {
        int n = std::min(n_batch, static_cast<int>(tokens.size() - pos));
        llama_batch batch = llama_batch_get_one(tokens.data() + pos, n);
        if (llama_decode(engine->ctx, batch) != 0) {
            POKERSI_LOGE("prompt decode failed");
            return {};
        }
        if (engine->cancelled.load()) return {};
    }
    stats->prompt_ms = ms_since(start);

    // Samplers are freed on every way out, exceptions included.
    struct SamplerDeleter {
        void operator()(llama_sampler *s) const { if (s != nullptr) llama_sampler_free(s); }
    };
    std::unique_ptr<llama_sampler, SamplerDeleter> grammar_owner;
    if (!grammar.empty()) {
        grammar_owner.reset(llama_sampler_init_grammar(vocab, grammar.c_str(), "root"));
        if (!grammar_owner) POKERSI_LOGW("grammar failed to parse; generating free text");
    }
    llama_sampler *grammar_sampler = grammar_owner.get();
    std::unique_ptr<llama_sampler, SamplerDeleter> chain_owner(llama_sampler_chain_init(llama_sampler_chain_default_params()));
    llama_sampler *chain = chain_owner.get();
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(seed));

    std::string output;
    size_t emitted = 0;
    std::vector<llama_token_data> cur;
    start = ggml_time_us();
    for (int i = 0; i < max_tokens; i++) {
        if (engine->cancelled.load()) break;
        llama_token token;
        try {
            token = sample(engine->ctx, chain, grammar_sampler, cur, &stats->grammar_resamples);
        } catch (const std::exception &e) {
            // The grammar throws when it has no way forward; keep what we have.
            POKERSI_LOGW("sampling stopped: %s", e.what());
            break;
        }
        if (llama_vocab_is_eog(vocab, token)) break;
        output += common_token_to_piece(vocab, token, /*special*/ false);
        stats->generated_tokens++;

        if (on_text) {
            std::string ready = valid_utf8_prefix(output);
            if (ready.size() > emitted) {
                std::string piece = ready.substr(emitted);
                emitted = ready.size();
                if (!on_text(piece)) break;
            }
        }

        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(engine->ctx, batch) != 0) {
            POKERSI_LOGE("decode failed after %d tokens", i);
            break;
        }
    }
    stats->generate_ms = ms_since(start);
    return output;
}

}  // namespace pokersi
