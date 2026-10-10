package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
 * the best hand ever as pixel cards, the pay-table of records, and the
 * Poker Tour bests. RESET STATS asks first.
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
