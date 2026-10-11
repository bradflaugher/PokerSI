package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.bradflaugher.pokersi.ui.pixel.PixelPalette
import com.bradflaugher.pokersi.game.StartingHands
import com.bradflaugher.pokersi.game.HandTally
import com.bradflaugher.pokersi.engine.Rank
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.game.LifetimeStats
import com.bradflaugher.pokersi.game.formatChips
import com.bradflaugher.pokersi.ui.pixel.LedColor
import com.bradflaugher.pokersi.ui.pixel.LedCounter
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.ui.pixel.PayRow
import com.bradflaugher.pokersi.ui.pixel.PayTable
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelCard
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType

/**
 * The cabinet's records: lifetime hands, wins and win rate on LED counters,
 * the best hand ever as pixel cards, the pay-table of records, the Hold'em
 * starting-hand chart ([HandChart]) and the Poker Tour bests. RESET STATS
 * asks first.
 */
@Composable
fun StatsScreen(stats: LifetimeStats, onReset: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("STATS", onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.readable(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    LedStat("HANDS", stats.handsPlayed, LedColor.Red, "Hands played ${stats.handsPlayed}")
                    LedStat("WON", stats.handsWon, LedColor.Green, "Hands won ${stats.handsWon}")
                    LedStat("WIN %", stats.winRate ?: 0, LedColor.Amber, "Win rate ${stats.winRate ?: 0} percent", digits = 3)
                }
                BestHandPanel(stats)
                PayTable(
                    listOf(
                        PayRow("HANDS PLAYED", formatChips(stats.handsPlayed)),
                        PayRow("HANDS WON", formatChips(stats.handsWon)),
                        PayRow("WIN RATE", stats.winRate?.let { "$it%" } ?: "-"),
                        PayRow("BIGGEST POT", if (stats.biggestPot > 0) formatChips(stats.biggestPot) else "-"),
                        PayRow("FAVORITE", stats.favoriteGame?.displayName?.uppercase() ?: "-"),
                    ),
                    Modifier.readable(),
                    title = "LIFETIME",
                )
                HandChart(stats.holdemHands)
                PayTable(
                    listOf(
                        PayRow("TOURS STARTED", "${stats.toursStarted}"),
                        PayRow("BEST TOUR", tables(stats.bestTour)),
                        PayRow("DAILY BEST", tables(stats.dailyBest)),
                    ),
                    Modifier.readable(),
                    title = "POKER TOUR",
                )
                PixelPanel(Modifier.readable(), title = "RESET", style = PanelStyle.Dark, paddingUnits = 2) {
                    if (!confirmReset) {
                        SmallButton("RESET STATS", { confirmReset = true }, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal)
                    } else {
                        BodyText("Wipe every record? Your best hand too?", color = p.yellow, small = true)
                        VGap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("KEEP THEM", { confirmReset = false }, Modifier.weight(1f))
                            SmallButton("YES, RESET", { confirmReset = false; onReset() }, Modifier.weight(1f), variant = PixelButtonVariant.Deal)
                        }
                    }
                }
                PixelText(
                    "RECORDS STAY ON THIS PHONE.",
                    style = PixelType.Label.copy(fontSize = 10.sp), color = p.gray, textAlign = TextAlign.Center, shadowUnits = 0,
                )
                VGap(12.dp)
            }
        }
    }
}

private fun tables(n: Int): String = when (n) {
    0 -> "-"
    1 -> "1 TABLE"
    else -> "$n TABLES"
}

@Composable
private fun LedStat(label: String, value: Int, color: LedColor, spoken: String, digits: Int = 5) {
    LedCounter(value, label = label, color = color, minDigits = digits, segmentUnits = 1, contentDescription = spoken)
}

@Composable
private fun BestHandPanel(stats: LifetimeStats) {
    val p = PixelTheme.palette
    val best = stats.bestHand
    PixelPanel(Modifier.readable(), title = "BEST HAND EVER", style = PanelStyle.Felt, paddingUnits = 2) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val cards = best?.cardList
                for (i in 0 until 5) {
                    PixelCard(cards?.getOrNull(i), faceUp = cards != null, size = 54.dp, highlighted = cards != null)
                }
            }
            if (best != null) {
                PixelText(best.category.label.uppercase(), style = PixelType.Heading, color = p.yellow, textAlign = TextAlign.Center)
                if (!best.description.equals(best.category.label, ignoreCase = true)) {
                    BodyText(best.description, color = p.cardCream, align = TextAlign.Center, small = true)
                }
                PixelText(best.variant.displayName.uppercase(), style = PixelType.Small.copy(fontSize = 8.sp), color = p.gray, shadowUnits = 0)
            } else {
                PixelText("NO HAND YET", style = PixelType.Heading, color = p.yellow, textAlign = TextAlign.Center)
                BodyText("Play a hand to the end and your best five cards land here.", color = p.cardCream, align = TextAlign.Center, small = true)
            }
        }
    }
}

/**
 * The classic 13x13 Hold'em chart, coloured by how often the player played
 * each starting hand (called, bet or raised before the flop): pairs on the
 * diagonal, suited above, offsuit below. Hands never dealt stay dark. Tap
 * one for its numbers: the whole grid is one large touch target (a cell is
 * far below 48dp), and one TalkBack node that reads out the chart.
 */
