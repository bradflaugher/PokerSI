# Closed-test feedback

Before production access, PokerSI went through a closed test on Google Play
(**Closed testing - Alpha**) with a paid testing provider, Testers
Community, the same way bf-12c and CozyGrams did. Their two reports are here
as they arrived:

- [`com.bradflaugher.pokersi_feedback.pdf`](com.bradflaugher.pokersi_feedback.pdf):
  the test report. No crashes, bugs or broken features on any device or SDK
  they tried, plus a list of suggested improvements.
- [`com.bradflaugher.pokersi_production.pdf`](com.bradflaugher.pokersi_production.pdf):
  template answers for Google Play's production-access questionnaire. Our own
  answers, accurate to what we actually changed, are in
  [`production-access-answers.md`](production-access-answers.md).

## What we did with each suggestion

| Suggestion | What we did | Where |
|---|---|---|
| **App Store Optimization**: more text and keywords, highlight the AI opponents, retro graphics and ease of use | Rewrote the listing around what people search for: the title is now **PokerSI: Offline Poker vs AI**; the first two sentences name offline poker, AI opponents, all six games (Texas Hold'em, Pot-Limit Omaha, Five-Card Draw, Seven-Card Stud, Short Deck, Crazy Pineapple), the retro pixel-art video poker cabinet look and single-player play with no internet, ads or account. The rest is short bullet sections (AI opponents, six games, Poker Tour, practice and stats, models, retro and easy to play, no ads or real money), about 3,400 of the 4,000 characters, every claim true to the app and no keyword lists. The site's title and meta description match. | `fastlane/metadata/android/en-US/` (`title.txt`, `short_description.txt`, `full_description.txt`), `site/index.html` |
| **UI/UX: layout** for consistency and intuitive navigation; game modes and settings easy to reach | The title screen groups the ways to play (CONTINUE / QUICK GAME / NEW TOUR / DAILY) apart from MODELS, STATS, HOW TO and SETUP, all on one phone screen without scrolling. SETTINGS is grouped as GAME, SOUND & FEEL, ACCESSIBILITY, POKER TOUR, HELP, WORKSHOP and ABOUT. Every screen now has the same "<" back button (the model bay had a text BACK). Fixed three things that clipped on a small 360x640dp phone (a game tile's tagline, a name breaking mid-word, the made hand at showdown); a small-phone render now runs with the screenshot tests. | `ui/screens/TitleScreen.kt`, `InfoScreens.kt`, `PickerScreen.kt`, `RunScreens.kt`, `ui/models/ModelsScreen.kt`, `ScreensScreenshotTest` |
| **UI/UX: spacing** to prevent accidental taps | A spacing scale with an 8dp minimum gap between tap targets (they were 5 to 7dp in places). At the table, FOLD now sits 20dp away from CALL and RAISE behind a dashed felt rail, so the hand you meant to play is never folded by a slip. Every button stays at least 48dp tall. | `ui/pixel/PixelSpacing.kt`, `ui/screens/TableScreen.kt`, `Arcade.kt` |
| **UI/UX: color contrast** | Measured every text/background pair the palette uses against WCAG AA and fixed the failures: gray secondary text on the felt went from as low as 1.6:1 to 4.8 to 6.1:1; white on the green CALL button (2.0:1) is now dark text at 9.3:1; the red and pink buttons and panel headers got deeper faces so their captions pass 4.5:1; disabled buttons went from 2.7 to 4.3:1. Card suits keep their bright red. A unit test now locks in every pair so it can't regress. | `ui/pixel/PixelTheme.kt`, `Contrast.kt`, `PixelButton.kt`, `PixelPanel.kt`, `ContrastTest` |
| **In-app exit confirmation**, with a settings toggle | Back on the title screen asks **LEAVE THE CABINET?** with STAY (the big, default button) and EXIT. Back or a tap outside means stay. SETTINGS > CONFIRM EXIT (on by default) turns it off. We also looked for every other place one stray tap loses something and put the same dialog there: quitting a quick game (its chips are lost; a tour is saved, so it doesn't ask), NEW TOUR over a saved tour (which used to overwrite it silently), ABANDON TOUR and RESET STATS. Nothing that loses nothing asks. | `ui/screens/ConfirmDialog.kt`, `ui/PokerSiApp.kt`, `game/Settings.kt` (`confirmExit`), `ConfirmDialogTest` |
| **Dynamic walkthrough: interactive tutorial** on first launch | The one-page tips became a five-page **guided tour** hosted by Dee, a pixel dealer whose speech bubble types out: meet the table (the AI is on your phone, offline), your cards (a hand deals in and the made hand reads out), your move (real FOLD / CHECK / CALL / RAISE buttons to tap, and HOLD for draw games), pick a game (Quick Game or the ten-table Poker Tour), and brains (MODELS, House Bot, pause, help). NEXT / BACK / dots / swipe; SKIP, Back or a tap outside closes it at any step, so it never blocks. TalkBack reads each page; with animations off it draws still. Players who saw the old tips aren't toured again. | `ui/screens/Tips.kt` (`TourOverlay`), `game/Guide.kt`, `GuideTest`, `AccessibilityTest` |
| **Dynamic walkthrough: progressive disclosure** | One-time **table hints**: the first time it's your turn, you open the raise panel, a draw or a Crazy Pineapple toss comes up, a hand ends, you can pause, or you choose your next tour table, a small TIP bubble points at the right control. One at a time, once ever, gone at the next touch or after about 6 seconds, never taking a tap and never covering the action buttons or your cards. Which hint shows when is plain Kotlin with unit tests. | `game/CoachMarks.kt`, `ui/screens/Coach.kt`, `TableScreen.kt`, `CoachMarksTest` |
| **Dynamic walkthrough: help section** with FAQs | HOW TO PLAY now opens with jump buttons (HANDS / GAMES / TOUR / AI / FAQ) and ends with **QUESTIONS**: ten collapsible answers (internet, slow opponents, which model, old phones, quiet opponents, the Poker Tour and seeds, real money, privacy, reporting a bug, seeing the tour again) and a TAKE THE TOUR button. SETTINGS > REPLAY TOUR brings back the tour and the table hints. | `ui/screens/InfoScreens.kt` (`HowToPlayScreen`) |
| **Accessibility**: options for text size, colors and sound | New SETTINGS > ACCESSIBILITY: **LARGER TEXT** (1.2x, on top of the phone's own font size, applied once in the theme so every screen gets it; one-line text shrinks to fit rather than clip) and **HIGH CONTRAST** (a second palette: brighter secondary text, black outlines, darker flatter felt, lighter scanlines). SOUND & FEEL gains **VOLUME** (LOW / MED / HIGH, previewed as you pick). TalkBack now reads toggles with their names ("Sound, On"). Renders of the table, draw table, title, settings and How to Play with larger text and high contrast run with the screenshot tests. | `ui/pixel/PixelTheme.kt`, `CrtOverlay.kt`, `FeltBackground.kt`, `Sound.kt`, `PixelType.kt`, `SettingsTest`, `PixelDesignTest` |
| **Play Store screenshots** (part of making the listing show the app) | Regenerated every store and README screenshot with the new look; the phone set swaps one Poker Tour shot for the guided tour (**NEW TO POKER?**). | `tools/screenshots/caption.py`, `fastlane/metadata/android/en-US/images/`, `docs/screenshots/` |
| **Performance monitoring** | Nothing to add: the report found it fast on every device. AI opponents already run on their own thread with a time limit, and House Bot takes any decision the model is slow on, so a hand never stalls; GAME SPEED and FAST FOLD are there for slower phones. The debug console shows timings and tokens per second for anyone curious. No analytics or monitoring SDK, which would break the no-network promise. | none |
| **Incorporate user feedback**, **community engagement** | SETTINGS > SEND FEEDBACK already opens a new GitHub issue with the app and device details filled in, and the source is public, so bugs, ideas and tips have a home where everyone can see them. No social SDKs or accounts. | `Links.kt` (existing) |
| **Regular updates** | Not code. Every push to `main` already builds a release and ships it to the Alpha track; this round is the first of those updates. | none |

## Promises a suggestion can't change

Whatever the testers suggest, these stay:

- **No network beyond model downloads.** PokerSI only connects to Hugging
  Face, and only to download a model the player picked. No analytics, crash
  SDK, ads or accounts.
- **No rating prompts or share nags.** Rating is a menu item, there when
  wanted, never a pop-up.
- **No real money.** Chips are pretend; nothing can be bought or cashed out.
- **Playable with no download.** House Bot plays every seat until a model is
  loaded.
