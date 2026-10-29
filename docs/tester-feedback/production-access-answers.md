# Production access: submitted answers (October 29, 2026)

Answers submitted on Google Play Console's production-access questionnaire on
October 29, 2026 (Play Console enforces a 300-character limit on each free-text
field), alongside the fuller context notes from the closed test report
([`com.bradflaugher.pokersi_production.pdf`](com.bradflaugher.pokersi_production.pdf);
see the implementation table in [`README.md`](README.md)).

---

### 1. How did you recruit users for your closed test?

**Submitted (282 / 300 chars):**
> I used a paid testing provider so the app got a proper run across a range of real devices and Android versions, with a written report at the end. That matters more than usual for PokerSI, because its AI opponents run on the phone itself and how well they play depends on the device.

### 2. How easy was it to recruit testers for your app?

**Submitted:** `Easy`

### 3. Describe the engagement you received from testers during your closed test

**Submitted (284 / 300 chars):**
> Testers played all 6 poker variants, the roguelite Poker Tour, model downloads, and settings across real phones and tablets—just like real players. They hit 0 crashes or bugs, and gave detailed UX feedback on contrast, button spacing, exit confirmation, onboarding, and accessibility.

### 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

**Submitted (291 / 300 chars):**
> Collected via a written report from our testing provider after the 14-day test. It found 0 crashes or bugs and suggested: fuller store keywords/ASO; higher WCAG AA contrast and wider button spacing; an exit confirmation toggle; a guided tour, table hints, and FAQ; and accessibility options.

### 5. Who is the intended audience for your game?

**Submitted (282 / 300 chars):**
> Adults who like poker, retro arcade games or roguelites, and people curious about running AI on their own phone, from beginners learning the hands to regulars who want a quick game offline. It's a casual game: no real money is involved, chips are pretend, and nothing can be bought.

### 6. Describe what makes your game stand out, and how it provides value to users.

**Submitted (288 / 300 chars):**
> PokerSI has 6 poker variants where every opponent is a character played by a small language model running 100% offline on the phone—plus a roguelite Poker Tour, seeded/daily runs, and a built-in House Bot with no download required. It's free with no ads, accounts, purchases, or tracking.

### 7. How many installs do you expect your game to have in your first year?

**Submitted:** `0 - 10K`

### 8. What changes did you make to your game based on what you learned during your closed test?

**Submitted (286 / 300 chars):**
> Implemented every suggestion: WCAG AA contrast fixes, wider button spacing (20dp FOLD guard), exit/reset confirmation dialogs, a 5-page guided tour with dealer Dee, one-time table hints, an FAQ, Larger Text/High Contrast/Volume settings, default Table Talk with Hand Blur, and ASO copy.

**Full breakdown of changes shipped before applying:**

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
- Added Larger text, High contrast, Volume, and Hand Blur (with Table Talk
  enabled by default).

### 9. How did you decide that your game is ready for production?

**Submitted (291 / 300 chars):**
> The 14-day closed test had 0 crashes or bugs across all devices, and all UX/accessibility improvements shipped. Every PR passes a full JVM test suite (hand evaluator, all 6 variants, side pots, AI GBNF parser, WCAG AA contrast, dialogs, tour/hints) and Android lint, with House Bot fallback.

