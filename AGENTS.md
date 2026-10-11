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
  - `Links.kt` — Share app, Rate on Google Play, Send feedback (a new
    GitHub issue), the privacy policy and the source code: intents to the
    share sheet, Play Store and browser, never a network call. A missing
    handler returns false instead of crashing.
  - `game/` — `GameController` (runs a table for the UI, and owns each
    table's `TableTalk`), settings, the saved run, the daily record and
    `Stats` (lifetime records for the STATS screen, fed by `GameController`
    at each hand's end, including the Hold'em starting-hand chart counted
    by `StartingHands`), all in SharedPreferences; `AttractLines` (the title
    screen's cycling credits/tour/daily/records line).
  - `debug/` — `AiLog`, the debug console's bounded (400 entries),
    thread-safe ring buffer: prompts, grammars, streaming replies, timings,
    parsed and coerced decisions, fallbacks, engine and table events.
  - `engine/` — the poker. **Pure Kotlin, no `android.*` imports**, fully
    unit-tested. Cards and seeded decks, the hand evaluator (high hands,
    best 5 of N, Omaha's exactly-two rule, short-deck ranking), every
    `GameVariant`, the hand state machine with side pots, the agent contract
    (`Agent.kt`), `HouseBot` (a heuristic opponent, used with no model
    loaded and whenever the LLM fails), Monte Carlo equity, and `TableTalk`
    (what has been said at a table, for keeping lines fresh).
  - `run/` — the roguelite "Run" mode: tables (blinds) of rising stakes,
    chips that carry over, choosing your next table (low or high stakes),
    seeded runs and the daily run. Plain poker, no rule twists.
    Pure Kotlin too.
  - `llm/` — `LlamaNative` (JNI surface), `LlamaEngine` (loads one model,
    serializes every call on one thread), the model catalog, downloads
    (WorkManager), model storage (app storage + user-picked SAF folders),
    the prompt builder, GBNF grammar and reply parser (`LlmAgent`, which
    talks to a model through `LlmBackend`), and `TalkPlan` (what a character
    talks about this turn).
  - `ui/` — Compose. The pixel design system (`ui/pixel/`: fonts, palette,
    `PixelCard`, buttons, LED digits, green felt, CRT overlay, procedural
    pixel avatars, chiptune sound), and the screens: title, game picker,
    table, run tour, models, how-to-play, settings, stats, the first-run
    tips overlay (`Tips.kt`) and the drop-down `DebugConsole`.
- `app/src/test/` — JVM unit tests (engine, run, prompt/grammar/parser,
  table talk, the console log, stats, links). `InvariantsTest` checks the
  rules below that a compiler can't.
- `tools/` — `icon/gen_icon.py` (launcher icon), `screenshots/`
  (Play/README screenshots and captions), `llm-harness/` (the real prompts
  and grammars through llama.cpp on a desktop, single cases and whole
  sessions of table talk).

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
- **Fast enough to play.** A character only talks on some turns
  (`TalkPlan.speak`: more often when spoken to or facing a bet); on a quiet
  turn the prompt leaves the talk out and the grammar has no `say`, so the
  model reads and writes less. The system prompt is the same either way.
  SETTINGS > FAST FOLD (on by default): once the player has folded, the
  rest of the hand plays out at once, each opponent's `HouseBot` deciding.
- **Table talk stays fresh.** Small models parrot whatever the prompt
  repeats, so the catchphrase is not in the prompt (it is said when a
  character sits down, and offered only now and then by `TalkPlan`). Each
  prompt carries a short, varied hint (react to the last action, answer
  whoever spoke, tease someone, the pot, the board, something in
  character), the latest lines by others and the character's own last
  lines. One `TableTalk` per table, shared by every seat (LLM and House
  Bot) and new for each table, remembers what was said; a line that
  repeats it (same words, mostly the same words, or the catchphrase
  uninvited) is held back while it streams and swapped for a House Bot
  line nobody said lately, or silence.
- **Bubbles come and go.** At most the two latest speakers' lines show,
  laid out together so they never overlap (`stackBubbles`), and a finished
  line goes away by itself after a short hold (`Pacing.chatterHoldAt`: a
  base plus a little per character, capped; scaled by game speed, frozen
  while paused).
- **No model? Still playable.** With no model loaded, opponents are played
  by `HouseBot` and the table says so. The game never blocks on a download.
- **Infinitely replayable.** Random personas, six variants, the Run mode's
  escalating tables, seeded runs and a daily seed.
- **Fair dealing.** Decks are shuffled with a seeded `kotlin.random.Random`;
  agents only ever see a `DecisionContext` of what their seat may know.
- Network is used only to download models the user chooses, from Hugging
  Face. No analytics, no ads, no accounts.
- Rating is a passive item (SETTINGS > ABOUT > RATE opens the Play
  listing), never a prompt, reminder or the in-app review API. No "please
  share" nags, and no email addresses in the app: feedback goes to GitHub
  issues. `InvariantsTest` checks this, the permissions and that only the
  launcher activity is exported.
- The first-run tips must never block: one tap anywhere or Back dismisses
  them, and SETTINGS > SHOW TIPS brings them back. Launching with the
  boolean extra `com.bradflaugher.pokersi.SKIP_TIPS` skips them for that
  launch (screenshots and tests use it).
- Accessible to TalkBack: cards, avatars, LED counters and opponent seats
  carry content descriptions, the table announces its news in a live
  region, and every action button is at least 48dp tall.
- Poker on a green table, not a deck-builder: no Jokers, no shop.
- Keep the engine pure: all Android code lives outside `engine/` and `run/`.
- The debug console (SETTINGS > DEBUG CONSOLE, then the "~" toggle pinned
  top right at the table and in the model bay, which stays put over the open
  console to close it, or a keyboard's backtick) is off by default, and `AiLog`
  records nothing unless it is on; switching it off clears it. It never
  leaves the phone except by the player's own COPY.

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

Every image in `fastlane/metadata/android/en-US/images/` and
`docs/screenshots/` is generated; regenerate them all with one command
after any visible UI change, then look at every PNG it writes:

```sh
tools/screenshots/shoot.py               # render + caption (needs ImageMagick)
tools/screenshots/shoot.py --no-render   # captions only, from build/screenshots/
```

- `shoot.py` runs `./gradlew -PskipNative -Pstore :app:testDebugUnitTest
  --tests '*StoreScreenshotTest*' --rerun`. `StoreScreenshotTest` renders
  every `ScreenshotScenes.all` scene at phone 1080x1920 (420dpi), 7-inch
  1224x2176 and 10-inch 1620x2880 (320dpi), all exactly 9:16, plus the
  whole feature graphic (`FeatureGraphic.kt`, drawn with the pixel design
  system at 320dpi so every piece of art sits on one 3px grid) and the
  launcher icon, into `build/screenshots/<class>/<scene>.png`. Without `-Pstore` (or
  `POKERSI_STORE_SHOTS`) it is skipped, so plain `test` stays fast.
- `caption.py` (ImageMagick 7 `magick` or 6 `convert`; pngquant and
  optipng when installed) picks scenes by name. `CAPTIONS` sets the store
  order, headlines (Press Start 2P, at most 13 characters a line, two
  lines) and sublines (VT323); `README_SHOTS` maps the uncaptioned README
  files. Each caption shows a different feature; keep them true to the app.
  Renaming or removing a scene means updating those two tables.
- Outputs: `phoneScreenshots/` (8), `sevenInchScreenshots/` and
  `tenInchScreenshots/` (6 each), `featureGraphic.png` (1024x500),
  `icon.png` (512x512, from the adaptive icon layers), and in
  `docs/screenshots/` the README shots plus `icon.png` (192px, rounded),
  which the README title needs.
- The quick look at UI work is still `./gradlew -PskipNative
  :app:testDebugUnitTest --tests '*ScreenshotTest*'`, which writes
  `app/build/outputs/roborazzi/`.
