package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.BettingStructure
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.DiscardRule

/**
 * One betting choice offered to the model. [amount] is fixed for every
 * action but `raise`, whose total may be anything in [range].
 */
data class ActionOption(val word: String, val amount: Int, val range: IntRange? = null)

/**
 * GBNF grammars that pin the model's reply to exactly the JSON [LlmReply]
 * reads, with only the legal action words and amounts:
 *
 *     {"action": "raise", "amount": 120, "say": "Arr!"}
 *     {"discard": [2, 5], "say": "Hmm."}
 *
 * The decision comes first, so it is complete early (a rambling say only
 * gets cut off by the token limit) and isn't talked out of by the chatter.
 *
 * Discard positions are 1-based (as the prompt numbers the cards).
 */
object PokerGrammar {
    const val SAY_MAX = 80

    /**
     * The betting choices for [context]. Folding is left out when checking
     * is free, `allin` only appears when the biggest raise really is the
     * whole stack (not a pot or fixed-limit cap).
     */
    fun actionMenu(context: DecisionContext): List<ActionOption> {
        val legal = context.legal
        val me = context.seats.firstOrNull { it.seat == context.seat }
        val stackTotal = me?.let { it.chips + it.betThisRound }
        val max = legal.maxRaiseTo
        val allIn = max != null &&
            if (stackTotal != null) max >= stackTotal else context.variant.structure == BettingStructure.NO_LIMIT
        return buildList {
            if (legal.canFold && !legal.canCheck) add(ActionOption("fold", 0))
            if (legal.canCheck) add(ActionOption("check", 0))
            if (!legal.canCheck && legal.toCall > 0) add(ActionOption("call", legal.toCall))
            val min = legal.minRaiseTo
            if (min != null && max != null && !(allIn && min >= max)) {
                add(ActionOption("raise", min, min..maxOf(min, max)))
            }
            if (allIn) add(ActionOption("allin", max!!))
            if (isEmpty()) add(ActionOption("check", 0))
        }
    }

    /** Grammar for a betting decision; without [speak], the reply has no say. */
    fun betting(menu: List<ActionOption>, speak: Boolean = true): String {
        val actions = menu.joinToString(" | ") { option ->
            val head = "\"\\\"${option.word}\\\", \\\"amount\\\": "
            if (option.range != null) "$head\" ${intRange(option.range.first, option.range.last)}"
            else "$head${option.amount}\""
        }
        return rules(
            if (speak) """root ::= "{\"action\": " action ", \"say\": \"" say "\"}"""" else """root ::= "{\"action\": " action "}"""",
            "action ::= $actions",
        )
    }

    /** Grammar for a draw / discard decision over [handSize] cards; without [speak], the reply has no say. */
    fun discard(handSize: Int, rule: DiscardRule, speak: Boolean = true): String {
        val max = rule.max.coerceIn(0, handSize)
        val min = rule.min.coerceIn(0, max)
        val positions = (1..handSize).joinToString(" | ") { "\"$it\"" }
        // Spelled out rather than {m,n}: llama.cpp's bounded repetition is slow to sample.
        val list = (min..max).joinToString(" | ") { n ->
            if (n == 0) "\"\"" else List(n) { "pos" }.joinToString(" \", \" ")
        }
        return rules(
            if (speak) """root ::= "{\"discard\": [" list "], \"say\": \"" say "\"}"""" else """root ::= "{\"discard\": [" list "]}"""",
            "list ::= $list",
            "pos ::= $positions",
        )
    }

    /** The grammar for whatever [context] asks for, with or without the say ([speak]). */
    fun forContext(context: DecisionContext, speak: Boolean = true): String {
        val rule = context.legal.discard
        return if (rule != null) discard(context.holeCards.size, rule, speak) else betting(actionMenu(context), speak)
    }

    // No quotes, backslashes or control characters: the say text needs no escaping.
    // Its length is not bounded here: sayc{1,80} makes llama.cpp's grammar
    // sampler seconds slower per rejected token. LlmReply trims it instead.
    private fun rules(vararg lines: String): String =
        (lines.toList() + "say ::= sayc+" +"""sayc ::= [^"\\\x00-\x1F]""").joinToString("\n", postfix = "\n")

    /** A GBNF expression matching exactly the decimal integers in [lo]..[hi] (no leading zeros). */
    fun intRange(lo: Int, hi: Int): String {
        require(lo in 0..hi)
        val alternatives = rangePatterns(lo, hi).map { pattern ->
            pattern.joinToString(" ") { if (it.first == it.last) "\"${it.first}\"" else "[${it.first}-${it.last}]" }
        }
        return if (alternatives.size == 1) alternatives[0] else alternatives.joinToString(" | ", "( ", " )")
    }

    /** [lo]..[hi] as digit-by-digit patterns, each a list of allowed digit ranges. */
    internal fun rangePatterns(lo: Int, hi: Int): List<List<IntRange>> {
        val out = mutableListOf<List<IntRange>>()
        var start = lo
        while (start <= hi) {
            val digits = start.toString().length
            val end = minOf(hi, if (digits >= 10) Int.MAX_VALUE else pow10(digits) - 1)
            out += sameLength(start.toString(), end.toString())
            if (end == Int.MAX_VALUE) break
            start = end + 1
        }
        return out
    }

    private fun pow10(n: Int): Int = (1..n).fold(1) { acc, _ -> acc * 10 }

    /** Patterns for a..b where a and b have the same number of digits. */
    private fun sameLength(a: String, b: String): List<List<IntRange>> {
        if (a.isEmpty()) return listOf(emptyList())
        val a0 = a[0].digitToInt()
        val b0 = b[0].digitToInt()
        val aRest = a.drop(1)
        val bRest = b.drop(1)
        if (a0 == b0) return sameLength(aRest, bRest).map { listOf(a0..a0) + it }
        val any = List(aRest.length) { 0..9 }
        val out = mutableListOf<List<IntRange>>()
        val lowFull = aRest.all { it == '0' }
        val highFull = bRest.all { it == '9' }
        if (!lowFull) out += sameLength(aRest, "9".repeat(aRest.length)).map { listOf(a0..a0) + it }
        val midLo = if (lowFull) a0 else a0 + 1
        val midHi = if (highFull) b0 else b0 - 1
        if (midLo <= midHi) out += listOf(listOf(midLo..midHi) + any)
        if (!highFull) out += sameLength("0".repeat(bRest.length), bRest).map { listOf(b0..b0) + it }
        return out
    }
}
