package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.game.OpponentGenerator
import com.bradflaugher.pokersi.game.OpponentProfile
import com.bradflaugher.pokersi.game.RunUiState
import com.bradflaugher.pokersi.game.formatChips
import com.bradflaugher.pokersi.run.Risk
import com.bradflaugher.pokersi.run.TableSpec
import com.bradflaugher.pokersi.ui.pixel.BigBanner
import com.bradflaugher.pokersi.ui.pixel.ConfettiBurst
import com.bradflaugher.pokersi.ui.pixel.LedColor
import com.bradflaugher.pokersi.ui.pixel.LedCounter
import com.bradflaugher.pokersi.ui.pixel.MarqueeFrame
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.ui.pixel.PayRow
import com.bradflaugher.pokersi.ui.pixel.PayTable
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx

/** "LOW STAKES", "HIGH STAKES" or the boss's name. */
fun stageName(spec: TableSpec): String = when {
    spec.isBoss -> "BOSS: ${spec.name.uppercase()}"
    else -> spec.risk.label
}

private fun blinds(spec: TableSpec) = "${formatChips(spec.smallBlind)}/${formatChips(spec.bigBlind)}"

// ---------------------------------------------------------------- in-table HUD

/** The Poker Tour strip above the table: the stop, the chip target and hands left. */
@Composable
fun RunHud(run: RunUiState, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val state = run.state
    val spec = state.table
    val myChips = state.progress.chips.firstOrNull() ?: 0
    PixelPanel(modifier.fillMaxWidth(), style = if (spec.isBoss) PanelStyle.Dark else PanelStyle.Cabinet, paddingUnits = 1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelTag("STOP ${state.level}", p.suitRed, textColor = p.yellow)
                    PixelText(stageName(spec), style = PixelType.Small.copy(fontSize = 8.sp), color = if (spec.isBoss) p.hotPink else p.white, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelText("TARGET", style = PixelType.Label.copy(fontSize = 9.sp), color = p.cardCream, shadowUnits = 0)
                    PixelMeter(myChips.toFloat() / spec.chipTarget, p.green, Modifier.weight(1f), height = 10.dp)
                    PixelText(formatChips(spec.chipTarget), style = PixelType.Label.copy(fontSize = 9.sp), color = p.yellow, shadowUnits = 0)
                }
                PixelText("HANDS LEFT ${state.handsLeft}", style = PixelType.Label.copy(fontSize = 9.sp), color = if (state.handsLeft <= 3) p.red else p.cardCream, shadowUnits = 0)
            }
            LedCounter(spec.reward, label = "BONUS", color = LedColor.Amber, minDigits = 3, segmentUnits = 1)
        }
    }
}

// ---------------------------------------------------------------- intro

/** The card shown before each tour table: who's here, the twist, the target. */
@Composable
fun RunIntroScreen(run: RunUiState, onPlay: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val state = run.state
    val spec = state.table
    val boss = spec.boss
    ArcadeScreen(modifier, swirl = if (boss != null) listOf(Color(0xFF1A0420), p.purpleDark, p.suitRed) else null) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar(if (state.daily) "DAILY TOUR" else "POKER TOUR", onBack) {
                LedCounter(state.playerChips, label = "CHIPS", color = LedColor.Amber, minDigits = 4, segmentUnits = 1)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MarqueeFrame(Modifier.readable(), frameColor = if (boss != null) p.hotPinkDark else p.suitRed) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelText("STOP ${state.level}", style = PixelType.Title, color = p.yellow, shadowUnits = 2)
                        PixelText(
                            if (boss != null) "BOSS TABLE" else stageName(spec),
                            style = PixelType.Heading,
                            color = if (boss != null) p.hotPink else p.white,
                        )
                        if (boss != null) {
                            PixelAvatar(OpponentGenerator.bossAvatarSeed(boss), Mood.Smug, size = 72.dp)
                            PixelText(boss.name.uppercase(), style = PixelType.Title.copy(fontSize = 18.sp), color = p.hotPink, shadowUnits = 2, textAlign = TextAlign.Center)
                            BodyText(boss.description, color = p.cardCream, align = TextAlign.Center)
                        }
                        PixelText(variantName(spec), style = PixelType.Small, color = p.teal, textAlign = TextAlign.Center)
                    }
                }
                PayTable(
                    listOf(
                        PayRow("WIN", "${formatChips(spec.chipTarget)} CHIPS"),
                        PayRow("WITHIN", "${state.handsLeft} HANDS"),
                        PayRow("YOU SIT WITH", formatChips(state.progress.chips.firstOrNull() ?: spec.playerChips)),
                        PayRow("BLINDS", blinds(spec)),
                        PayRow("BONUS", "+${formatChips(spec.reward)}"),
                    ),
                    Modifier.readable(),
                    title = "THE TABLE",
                )
                OpponentLineup(run.opponents.filterNotNull().let { if (boss != null) it.drop(1) else it })
            }
            PixelButton(
                "SIT DOWN", onPlay,
                Modifier.readable(420.dp).padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.CenterHorizontally),
                variant = PixelButtonVariant.Deal, sound = Sfx.Deal,
            )
        }
    }
}

