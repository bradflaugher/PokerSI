package com.bradflaugher.pokersi.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.bradflaugher.pokersi.game.OpponentGenerator
import com.bradflaugher.pokersi.game.OpponentProfile
import com.bradflaugher.pokersi.game.PayoutUi
import com.bradflaugher.pokersi.game.RunUiState
import com.bradflaugher.pokersi.game.formatChips
import com.bradflaugher.pokersi.run.Joker
import com.bradflaugher.pokersi.run.Jokers
import com.bradflaugher.pokersi.run.Rarity
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.TableSpec
import com.bradflaugher.pokersi.ui.pixel.BigBanner
import com.bradflaugher.pokersi.ui.pixel.ConfettiBurst
import com.bradflaugher.pokersi.ui.pixel.LedColor
import com.bradflaugher.pokersi.ui.pixel.LedCounter
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
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
import com.bradflaugher.pokersi.ui.pixel.pxRect

/** "SMALL TABLE", "BIG TABLE" or the boss's name. */
fun stageName(spec: TableSpec): String = when {
    spec.isBoss -> "BOSS: ${spec.name.uppercase()}"
    spec.stage == 0 -> "SMALL TABLE"
    else -> "BIG TABLE"
}

@Composable
fun rarityColor(rarity: Rarity): Color {
    val p = PixelTheme.palette
    return when (rarity) {
        Rarity.COMMON -> p.cabinetLight
        Rarity.UNCOMMON -> p.purple
        Rarity.RARE -> p.gold
    }
}

/** A Joker's face: a procedural pixel avatar, grinning. */
fun jokerSeed(joker: Joker): Long = joker.id.hashCode().toLong() * 104_729L + 3

// ---------------------------------------------------------------- in-table HUD

/** The Run mode strip above the table: ante, hands left, chip target, coins and Jokers. */
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
                    PixelTag("ANTE ${state.ante}", p.suitRed, textColor = p.yellow)
                    PixelText(stageName(spec), style = PixelType.Small.copy(fontSize = 8.sp), color = if (spec.isBoss) p.hotPink else p.white, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelText("TARGET", style = PixelType.Label.copy(fontSize = 9.sp), color = p.cardCream, shadowUnits = 0)
                    PixelMeter(myChips.toFloat() / spec.chipTarget, p.green, Modifier.weight(1f), height = 10.dp)
                    PixelText(formatChips(spec.chipTarget), style = PixelType.Label.copy(fontSize = 9.sp), color = p.yellow, shadowUnits = 0)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelText("HANDS LEFT ${state.handsLeft}", style = PixelType.Label.copy(fontSize = 9.sp), color = if (state.handsLeft <= 3) p.red else p.cardCream, shadowUnits = 0)
                    JokerRow(state, run.payout, size = 24.dp)
                }
            }
            LedCounter(run.payout?.coins ?: state.coins, label = "COINS", color = LedColor.Amber, minDigits = 3, segmentUnits = 1, sound = Sfx.Chip)
        }
    }
}

/** The player's Jokers as little cards; tap one for what it does. A triggered Joker bounces. */
@Composable
fun JokerRow(state: RunState, payout: PayoutUi?, size: Dp, modifier: Modifier = Modifier) {
    val hot = payout?.triggers?.getOrNull(payout.shown - 1)?.jokerId
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        state.jokers.mapNotNull(Jokers::byId).forEach { joker ->
            JokerChip(joker, size, hot = joker.id == hot, disabled = state.activeJokers.none { it.id == joker.id })
        }
    }
}

