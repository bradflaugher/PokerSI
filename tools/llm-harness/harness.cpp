// Desktop harness for PokerSI's on-device LLM layer.
//
// Runs prompt cases dumped by the app's JVM tests (PromptDumpTest) through
// pokersi-llm.h, the same inference code the app's JNI bridge uses, and
// reports speed and whether each reply is valid JSON.
//
// A case is three files sharing a name in one directory:
//   <name>.system.txt  system message
//   <name>.user.txt    user message
//   <name>.gbnf        grammar (optional)
// Each reply is written to <out>/<name>.reply.txt.
//
// usage: pokersi-harness -m model.gguf -d cases/ [-o out/] [-t threads]
//                        [--temp 0.8] [--max 96] [--seed 1] [--runs 1] [--no-grammar]

#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "pokersi-llm.h"

namespace fs = std::filesystem;

namespace {

std::string read_file(const fs::path &p) {
    std::ifstream in(p, std::ios::binary);
    std::stringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

void quiet_log(ggml_log_level level, const char *text, void *) {
    if (level >= GGML_LOG_LEVEL_ERROR) std::fputs(text, stderr);
}

[[noreturn]] void usage() {
    std::fprintf(stderr,
                 "usage: pokersi-harness -m model.gguf -d cases/ [-o out/] [-t threads] [--temp 0.8] "
                 "[--max 96] [--seed 1] [--runs 1] [--no-grammar] [--verbose]\n");
    std::exit(2);
}

}  // namespace

int main(int argc, char **argv) {
    std::string model_path, cases_dir, out_dir;
    int threads = 4, max_tokens = 96, runs = 1;
    uint32_t seed = 1;
    float temp = 0.8f;
    bool use_grammar = true, verbose = false;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        auto next = [&]() -> std::string { if (i + 1 >= argc) usage(); return argv[++i]; };
        if (a == "-m") model_path = next();
        else if (a == "-d") cases_dir = next();
        else if (a == "-o") out_dir = next();
        else if (a == "-t") threads = std::stoi(next());
        else if (a == "--temp") temp = std::stof(next());
        else if (a == "--max") max_tokens = std::stoi(next());
        else if (a == "--seed") seed = static_cast<uint32_t>(std::stoul(next()));
        else if (a == "--runs") runs = std::stoi(next());
        else if (a == "--no-grammar") use_grammar = false;
        else if (a == "--verbose") verbose = true;
        else usage();
    }
    if (model_path.empty() || cases_dir.empty()) usage();
    if (out_dir.empty()) out_dir = cases_dir + "/out";
    fs::create_directories(out_dir);

    if (!verbose) llama_log_set(quiet_log, nullptr);
    ggml_backend_load_all();
    llama_backend_init();

    int64_t t0 = ggml_time_us();
    pokersi::Engine *engine = pokersi::load(model_path, threads, 4096);
    if (engine == nullptr) return 1;
    std::printf("model: %s (%s), load %.0f ms\n", fs::path(model_path).filename().c_str(),
                pokersi::describe(engine).c_str(), pokersi::ms_since(t0));

    std::vector<fs::path> cases;
    for (const auto &e : fs::directory_iterator(cases_dir)) {
        std::string name = e.path().filename().string();
        const std::string suffix = ".user.txt";
        if (name.size() > suffix.size() && name.compare(name.size() - suffix.size(), suffix.size(), suffix) == 0) {
            cases.push_back(e.path().parent_path() / name.substr(0, name.size() - suffix.size()));
        }
    }
    std::sort(cases.begin(), cases.end());

    int total = 0, valid = 0, prompt_tokens = 0, gen_tokens = 0;
    double prompt_ms = 0, gen_ms = 0;
    for (const auto &base : cases) {
        std::string name = base.filename().string();
        std::vector<common_chat_msg> messages(2);
        messages[0].role = "system";
        messages[0].content = read_file(base.string() + ".system.txt");
        messages[1].role = "user";
        messages[1].content = read_file(base.string() + ".user.txt");
        std::string grammar = use_grammar && fs::exists(base.string() + ".gbnf") ? read_file(base.string() + ".gbnf") : "";

        std::ofstream reply_file(fs::path(out_dir) / (name + ".reply.txt"), std::ios::binary);
        for (int r = 0; r < runs; r++) {
            pokersi::GenerateStats stats;
            std::string reply = pokersi::generate(engine, messages, grammar, max_tokens, temp, seed + r, nullptr, &stats);
            bool ok = nlohmann::json::accept(reply);
            total++;
            valid += ok;
            prompt_tokens += stats.prompt_tokens;
            gen_tokens += stats.generated_tokens;
            prompt_ms += stats.prompt_ms;
            gen_ms += stats.generate_ms;
            std::printf("%-20s %s %4d+%-3d tok %5.0f+%-5.0f ms %2d rs  %s\n", name.c_str(), ok ? "ok " : "BAD",
                        stats.prompt_tokens, stats.generated_tokens, stats.prompt_ms, stats.generate_ms,
                        stats.grammar_resamples, reply.c_str());
            std::fflush(stdout);
            reply_file << reply << "\n";
        }
    }
    if (total > 0) {
        std::printf("\n%d/%d valid JSON | prompt %.0f tok/s (avg %d tok) | generation %.1f tok/s (avg %d tok) | "
                    "avg %.0f ms per decision\n",
                    valid, total, prompt_tokens / (prompt_ms / 1000.0), prompt_tokens / total,
                    gen_tokens / (gen_ms / 1000.0), gen_tokens / total, (prompt_ms + gen_ms) / total);
    }
    pokersi::free_engine(engine);
    llama_backend_free();
    return valid == total ? 0 : 1;
}
