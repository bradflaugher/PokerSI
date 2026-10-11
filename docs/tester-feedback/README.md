# Closed-test feedback

Before production access, PokerSI goes through a closed test on Google Play
(**Closed testing - Alpha**), the same way bf-12c and CozyGrams did. When
the testers' reports arrive, they go in this folder as they arrived, next
to a table of what we did with each suggestion.

- Draft answers for Google Play's production-access questionnaire, accurate
  to what PokerSI actually does, are in
  [`production-access-answers.md`](production-access-answers.md). Fill in
  the **[BRAD: ...]** spots once the test is over.

## What we did with each suggestion

| Suggestion | What we did | Where |
|---|---|---|
| _(none yet: the closed test hasn't run)_ | | |

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