@Composable
private fun JokerChip(joker: Joker, size: Dp, hot: Boolean, disabled: Boolean) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    var open by remember { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(hot) {
        if (hot) {
            pop.snapTo(1.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val rarity = rarityColor(joker.rarity)
    Box(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .arcadeClick { open = !open }
            .drawBehind {
                val w = this.size.width.toInt(); val h = this.size.height.toInt()
                pxBox(0, 0, w, h, unit, p.ink, if (hot) p.yellow else rarity)
                pxBox(unit, unit, w - 2 * unit, h - 2 * unit, unit, p.ink, if (disabled) p.grayDark else p.cardCream)
            }
            .padding(3.dp),
    ) {
        PixelAvatar(jokerSeed(joker), Mood.Happy, size = size, background = false, blink = false)
        if (open) {
            Popup(onDismissRequest = { open = false }) {
                PixelPanel(Modifier.width(220.dp).padding(top = size + 12.dp), style = PanelStyle.Dark, paddingUnits = 2) {
                    PixelText(joker.name.uppercase(), style = PixelType.Small, color = rarity)
                    VGap(4.dp)
                    BodyText(joker.description, small = true)
                    if (disabled) BodyText("Switched off at this table.", color = p.red, small = true)
                }
            }
        }
    }
}

/** The Joker trigger list that pops in after a run hand, coins counting up. */
@Composable
fun PayoutPanel(payout: PayoutUi, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    PixelPanel(modifier, title = "JOKERS!", style = PanelStyle.Felt, paddingUnits = 2) {
        payout.triggers.take(payout.shown).forEachIndexed { i, t ->
            val hot = i == payout.shown - 1
            val pop = remember(t, i) { Animatable(if (hot) 0.3f else 1f) }
            LaunchedEffect(t, i) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
            Row(
                Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Jokers.byId(t.jokerId)?.let { PixelAvatar(jokerSeed(it), Mood.Happy, size = 28.dp, blink = false) }
                PixelText(t.name.uppercase(), Modifier.weight(1f), style = PixelType.Small.copy(fontSize = 9.sp), color = p.white, maxLines = 1)
                PixelTag(t.text.uppercase(), if (t.coins > 0) p.gold else p.green, textColor = p.ink)
            }
        }
    }
}

// ---------------------------------------------------------------- intro

/** The card shown before each run table: who's here, the twist, the target. */
@Composable
fun RunIntroScreen(run: RunUiState, onPlay: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val state = run.state
    val spec = state.table
    val boss = spec.boss
    ArcadeScreen(modifier, swirl = if (boss != null) listOf(Color(0xFF1A0420), p.purpleDark, p.suitRed) else null) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar(if (state.daily) "DAILY RUN" else "RUN MODE", onBack) {
                LedCounter(state.coins, label = "COINS", color = LedColor.Amber, minDigits = 3, segmentUnits = 1)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MarqueeFrame(Modifier.readable(), frameColor = if (boss != null) p.hotPinkDark else p.suitRed) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelText("ANTE ${state.ante}", style = PixelType.Title, color = p.yellow, shadowUnits = 2)
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
                        PixelText(spec.variant.displayName.uppercase().let { if (boss?.rules?.rotateVariants == true) "A NEW GAME EVERY HAND" else it }, style = PixelType.Small, color = p.teal, textAlign = TextAlign.Center)
                    }
                }
                PayTable(
                    listOf(
                        PayRow("WIN", "${formatChips(spec.chipTarget)} CHIPS"),
                        PayRow("WITHIN", "${state.handsLeft} HANDS"),
                        PayRow("YOU START WITH", formatChips(spec.playerChips)),
                        PayRow("BLINDS", "${formatChips(spec.smallBlind)}/${formatChips(spec.bigBlind)}"),
                        PayRow("REWARD", "$${spec.reward}+"),
                    ),
                    Modifier.readable(),
                    title = "THE TABLE",
                )
                OpponentLineup(run.opponents.filterNotNull().let { if (boss != null) it.drop(1) else it })
                if (state.jokers.isNotEmpty()) {
                    SectionHeading("YOUR JOKERS")
                    JokerRow(state, null, size = 36.dp)
                }
            }
            PixelButton(
                "SIT DOWN", onPlay,
                Modifier.readable(420.dp).padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.CenterHorizontally),
                variant = PixelButtonVariant.Deal, sound = Sfx.Deal,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpponentLineup(opponents: List<OpponentProfile>, bossFirst: Boolean = false, modifier: Modifier = Modifier, avatar: Dp = 56.dp) {
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
fun RunClearedScreen(run: RunUiState, onShop: () -> Unit, modifier: Modifier = Modifier) {
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
            PixelText("ANTE ${run.state.ante} - ${stageName(run.state.table)}", style = PixelType.Small, color = p.white, textAlign = TextAlign.Center)
            if (reward != null) {
                PayTable(
                    listOf(
                        PayRow("TABLE", "$${reward.base}"),
                        PayRow("HANDS TO SPARE", "+$${reward.handsLeftBonus}"),
                        PayRow("INTEREST", "+$${reward.interest}"),
                        PayRow("JOKERS", "+$${reward.jokerBonus}"),
                        PayRow("TOTAL", "$${reward.total}"),
                    ),
                    Modifier.readable(),
                    title = "CASH OUT",
                    highlight = 4,
                )
            }
            LedCounter(run.state.coins, label = "COINS", color = LedColor.Amber, minDigits = 4, sound = Sfx.Chip)
            Spacer(Modifier.height(8.dp))
            PixelButton("TO THE SHOP", onShop, Modifier.readable(420.dp), variant = PixelButtonVariant.Bet, sound = Sfx.Chip)
        }
    }
}

// ---------------------------------------------------------------- shop

/** The Joker shop between tables. */
@Composable
fun ShopScreen(
    run: RunUiState,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit,
    onReroll: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = PixelTheme.palette
    val state = run.state
    ArcadeScreen(modifier, swirl = listOf(Color(0xFF1B0A3A), p.purple, p.hotPink)) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("JOKER SHOP", onBack) {
                LedCounter(state.coins, label = "COINS", color = LedColor.Amber, minDigits = 3, segmentUnits = 1, sound = Sfx.Chip)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelText("NEXT UP: ANTE ${if (state.stage + 1 >= TableSpec.STAGES_PER_ANTE) state.ante + 1 else state.ante}", style = PixelType.Small, color = p.cardCream)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardW = ((maxWidth - 16.dp) / 3).coerceAtMost(220.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Top) {
                        state.shop.mapNotNull(Jokers::byId).forEach { joker ->
                            JokerOffer(joker, state.price(joker), state.canBuy(joker.id), { onBuy(joker.id) }, Modifier.width(cardW))
                        }
                        if (state.shop.isEmpty()) BodyText("Sold out!", color = p.yellow)
                    }
                }
                Row(Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("REROLL $${state.rerollCost}", onReroll, Modifier.weight(1f), variant = PixelButtonVariant.Hold, enabled = state.coins >= state.rerollCost, textStyle = PixelType.Small, sound = Sfx.Flip)
                }
                SectionHeading("YOUR JOKERS ${state.jokers.size}/${RunState.MAX_JOKERS}")
                if (state.jokers.isEmpty()) {
                    BodyText("None yet. Buy one, they're funny.", color = p.cardCream, small = true)
                }
                state.jokers.mapNotNull(Jokers::byId).forEach { joker ->
                    OwnedJoker(joker, state.sellValue(joker), { onSell(joker.id) }, Modifier.readable())
                }
                VGap(8.dp)
            }
            PixelButton(
                "NEXT TABLE", onLeave,
                Modifier.readable(420.dp).padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.CenterHorizontally),
                variant = PixelButtonVariant.Deal, sound = Sfx.Deal,
            )
        }
    }
}

