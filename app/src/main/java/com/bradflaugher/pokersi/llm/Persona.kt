package com.bradflaugher.pokersi.llm

import kotlin.math.roundToInt

/**
 * A character the model plays at the table. The style numbers (0..1) steer
 * both the prompt and the sampling temperature; [seed] gives the UI a stable
 * value to draw the pixel face from.
 */
data class Persona(
    val name: String,
    /** One or two sentences of personality, written to the model ("You are..."-free). */
    val bio: String,
    /** How they talk, e.g. "a pirate: says arr, matey and doubloons". */
    val voice: String,
    val catchphrase: String,
    /** 0 = plays any two cards, 1 = only premium hands. */
    val tightness: Float,
    /** 0 = calls and checks, 1 = bets and raises. */
    val aggression: Float,
    /** 0 = never bluffs, 1 = bluffs constantly. */
    val bluffiness: Float,
    val seed: Long = name.hashCode().toLong(),
) {
    /** Sampling temperature: wilder characters sample hotter. */
    val temperature: Float get() = (0.6f + 0.3f * (bluffiness + aggression) / 2f).coerceIn(0.6f, 0.9f)

    /** The style in plain words for the prompt, e.g. "tight, aggressive, rarely bluffs". */
    val styleWords: String
        get() = listOf(
            scale(tightness, "very loose (plays almost any hand)", "loose", "selective", "tight", "very tight (folds weak hands)"),
            scale(aggression, "passive (prefers to check and call)", "fairly passive", "balanced", "aggressive", "very aggressive (loves to bet and raise)"),
            scale(bluffiness, "never bluffs", "rarely bluffs", "bluffs sometimes", "bluffs often", "bluffs all the time"),
        ).joinToString(", ")

    private fun scale(v: Float, vararg words: String): String =
        words[(v.coerceIn(0f, 1f) * (words.size - 1)).roundToInt()]
}