@Composable
internal fun HandChart(hands: Map<String, HandTally>) {
    val p = PixelTheme.palette
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val dealt = hands.values.sumOf { it.dealt }
    val played = hands.values.sumOf { it.played }
    PixelPanel(Modifier.readable(), title = "HOLD'EM HANDS", style = PanelStyle.Dark, paddingUnits = 2) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText(
                if (dealt == 0) "Play some Texas Hold'em: every starting hand you're dealt lands here, coloured by how often you played it."
                else "How often you played each starting hand (called or raised before the flop). Pairs run down the middle, suited hands above, offsuit below.",
                color = p.cardCream, small = true,
            )
            val spoken = chartDescription(hands, picked)
            BoxWithConstraints(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
            ) {
                val gap = 1.dp
                val cell = (maxWidth - gap * 12) / 13
                val pitch = with(LocalDensity.current) { (cell + gap).toPx() }
                Column(
                    Modifier.pointerInput(pitch) {
                        detectTapGestures { at ->
                            val row = (at.y / pitch).toInt().coerceIn(0, 12)
                            val col = (at.x / pitch).toInt().coerceIn(0, 12)
                            val cls = StartingHands.at(row, col)
                            picked = if (picked == cls) null else cls
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (row in 0 until 13) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (col in 0 until 13) {
                                val cls = StartingHands.at(row, col)
                                ChartCell(cls, hands[cls], cls == picked, cell)
                            }
                        }
                    }
                }
            }
            ChartLegend()
            val pickedLine = picked?.let { cls ->
                val t = hands[cls] ?: HandTally()
                if (t.dealt == 0) "$cls: NOT DEALT YET" else "$cls: DEALT ${t.dealt}, PLAYED ${t.played} (${t.playRate}%)"
            }
            PixelText(
                pickedLine ?: if (dealt == 0) "NO HOLD'EM HANDS YET" else "PLAYED ${(played * 100 + dealt / 2) / dealt}% OF $dealt HANDS",
                Modifier.fillMaxWidth(),
                style = PixelType.Label.copy(fontSize = 11.sp),
                color = p.yellow,
                textAlign = TextAlign.Center,
                shadowUnits = 0,
            )
        }
    }
}

/** Never played (dark) through red to always (gold); a never-dealt hand is near black. */
private fun heat(p: PixelPalette, rate: Int?): Color = when {
    rate == null -> p.cabinetBlack
    rate <= 50 -> lerp(p.cabinetDeep, p.suitRed, rate / 50f)
    else -> lerp(p.suitRed, p.gold, (rate - 50) / 50f)
}

@Composable
private fun ChartCell(cls: String, tally: HandTally?, picked: Boolean, size: Dp) {
    val p = PixelTheme.palette
    val rate = tally?.playRate
    val fill = heat(p, rate)
    Box(
        Modifier
            .size(size)
            .background(fill)
            .then(if (picked) Modifier.border(2.dp, p.white) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(
            cls,
            style = PixelType.Label.copy(fontSize = 7.sp),
            color = if (rate != null && rate > 60) p.ink else if (rate == null) p.gray else p.cardCream,
            shadowUnits = 0,
            maxLines = 1,
        )
    }
}

/**
 * What TalkBack reads for the chart: the totals, the hands played most
 * often, and the picked hand's numbers.
 */
internal fun chartDescription(hands: Map<String, HandTally>, picked: String?): String {
    val dealt = hands.values.sumOf { it.dealt }
    if (dealt == 0) return "Hold'em starting-hand chart: no hands yet."
    val played = hands.values.sumOf { it.played }
    val top = hands.filterValues { it.played > 0 }.entries
        .sortedWith(compareByDescending<Map.Entry<String, HandTally>> { it.value.playRate }.thenByDescending { it.value.dealt })
        .take(5).joinToString(", ") { (cls, t) -> "${spokenHand(cls)} ${t.playRate} percent" }
    val pick = picked?.let { cls ->
        val t = hands[cls] ?: HandTally()
        " Selected: ${spokenHand(cls)}, " + if (t.dealt == 0) "not dealt yet." else "dealt ${t.dealt}, played ${t.played}."
    }.orEmpty()
    return "Hold'em starting-hand chart: you played ${(played * 100 + dealt / 2) / dealt} percent of $dealt hands." +
        (if (top.isNotEmpty()) " Played most: $top." else "") + pick + " Tap a hand on the chart for its numbers."
}

/** "AKs" read out as "Ace King suited". */
private fun spokenHand(cls: String): String {
    fun name(c: Char) = Rank.entries.first { it.letter == c }.label
    val ranks = "${name(cls[0])} ${name(cls[1])}"
    return when (cls.getOrNull(2)) {
        's' -> "$ranks suited"
        'o' -> "$ranks offsuit"
        else -> "Pair of ${Rank.entries.first { it.letter == cls[0] }.plural}"
    }
}

@Composable
private fun ChartLegend() {
    val p = PixelTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelText("NEVER", style = PixelType.Label.copy(fontSize = 9.sp), color = p.gray, shadowUnits = 0)
        Row(Modifier.weight(1f).height(10.dp)) {
            for (i in 0..10) Box(Modifier.weight(1f).fillMaxHeight().background(heat(p, i * 10)))
        }
        PixelText("ALWAYS", style = PixelType.Label.copy(fontSize = 9.sp), color = p.gray, shadowUnits = 0)
    }
}