/** A Joker for sale, drawn as a big cream card. */
@Composable
fun JokerOffer(joker: Joker, price: Int, canBuy: Boolean, onBuy: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val rarity = rarityColor(joker.rarity)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(212.dp)
                .drawBehind {
                    val w = size.width.toInt(); val h = size.height.toInt()
                    pxBox(unit, unit * 2, w - unit, h - unit * 2, unit, p.ink.copy(alpha = 0.5f), p.ink.copy(alpha = 0.5f))
                    pxBox(0, 0, w - unit, h - unit * 2, unit, p.ink, rarity)
                    pxBox(unit * 2, unit * 2, w - unit * 5, h - unit * 6, unit, p.ink, p.cardCream)
                    pxRect(unit * 3, unit * 3, w - unit * 7, unit, p.cardHighlight)
                }
                .padding(horizontal = 9.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PixelTag(joker.rarity.label.uppercase(), rarity, textColor = if (joker.rarity == Rarity.RARE) p.ink else p.white)
            PixelAvatar(jokerSeed(joker), Mood.Happy, size = 52.dp)
            PixelText(joker.name.uppercase(), style = PixelType.LabelBold.copy(fontSize = 11.sp), color = p.suitRedDark, shadowUnits = 0, textAlign = TextAlign.Center, maxLines = 2)
            PixelText(joker.description, style = PixelType.BodySmall.copy(fontSize = 17.sp), color = p.ink, shadowUnits = 0, textAlign = TextAlign.Center)
        }
        PixelButton(
            "BUY $$price", onBuy, Modifier.fillMaxWidth(),
            variant = PixelButtonVariant.Bet, enabled = canBuy, textStyle = PixelType.Small, sound = Sfx.Chip,
        )
    }
}

