# Agent and contributor instructions

PokerSI is an Android poker game where every opponent is played by a large
language model running **on the phone** (llama.cpp, GGUF models). It looks
like a 90s video poker cabinet on a green card table: chunky 8-bit
cards, lamp-lit pixel felt, CRT scanlines, marquee lights, LED
pot counters, HOLD buttons, and silly opponents who trash-talk. Read
`README.md` for the feature overview and keep it in sync with any behavior
you change.

## Latest-only platform policy

Same as the author's other Android apps (bf-12c, CozyGrams):

- `targetSdk` and `compileSdk` are the latest public stable API level
  (`app/build.gradle.kts`). `minSdk` is the newest API the code uses (33, for
  the `POST_NOTIFICATIONS` permission). No `Build.VERSION.SDK_INT` checks, no compat shims.
- AGP, Kotlin, Compose BOM, libraries (`gradle/libs.versions.toml`) and the
  Gradle wrapper (checksum-pinned) track the latest stable releases.
  Dependabot keeps them current. Do not copy version numbers into docs.

## Layout

- `app/src/main/cpp/` — `pokersi-jni.cpp`, the JNI bridge to llama.cpp, and
  `llama.cpp/`, a git submodule of the
  [andriydruk/llama.cpp-android](https://github.com/andriydruk/llama.cpp-android)
  fork that LM Playground uses (upstream plus `fd:N` file-descriptor paths,
  so models picked through the Storage Access Framework load without a copy).
- `app/src/main/java/com/bradflaugher/pokersi/`
  - `engine/` — the poker. **Pure Kotlin, no `android.*` imports**, fully
    unit-tested. Cards and seeded decks, the hand evaluator (high hands,
    best 5 of N, Omaha's exactly-two rule, short-deck ranking), every
    `GameVariant`, the hand state machine with side pots, the agent contract
    (`Agent.kt`), `HouseBot` (a heuristic opponent, used with no model
    loaded and whenever the LLM fails), Monte Carlo equity.
  - `run/` — the roguelite "Run" mode: tables (blinds) of rising stakes,
    boss opponents with rule twists, choosing your next table, seeded runs
    and the daily run. Pure Kotlin too.
  - `llm/` — `LlamaNative` (JNI surface), `LlamaEngine` (loads one model,
    serializes every call on one thread), the model catalog, downloads
    (WorkManager), model storage (app storage + user-picked SAF folders),
    the prompt builder, GBNF grammar and reply parser (`LlmAgent`).
  - `ui/` — Compose. The pixel design system (`ui/pixel/`: fonts, palette,
    `PixelCard`, buttons, LED digits, green felt, CRT overlay, procedural
    pixel avatars, chiptune sound), and the screens: title, game picker,
    table, run tour, models, how-to-play, settings.
- `app/src/test/` — JVM unit tests (engine, run, prompt/grammar/parser).
- `tools/` — `icon/gen_icon.py` (launcher icon), `screenshots/`
  (Play/README screenshots and captions).

## Design rules

- **The LLM does the playing.** Each opponent is a persona (name, pixel
  face, personality) played by the loaded model. Each decision is a fresh,
  self-contained prompt: persona + rules + what that seat can see + legal
  actions + the seat's current made hand in words (tiny models can't read
  poker hands reliably), its strength, and a one-word piece of advice the
  persona may ignore. The reply is constrained by a GBNF grammar to
  `{"action": "...", "amount": N, "say": "..."}` (or a discard list),
  decision first so a small model can't talk itself out of it, then
  clamped to a legal action by `LegalActions.coerce`. If generation fails
  or times out, `HouseBot` decides instead and the hand carries on.
- **No model? Still playable.** With no model loaded, opponents are played
  by `HouseBot` and the table says so. The game never blocks on a download.
- **Infinitely replayable.** Random personas, six variants, the Run mode's
  escalating tables, bosses, seeded runs and a daily seed.
- **Fair dealing.** Decks are shuffled with a seeded `kotlin.random.Random`;
  agents only ever see a `DecisionContext` of what their seat may know.
- Network is used only to download models the user chooses, from Hugging
  Face. No analytics, no ads, no accounts. Rating is a passive menu item.
- Keep the engine pure: all Android code lives outside `engine/` and `run/`.

## Build, test, release

```sh
./gradlew lint test assembleDebug          # full build, including llama.cpp
./gradlew -PskipNative test lintDebug       # fast: no native code
```

`-PskipNative` builds without llama.cpp; the app then only offers House Bot.
Use it for unit tests, lint and screenshot work.

`.github/workflows/ci.yml` runs the unit tests and lint on every pull
request and push to `main`. Every push to `main` builds a signed APK and
Play bundle and publishes them as the single date-labeled GitHub release
`vYYYY.MM.DD.<run>`, deleting all older releases. Pull requests build
unsigned and publish nothing. Keep `main` green.

## Screenshots

`tools/screenshots/` renders the store and README screenshots on the JVM
(no emulator) from the real composables with scripted game states, then
`caption.py` adds the headline captions in the app's own pixel style and
builds the feature graphic. Rerun after a visible UI change and look at every
PNG it writes.