private fun variantName(spec: TableSpec): String =
    if (spec.boss?.rules?.rotateVariants == true) "A NEW GAME EVERY HAND" else spec.variant.displayName.uppercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpponentLineup(opponents: List<OpponentProfile>, modifier: Modifier = Modifier, bossFirst: Boolean = false, avatar: Dp = 56.dp) {
    val p = PixelTheme.palette
    FlowRow(modifier.readable(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opponents.forEachIndexed { i, o ->
            Column(Modifier.width(avatar + 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PixelAvatar(o.avatarSeed, if (bossFirst && i == 0) Mood.Angry else Mood.Neutral, size = avatar)
                PixelText(o.name.uppercase(), style = PixelType.LabelBold.copy(fontSize = 10.sp), color = if (bossFirst && i == 0) p.hotPink else p.yellow, textAlign = TextAlign.Center, maxLines = 2)
                PixelText(o.style.name.uppercase(), style = PixelType.Label.copy(fontSize = 9.sp), color = p.cardCream, textAlign = TextAlign.Center, shadowUnits = 0)
            }
        }
    }
}

// ---------------------------------------------------------------- cleared

@Composable
fun RunClearedScreen(run: RunUiState, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val reward = run.reward ?: run.state.lastReward
    ArcadeScreen(modifier) {
        ConfettiBurst(run.state.stats.tablesCleared, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VGap(24.dp)
            BigBanner("TABLE CLEARED!", style = PixelType.Banner.copy(fontSize = 22.sp), sound = Sfx.Win)
            PixelText("STOP ${run.state.level} - ${stageName(run.state.table)}", style = PixelType.Small, color = p.white, textAlign = TextAlign.Center)
            if (reward != null) {
                PayTable(
                    listOf(
                        PayRow("TABLE BONUS", "+${formatChips(reward.bonus)}"),
                        PayRow("HANDS TO SPARE", "+${formatChips(reward.handsLeftBonus)}"),
                        PayRow("TOTAL BONUS", "+${formatChips(reward.total)}"),
                    ),
                    Modifier.readable(),
                    title = "CASH OUT",
                    highlight = 2,
                )
            }
            LedCounter(run.state.playerChips, label = "YOUR CHIPS", color = LedColor.Amber, minDigits = 5, sound = Sfx.Chip)
            Spacer(Modifier.height(8.dp))
            PixelButton("CHOOSE NEXT TABLE", onNext, Modifier.readable(420.dp), variant = PixelButtonVariant.Bet, sound = Sfx.Chip)
        }
    }
}

// ---------------------------------------------------------------- choose the next table

/** Between tables: pick the next one. Bigger risk, bigger bonus. */
@Composable
fun ChooseTableScreen(run: RunUiState, onChoose: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val state = run.state
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("CHOOSE YOUR TABLE", onBack) {
                LedCounter(state.playerChips, label = "CHIPS", color = LedColor.Amber, minDigits = 4, segmentUnits = 1)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelText("STOP ${state.level + 1}", style = PixelType.Small, color = p.cardCream)
                state.offers.forEachIndexed { i, spec ->
                    TableOffer(spec, run.offerOpponents.getOrNull(i).orEmpty(), { onChoose(i) }, Modifier.readable())
                }
                VGap(8.dp)
            }
        }
    }
}

