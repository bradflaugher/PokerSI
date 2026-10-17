# Production access: draft answers

Draft answers for Google Play's production-access questionnaire, based on
the testers' template ([`com.bradflaugher.pokersi_production.pdf`](com.bradflaugher.pokersi_production.pdf))
but rewritten to match what PokerSI does and what actually changed (see the
table in [`README.md`](README.md)). Brad: check every **[BRAD: ...]** spot
before pasting. The template mentions "surveys, direct communication, and
usability testing sessions"; what we actually got was the provider's
written report, so the answers say that.

---

### 1. How did you recruit users for your closed test?

I used a paid testing provider so the app got a proper run across a range
of real devices and Android versions, with a written report at the end.
That matters more than usual for PokerSI, because its AI opponents run on
the phone itself and how well they play depends on the device.
**[BRAD: if you also had poker-playing friends try it, add one sentence;
otherwise delete this note.]**

### 2. How easy was it to recruit testers for your app?

**[BRAD: pick one: Very easy / Easy / Neither easy nor difficult / Difficult / Very difficult.]**
(The template suggests "Easy". With a paid provider that's honest.)

### 3. Describe the engagement you received from testers during your closed test

Testers ran the app across phones and tablets on several Android versions
and went through all of it: the six poker variants, the Poker Tour, the
model downloads and the settings. They reported no crashes, bugs or broken
features on any device. Their feedback was about the experience around the
game: how easy the store listing is to find, layout, spacing and contrast,
protecting players from leaving by accident, and how a new player learns
the game. That was the useful kind of feedback for a game that already
worked, and every point led to a change (question 8).

### 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

The feedback came as a written report from the testing provider at the end
of the test. It found no crashes or bugs on any device or Android version.
Its suggestions were: a fuller store description with the keywords poker
players search for; better layout, more spacing between buttons and
stronger color contrast; a confirmation before leaving the app, with a
setting to turn it off; a walkthrough for new players, with step-by-step
hints and a help/FAQ section; and accessibility options for text size,
colors and sound.

### 5. Who is the intended audience for your app?

Adults who like poker, retro arcade games or roguelites, and people curious
about running AI on their own phone, from beginners learning the hands to
regulars who want a quick game offline. It's a casual game: no real money
is involved, chips are pretend, and nothing can be bought.

### 6. Describe how your app provides value to the users.

PokerSI is a poker game with six variants where every opponent is a
character with its own personality, played by a small language model that
runs entirely on the phone. They bet, bluff and talk trash, and nothing
about the game leaves the device. A roguelite Run mode (tables of rising
stakes, chips that carry over, a choice of next table, seeded and daily
runs) makes it endlessly replayable. It works with no download at all
against the built-in House Bot, and players who want the AI download a
small model in the app or load their own GGUF files. It's free, with no
ads, no accounts, no in-app purchases and no tracking; the only network use
is downloading a model the player chose, from Hugging Face.

### 7. How many installs do you expect your app to have in your first year?

**[BRAD: pick a range. For a niche game with no marketing, 1K - 10K is a
realistic start; on-device AI is a draw, so 10K - 100K isn't unreasonable.
Your call.]**

### 8. What changes did you make to your app based on what you learned during your closed test?

Every suggestion from the report went in:

- Rewrote the store listing around what people search for (offline poker,
  Texas Hold'em, Omaha, video poker, pixel/retro, poker vs AI), in short
  sections, every claim true to the app, and regenerated the screenshots.
- Measured every text color against its background and fixed the ones
  below the WCAG AA contrast standard (some secondary text was as low as
  1.6:1; all now pass 4.5:1), with an automated test so it stays that way.
- More space between buttons everywhere (at least 8dp), and the FOLD
  button now sits well apart from CALL and RAISE so a slip can't fold a
  hand. Regrouped the title screen and settings, made back buttons
  consistent, and fixed text that clipped on small phones.
- Added an exit confirmation ("Leave the cabinet?" with Stay and Exit),
  which can be switched off in Settings. The same confirmation now guards
  every other place one tap could lose progress: quitting a game, starting
  a new tour over a saved one, and the reset buttons.
- Replaced the one-screen tips with a five-page guided tour hosted by a
  pixel dealer, with small live pictures and buttons to try, which can be
  skipped at any point and replayed from Settings or How to Play.
- Added one-time hints at the table that point at the right control the
  first time something comes up (your first turn, raising, holding cards,
  pausing, choosing a tour table).
- Added a Questions section and jump buttons to How to Play.
- Added Larger text, High contrast and a Volume setting.

### 9. How did you decide that your app is ready for production?

The closed test found no crashes or bugs on any device it ran on, and the
improvements it suggested are now in. Beyond the closed test, the poker
engine has a large automated test suite: the hand evaluator against every
hand ranking (including Omaha's exactly-two rule and Short Deck's order),
each variant's betting, side pots and showdowns, Poker Tour's tables and
table choices, the parser that turns the AI's reply into a legal action,
and now the color contrast, the confirmation dialogs, the tour and the
table hints. Every change has to pass it and Android lint before it can be
merged. If the AI ever fails to answer in time, House Bot takes the seat
for that decision, so a slow phone or a bad model can't stall a hand.

### 10. What did you do differently this time?

**[BRAD: this is the first production-access application for PokerSI, so
say so in a sentence, or describe your previous attempt if there was one.
For example:]** This is PokerSI's first application for production. As
with my other apps, I ran a full closed test with outside testers on a wide
range of devices, got a written report, and acted on all of it before
applying: a clearer store listing, better contrast and spacing, an exit
confirmation, a guided tour with table hints and an FAQ, and accessibility
settings.

