# Production access: draft answers

Draft answers for Google Play's production-access questionnaire, written
before the closed test from what PokerSI actually does. Brad: check every
**[BRAD: ...]** spot before pasting, and rewrite questions 3, 4 and 8 from
the testers' real report once it is in this folder.

---

### 1. How did you recruit users for your closed test?

**[BRAD: describe what you actually did. If it's a paid testing provider
again, as with bf-12c and CozyGrams:]** I used a paid testing provider so
the app got a proper run across a range of real devices and Android
versions, with a written report at the end. That matters more than usual
for PokerSI, because its AI opponents run on the phone itself and how well
they play depends on the device.

### 2. How easy was it to recruit testers for your app?

**[BRAD: pick one: Very easy / Easy / Neither easy nor difficult / Difficult / Very difficult.]**

### 3. Describe the engagement you received from testers during your closed test

**[BRAD: replace with what the report says. A shape to start from:]**
Testers played every variant (Texas Hold'em, Pot-Limit Omaha, Five-Card
Draw, Seven-Card Stud, Short Deck and Crazy Pineapple), against House Bot
with no model loaded and against the on-device AI after downloading a
model, and tried Run mode through several tables, a boss and the choice of
next table. They also loaded their own GGUF model files from a folder.

### 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

**[BRAD: summarize the written report from the testing provider: what they
found (crashes, bugs, slow devices) and what they suggested.]**

### 5. Who is the intended audience for your app?

Adults who like poker, retro arcade games or roguelites, and people curious
about running AI on their own phone. It's a casual game: no real money is
involved, chips are pretend, and nothing can be bought.

### 6. Describe how your app provides value to the users.

PokerSI is a poker game with six variants where every opponent is a
character with its own personality, played by a small language model that
runs entirely on the phone. They bet, bluff and talk trash, and nothing
about the game leaves the device. A roguelite Run mode (tables of rising
stakes, boss opponents with rule twists, a choice of next table, seeded and daily
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

**[BRAD: list the real changes, as in bf-12c's answers. Keep it to what
actually shipped. If a suggestion was declined on purpose (for example a
rating prompt, or analytics), say so and why: PokerSI's rating button is a
menu item only, and it sends nothing anywhere except model downloads.]**

### 9. How did you decide that your app is ready for production?

**[BRAD: add the closed test's result.]** Beyond the closed test, the
poker engine has a large automated test suite: the hand evaluator against
every hand ranking (including Omaha's exactly-two rule and Short Deck's
order), each variant's betting, side pots and showdowns, Run mode's
tables, bosses and table choices, and the parser that turns the AI's reply into a
legal action. Every change has to pass it and Android lint before it can
be merged. If the AI ever fails to answer in time, House Bot takes the
seat for that decision, so a slow phone or a bad model can't stall a hand.

### 10. What did you do differently this time?

**[BRAD: if this is the first production-access application for PokerSI,
say so in a sentence. Otherwise describe what changed since the last one.]**
