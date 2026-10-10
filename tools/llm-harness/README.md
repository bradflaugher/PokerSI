# LLM harness

Runs PokerSI's real opponent prompts and GBNF grammars through llama.cpp on
a desktop, so prompt and grammar changes can be judged without a phone.

- `harness.cpp` links `app/src/main/cpp/pokersi-llm.h`, the same inference
  code the app's JNI bridge calls (chat template with thinking off, grammar
  sampling, the same sampler chain), and prints each reply with timings and
  whether it is valid JSON.
- The cases come from the app itself: `HarnessCasesTest.dumpCases` writes
  every fixture in `app/src/test/.../llm/Fixtures.kt` as
  `<name>.system.txt`, `<name>.user.txt` and `<name>.gbnf`, and
  `HarnessCasesTest.checkReplies` parses the replies with the app's
  `LlmReply`. Both tests are no-ops unless `POKERSI_HARNESS_CASES` is set.

## Run

Needs CMake, Ninja, a C++17 compiler, the llama.cpp submodule checked out
and some GGUF models (any from `ModelCatalog`).

```sh
tools/llm-harness/run.sh ~/models/LFM2.5-1.2B-Instruct-Q4_K_M.gguf ~/models/Qwen3-0.6B-Q4_K_M.gguf
```

Environment: `LLAMA_DIR` (llama.cpp tree, default the submodule),
`WORK_DIR` (build and output, default `/tmp/pokersi-harness`), `RUNS`
(samples per case, default 2), `THREADS` (default 4).

Or by hand, after building the `pokersi-harness` target:

```sh
pokersi-harness -m model.gguf -d cases/ [-o out/] [-t 4] [--temp 0.8] [--max 96] [--runs 3] [--no-grammar]
```

Nothing here is committed but sources: no binaries, no models.

## What we learned

Measured on a 4-core x86 cloud VM (shared, so timings are noisy), Q4_K_M:

| model | prompt tok/s | gen tok/s | per decision | play |
|---|---|---|---|---|
| Qwen 3 0.6B | ~460 | ~30 | ~2.0 s | follows the advice, chatty, often names its cards |
| LFM2.5 1.2B | ~270 | ~19 | ~2.8 s | sensible and short; a little passive |
| Llama 3.2 1B | ~290 | ~10-15 | ~3-4 s | good talk, stubborn caller |
| Gemma 3 1B | ~130 | ~9 | ~5-7 s | follows the advice, flowery; slow prompt (262k vocab) |

Every reply from every model was valid JSON and parsed to a legal decision.

- **Bounded repetition is slow.** `say ::= sayc{1,80}` made each token the
  grammar had to correct cost up to seconds (generation fell from ~30 to ~7
  tok/s). The grammar uses `sayc+` and `LlmReply` trims the say instead;
  small counts (discard lists) are spelled out as alternatives.
- **Decision first, talk last.** With `say` first, small models reason in
  the say ("With my pair of Aces I should...") and then contradict it, and a
  long say can eat the token budget before the action. `{"action", "amount",
  "say"}` keeps the decision intact even when the talk is cut off.
- **Spell out the strength and give advice.** 0.6B-1B models can't rate a
  hand; without help they raise or call everything. The prompt rates the hand
  ("Pair of Aces (very strong)") and ends with `Advice: "fold".` (bluffers
  get `A bluffer like you might "raise" instead.`). Advice phrased as an
  action word, placed last, is followed far more than prose tips placed
  mid-prompt.
- **Exact numbers in the grammar.** Fixed amounts for call/all-in and an
  integer range rule for raises mean the amount is always legal.
