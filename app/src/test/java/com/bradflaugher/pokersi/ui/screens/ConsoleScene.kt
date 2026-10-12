package com.bradflaugher.pokersi.ui.screens

import com.bradflaugher.pokersi.debug.AiLogEntry
import com.bradflaugher.pokersi.debug.AiLogKind

/** Plausible debug console content for the screenshot: the flop of [ScreenshotScenes.holdemMidHand]. */
object ConsoleScene {
    private const val T0 = 1_760_000_000_000L + 21 * 3_600_000L + 14 * 60_000L

    private val dotPrompt = """
        [system]
        You are Lady Dot, a character playing poker at a friendly game night. Counts cards. Mostly up to seven.
        You talk like a very proper lady who sips tea.
        Your playing style: tight, balanced, rarely bluffs.
        "say" is one short, funny sentence in your voice, under 12 words, different every time.

        [user]
        Hand #12, Flop. Big blind 20.
        Your cards: Qc Jd
        Board: Ks 7h 2h
        Your hand: High card King (weak)
        Pot: 270. You have 1130 chips.
        Nobody has bet: you can check for free.
        Table talk:
        - Byte-Sized Bob: "Nope."
        - Captain Chip: "Let's see a card."
        You already said "Trust the process.". Don't repeat yourself.
        For "say": Tease Captain Chip about their 1570 chips.
        Your hand is weak. Advice: "check".
        Your move, Lady Dot?
    """.trimIndent()

    private const val GRAMMAR = """root ::= "{\"action\": " action ", \"say\": \"" say "\"}"
action ::= "\"check\", \"amount\": 0" | "\"raise\", \"amount\": " ( [2-9] [0-9] | [1-9] [0-9] [0-9] | "1" [0-1] [0-9] [0-9] )
say ::= sayc+
sayc ::= [^"\\\x00-\x1F]"""

    val entries: List<AiLogEntry> = listOf(
        AiLogEntry(1, T0, AiLogKind.ENGINE, "ENGINE", "loading Qwen 3.5 2B"),
        AiLogEntry(2, T0 + 2_310, AiLogKind.ENGINE, "ENGINE", "ready: Qwen 3.5 2B", "qwen35 2B Q4_K_M · 4 threads · ctx 4096"),
        AiLogEntry(3, T0 + 9_004, AiLogKind.TABLE, "TABLE", "hand 12: Texas Hold'em, blinds 10/20"),
        AiLogEntry(4, T0 + 10_120, AiLogKind.TABLE, "TABLE", "Captain Chip calls 20"),
        AiLogEntry(5, T0 + 14_870, AiLogKind.TABLE, "TABLE", "You raises to 80"),
        AiLogEntry(6, T0 + 14_900, AiLogKind.PROMPT, "Byte-Sized Bob", "hand 12, Pre-flop: React to this: You raises to 80.", "[system]\nYou are Byte-Sized Bob...\n[user]\n..."),
        AiLogEntry(7, T0 + 16_740, AiLogKind.DECISION, "Byte-Sized Bob", "FOLD · 1840 ms (prompt 1210 ms) · 18 tok · 28.6 tok/s", "say: \"Nope.\""),
        AiLogEntry(8, T0 + 17_300, AiLogKind.ERROR, "Captain Chip", "dropped a repeat: \"All in on vibes.\"", "said instead: \"Let's see a card.\""),
        AiLogEntry(9, T0 + 17_310, AiLogKind.DECISION, "Captain Chip", "CALL (model said RAISE 9000) · 2210 ms · 21 tok · 24.0 tok/s", "say: \"Let's see a card.\""),
        AiLogEntry(10, T0 + 18_200, AiLogKind.TABLE, "TABLE", "Flop: Ks 7h 2h"),
        AiLogEntry(11, T0 + 18_260, AiLogKind.PROMPT, "Lady Dot", "hand 12, Flop: Tease Captain Chip about their 1570 chips.", dotPrompt),
        AiLogEntry(12, T0 + 18_270, AiLogKind.GRAMMAR, "Lady Dot", "grammar (4 rules)", GRAMMAR),
        AiLogEntry(13, T0 + 20_050, AiLogKind.REPLY, "Lady Dot", "71 chars", """{"action": "check", "amount": 0, "say": "Captain, your chips need a chaperone."}"""),
        AiLogEntry(14, T0 + 20_060, AiLogKind.DECISION, "Lady Dot", "CHECK · 1800 ms (prompt 1020 ms) · 22 tok · 28.2 tok/s", "say: \"Captain, your chips need a chaperone.\""),
        AiLogEntry(15, T0 + 21_400, AiLogKind.ERROR, "Captain Chip", "House Bot decides (timed out after 25 s): RAISE 160", "after 25004 ms"),
        AiLogEntry(16, T0 + 21_450, AiLogKind.PROMPT, "Byte-Sized Bob", "hand 13, Pre-flop: Answer Captain Chip, who said \"Big bet. Huge.\"", "[system]\n..."),
        AiLogEntry(17, T0 + 22_010, AiLogKind.REPLY, "Byte-Sized Bob", "streaming…", """{"action": "call", "amount": 20, "say": "Huge? My toaster bets"""),
    )

    /** The Lady Dot prompt is shown expanded. */
    val expanded: Set<Long> = setOf(11L)
}