@Composable
private fun TableOffer(spec: TableSpec, opponents: List<OpponentProfile>, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val boss = spec.boss
    val accent = when (spec.risk) {
        Risk.LOW -> p.green
        Risk.HIGH -> p.yellow
        Risk.BOSS -> p.hotPink
    }
    PixelPanel(modifier, title = spec.risk.label, style = if (boss != null) PanelStyle.Dark else PanelStyle.Cabinet, paddingUnits = 2) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelText(variantName(spec), style = PixelType.Small, color = p.teal)
            if (boss != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelAvatar(OpponentGenerator.bossAvatarSeed(boss), Mood.Smug, size = 40.dp, blink = false)
                    Column(Modifier.weight(1f)) {
                        PixelText(boss.name.uppercase(), style = PixelType.Small, color = p.hotPink)
                        BodyText(boss.description, color = p.cardCream, small = true)
                    }
                }
            }
            OfferLine("BLINDS", blinds(spec))
            OfferLine("TARGET", "${formatChips(spec.chipTarget)} IN ${spec.handLimit} HANDS")
            OfferLine("BONUS", "+${formatChips(spec.reward)} CHIPS", accent)
            OpponentLineup(opponents.let { if (boss != null) it.drop(1) else it }, avatar = 32.dp)
            PixelButton(
                "PLAY THIS TABLE", onChoose, Modifier.fillMaxWidth(),
                variant = if (boss != null) PixelButtonVariant.Pink else PixelButtonVariant.Deal, textStyle = PixelType.Small, sound = Sfx.Deal,
            )
        }
    }
}

@Composable
private fun OfferLine(name: String, value: String, color: Color = PixelTheme.palette.yellow) {
    val p = PixelTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PixelText(name, Modifier.weight(1f), style = PixelType.Label.copy(fontSize = 10.sp), color = p.cardCream, shadowUnits = 0)
        PixelText(value, style = PixelType.LabelBold.copy(fontSize = 10.sp), color = color, shadowUnits = 0)
    }
}

// ---------------------------------------------------------------- game over / victory

@Composable
fun RunOverScreen(run: RunUiState, victory: Boolean, onNewRun: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier, onContinue: () -> Unit = {}) {
    val p = PixelTheme.palette
    val state = run.state
    val s = state.stats
    ArcadeScreen(modifier, swirl = if (victory) null else listOf(Color(0xFF12040C), p.redDark, p.purpleDark)) {
        if (victory) ConfettiBurst(Unit, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VGap(20.dp)
            BigBanner(
                if (victory) "YOU WIN!" else "GAME OVER",
                style = PixelType.Banner,
                ribbon = if (victory) p.hotPink else p.redDark,
                color = if (victory) p.yellow else p.white,
                sound = if (victory) Sfx.Win else Sfx.Lose,
            )
            BodyText(
                if (victory) "You cleared the tour's final table. The house is speechless. Mostly." else "The house always wins. Eventually.",
                color = p.cardCream, align = TextAlign.Center,
            )
            PayTable(
                listOf(
                    PayRow("REACHED", "STOP ${state.level}"),
                    PayRow("TABLES CLEARED", "${s.tablesCleared}"),
                    PayRow("HANDS WON", "${s.handsWon}/${s.handsPlayed}"),
                    PayRow("BIGGEST POT", formatChips(s.biggestPot)),
                    PayRow("BEST HAND", s.bestHand?.label?.uppercase() ?: "-"),
                    PayRow("MOST CHIPS", formatChips(maxOf(s.peakChips, state.playerChips))),
                ),
                Modifier.readable(),
                title = if (state.daily) "DAILY TOUR" else "THE TOUR",
            )
            PixelText("SEED ${java.lang.Long.toHexString(state.seed).uppercase().takeLast(8)}", style = PixelType.Small.copy(fontSize = 9.sp), color = p.gray)
            if (victory) {
                PixelButton("KEEP GOING", onContinue, Modifier.readable(420.dp), variant = PixelButtonVariant.Call, subtitle = "Endless mode")
            }
            Row(Modifier.readable(420.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("MENU", onMenu, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
                PixelButton("NEW TOUR", onNewRun, Modifier.weight(1.5f), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
            }
        }
    }
}