@Composable
private fun OwnedJoker(joker: Joker, sellValue: Int, onSell: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    PixelPanel(modifier, style = PanelStyle.Dark, paddingUnits = 1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelAvatar(jokerSeed(joker), Mood.Happy, size = 40.dp, blink = false)
            Column(Modifier.weight(1f)) {
                PixelText(joker.name.uppercase(), style = PixelType.Small.copy(fontSize = 9.sp), color = rarityColor(joker.rarity))
                PixelText(joker.description, style = PixelType.BodySmall.copy(fontSize = 17.sp), color = p.cardCream, shadowUnits = 0)
            }
            SmallButton("SELL $$sellValue", onSell, variant = PixelButtonVariant.Pink, sound = Sfx.Chip)
        }
    }
}

// ---------------------------------------------------------------- game over / victory

@Composable
fun RunOverScreen(run: RunUiState, victory: Boolean, onNewRun: () -> Unit, onMenu: () -> Unit, onContinue: () -> Unit = {}, modifier: Modifier = Modifier) {
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
                if (victory) "You beat the eighth boss. The house is speechless. Mostly." else "The house always wins. Eventually.",
                color = p.cardCream, align = TextAlign.Center,
            )
            PayTable(
                listOf(
                    PayRow("REACHED", "ANTE ${state.ante}"),
                    PayRow("TABLES CLEARED", "${s.tablesCleared}"),
                    PayRow("HANDS WON", "${s.handsWon}/${s.handsPlayed}"),
                    PayRow("BIGGEST POT", formatChips(s.biggestPot)),
                    PayRow("BEST HAND", s.bestHand?.label?.uppercase() ?: "-"),
                    PayRow("COINS EARNED", "$${s.coinsEarned}"),
                    PayRow("JOKERS", "${state.jokers.size}"),
                ),
                Modifier.readable(),
                title = if (state.daily) "DAILY RUN" else "THE RUN",
            )
            PixelText("SEED ${java.lang.Long.toHexString(state.seed).uppercase().takeLast(8)}", style = PixelType.Small.copy(fontSize = 9.sp), color = p.gray)
            if (victory) {
                PixelButton("KEEP GOING", onContinue, Modifier.readable(420.dp), variant = PixelButtonVariant.Call, subtitle = "Endless mode")
            }
            Row(Modifier.readable(420.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("MENU", onMenu, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
                PixelButton("NEW RUN", onNewRun, Modifier.weight(1.5f), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
            }
        }
    }
}
