package com.bradflaugher.pokersi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.ui.pixel.AnimatedPixelCard
import com.bradflaugher.pokersi.ui.pixel.BubbleTail
import com.bradflaugher.pokersi.ui.pixel.LocalPixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.LocalSoundPlayer
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelGlyph
import com.bradflaugher.pokersi.ui.pixel.PixelGlyphBox
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.ui.pixel.Sfx
import com.bradflaugher.pokersi.ui.pixel.SpeechBubble
import com.bradflaugher.pokersi.ui.pixel.pxRect
import kotlinx.coroutines.delay
import kotlin.math.abs

/** One page of the tour: a title, the dealer's mood and line, and a little live picture. */
internal class TourStep(val title: String, val mood: Mood, val line: String, val art: @Composable () -> Unit)

/** The dealer who hosts the tour (a fixed face from the avatar generator). */
private const val DEALER_SEED = 4242L
private const val HOUSE_BOT_SEED = 90210L

internal val TourSteps: List<TourStep> = listOf(
    TourStep(
        "MEET THE TABLE", Mood.Happy,
        "Welcome in! I'm Dee, your dealer. Everyone you play here is an AI character, thinking right on this phone. Offline.",
    ) { MeetArt() },
    TourStep(
        "YOUR CARDS", Mood.Neutral,
        "Your cards sit at the bottom, shared cards in the middle. I spell out your best hand, so you never have to squint.",
    ) { CardsArt() },
    TourStep(
        "YOUR MOVE", Mood.Smug,
        "On your turn the buttons light up. No clock, no rush. Tap one below to see what it does!",
    ) { MoveArt() },
    TourStep(
        "PICK A GAME", Mood.Happy,
        "QUICK GAME is one table, six kinds of poker. POKER TOUR climbs ten tables of rising stakes, chips and all.",
    ) { ModesArt() },
    TourStep(
        "BRAINS", Mood.Thinking,
        "Grab an AI brain in MODELS. Until then, House Bot plays every seat. PAUSE or Back stops a hand, and HOW TO has every rule.",
    ) { BrainsArt() },
)

/**
 * The first-run tour over the title screen: Dee the dealer walks through
 * five pages (the AI opponents, your cards, your move, the two modes, models
 * and help), each with a small live picture made of the real pieces.
 * NEXT, BACK, a swipe or a tap on the panel moves between pages. Never
 * blocks: SKIP, Back or a tap outside the panel closes it, and SETTINGS >
 * REPLAY TOUR (or HOW TO > TAKE THE TOUR) brings it back. Animations rest
 * when the system turns animations off.
 */
@Composable
fun TourOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, startStep: Int = 0) {
    val sound = LocalSoundPlayer.current
    var step by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, TourSteps.lastIndex)) }
    var forward by remember { mutableStateOf(true) }
    val last = TourSteps.lastIndex
    fun go(to: Int, play: Boolean = true) {
        if (to > last) { onDismiss(); return }
        if (to < 0 || to == step) return
        forward = to > step
        step = to
        if (play) sound.play(Sfx.Flip)
    }
    BackHandler(onBack = onDismiss)
    val still = !PixelTheme.animated || systemAnimationsOff()
    CompositionLocalProvider(LocalPixelRenderMode provides if (still) PixelRenderMode.Static else LocalPixelRenderMode.current) {
        // The scrim is a sibling behind the panel, not its parent: a clickable parent would
        // merge the whole panel into one "dismiss" node for TalkBack.
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xC8000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Skip the tour", onClick = onDismiss),
            )
            TabletScaled(Modifier.fillMaxSize().safeDrawingPadding()) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val wide = maxWidth > maxHeight && maxHeight < 560.dp
                    val swipe = rememberUpdatedState { dx: Float -> if (dx < 0) go(step + 1) else go(step - 1) }
                    PixelPanel(
                        Modifier
                            .widthIn(max = if (wide) 720.dp else 440.dp)
                            .fillMaxWidth()
                            .padding(if (wide) 8.dp else 16.dp)
                            // A tap on the panel turns the page (its buttons handle their own taps)...
                            .pointerInput(Unit) { detectTapGestures { swipe.value(-1f) } }
                            // ...and so does a swipe.
                            .pointerInput(Unit) {
                                var dx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dx = 0f },
                                    onDragEnd = { if (abs(dx) > 48.dp.toPx()) swipe.value(dx) },
                                ) { change, amount ->
                                    change.consume()
                                    dx += amount
                                }
                            }
                            .semantics {
                                paneTitle = "Tour"
                                isTraversalGroup = true
                            },
                        paddingUnits = 3,
                    ) {
                        TourHeader(step, onSkip = onDismiss)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            AnimatedContent(
                                targetState = step,
                                transitionSpec = {
                                    if (still) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else {
                                        val dir = if (forward) 1 else -1
                                        (slideInHorizontally(tween(260)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
                                            (slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(160)))
                                    }
                                },
                                label = "tour",
                            ) { shown ->
                                StepBody(shown, wide)
                            }
                        }
                        VGap(10.dp)
                        TourControls(step, onBack = { go(step - 1, play = false) }, onNext = { go(step + 1, play = false) })
                    }
                }
            }
        }
    }
}

/** True when the system's animator duration scale is off (Developer options or accessibility "Remove animations"). */
@Composable
private fun systemAnimationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
private fun TourHeader(step: Int, onSkip: () -> Unit) {
    val p = PixelTheme.palette
    val s = TourSteps[step]
    val spoken = "Step ${step + 1} of ${TourSteps.size}. ${s.title.lowercase().replaceFirstChar(Char::uppercase)}. ${s.line}"
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelTag("${step + 1}/${TourSteps.size}", p.hotPink, Modifier.clearAndSetSemantics { }, style = PixelType.Small.copy(fontSize = 9.sp))
        PixelText(
            s.title,
            Modifier
                .weight(1f)
                // Read in full on every page turn: the bubble types out, so it is hidden from TalkBack.
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken
                },
            style = PixelType.Small,
            color = p.yellow,
            maxLines = 1,
        )
        SmallButton("SKIP", onSkip, Modifier.widthIn(min = 72.dp))
    }
}

/** One page: the dealer's line, the dealer, and the page's picture. */
@Composable
private fun StepBody(index: Int, wide: Boolean) {
    val s = TourSteps[index]
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { DealerSays(s) }
            Box(Modifier.weight(1f).height(ART_HEIGHT), contentAlignment = Alignment.Center) { s.art() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            DealerSays(s)
            VGap(8.dp)
            Box(Modifier.fillMaxWidth().height(ART_HEIGHT), contentAlignment = Alignment.Center) { s.art() }
        }
    }
}

private val ART_HEIGHT = 168.dp

/**
 * Dee's speech bubble, typing out, over Dee. The bubble is as tall as the
 * longest page's (laid out invisibly underneath), so turning a page never
 * makes the panel jump.
 */
@Composable
private fun DealerSays(s: TourStep) {
    val p = PixelTheme.palette
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
            TourSteps.forEach { other ->
                SpeechBubble(other.line, Modifier.fillMaxWidth().alpha(0f), tail = BubbleTail.BottomStart, textStyle = PixelType.BodySmall)
            }
        }
        SpeechBubble(s.line, Modifier.matchParentSize(), tail = BubbleTail.BottomStart, textStyle = PixelType.BodySmall, charsPerSecond = 60)
    }
    Row(Modifier.padding(start = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelAvatar(DEALER_SEED, s.mood, size = 44.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PixelText("DEE", style = PixelType.Small, color = p.yellow)
            PixelText("YOUR DEALER", style = PixelType.Label.copy(fontSize = 10.sp), color = p.gray, shadowUnits = 0)
        }
    }
}

@Composable
private fun TourControls(step: Int, onBack: () -> Unit, onNext: () -> Unit) {
    val lastStep = step == TourSteps.lastIndex
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton("BACK", onBack, Modifier.weight(1f), enabled = step > 0)
        StepDots(step, Modifier.weight(1f))
        PixelButton(
            if (lastStep) "LET'S PLAY!" else "NEXT",
            onNext,
            Modifier.weight(1.4f).height(48.dp),
            variant = if (lastStep) PixelButtonVariant.Deal else PixelButtonVariant.Call,
            textStyle = PixelType.Small,
            sound = if (lastStep) Sfx.Deal else Sfx.Flip,
        )
    }
}

/** Which page this is, as a row of pixel squares (the header says it aloud). */
@Composable
private fun StepDots(step: Int, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Canvas(modifier.height(12.dp).clearAndSetSemantics { }) {
        val n = TourSteps.size
        val dot = unit * 4
        val gap = unit * 3
        val total = n * dot + (n - 1) * gap + unit * 2
        var x = (size.width.toInt() - total) / 2
        val y = (size.height.toInt() - dot) / 2
        for (i in 0 until n) {
            val big = i == step
            val d = if (big) dot + unit * 2 else dot
            val dy = if (big) y - unit else y
            pxRect(x, dy, d, d, p.ink)
            pxRect(x + unit, dy + unit, d - 2 * unit, d - 2 * unit, if (big) p.yellow else if (i < step) p.gold else p.grayDark)
            x += d + gap
        }
    }
}

/** Counts up every [periodMillis] while animated: drives the pictures' little loops. */
@Composable
private fun ticker(periodMillis: Long): Int {
    var tick by remember { mutableIntStateOf(0) }
    val animated = PixelTheme.animated
    LaunchedEffect(animated) {
        while (animated) {
            delay(periodMillis)
            tick++
        }
    }
    return tick
}

// ---------------------------------------------------------------- the pictures

private val MEET = listOf(42L to "BOB", 1337L to "DOT", 2024L to "CHIP")
private val MOODS = listOf(Mood.Smug, Mood.Happy, Mood.Angry, Mood.Shocked, Mood.Neutral, Mood.Sad)

/** Three rivals pulling faces, one of them thinking it over. */
@Composable
private fun MeetArt() {
    val p = PixelTheme.palette
    val tick = ticker(1600)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "Three AI opponents, one of them thinking" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            MEET.forEachIndexed { i, (seed, name) ->
                val thinking = i == (tick + 1) % MEET.size
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.height(34.dp), contentAlignment = Alignment.BottomCenter) {
                        if (thinking) SpeechBubble("", thinking = true, tail = BubbleTail.BottomStart)
                    }
                    PixelAvatar(seed, if (thinking) Mood.Thinking else MOODS[(i * 2 + tick) % MOODS.size], size = 56.dp)
                    PixelText(name, style = PixelType.Small.copy(fontSize = 9.sp), color = p.yellow)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelTag("ON-DEVICE AI", p.teal, textColor = p.ink)
            PixelTag("OFFLINE", p.purple)
            PixelTag("NO ACCOUNT", p.cabinetLight)
        }
    }
}

private val TOUR_HOLE = Card.parseList("Ah Kh")
private val TOUR_BOARD = Card.parseList("Ks 7h 2c Td 3h")

/** Two hole cards and a board dealt in, and the hand they make, in words. */
@Composable
private fun CardsArt() {
    val p = PixelTheme.palette
    val made = remember { HandEvaluator.evaluate(TOUR_HOLE, TOUR_BOARD, GameVariant.TEXAS_HOLDEM).description }
    val animated = PixelTheme.animated
    var landed by remember { mutableStateOf(!animated) }
    LaunchedEffect(Unit) {
        if (!landed) { delay(1500); landed = true }
    }
    val sway = LocalCardSway.current
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "Your cards, ace and king of hearts, under a board of five shared cards: a pair of kings" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TOUR_BOARD.forEachIndexed { i, card ->
                AnimatedPixelCard(
                    card, faceUp = true, size = 36.dp, sway = sway, swayPhase = i * 0.9f,
                    highlighted = landed && card.rank == Rank.KING,
                    dealFrom = DpOffset(0.dp, (-50).dp), flipOnDeal = true, dealDelayMillis = 420 + i * 120,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TOUR_HOLE.forEachIndexed { i, card ->
                AnimatedPixelCard(
                    card, faceUp = true, size = 44.dp, sway = sway, swayPhase = i * 1.3f,
                    highlighted = landed && card.rank == Rank.KING,
                    dealFrom = DpOffset(0.dp, 90.dp), flipOnDeal = true, dealDelayMillis = i * 140,
                )
            }
            HGap(6.dp)
            Column(Modifier.alpha(if (landed) 1f else 0f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PixelText("YOU", style = PixelType.Small.copy(fontSize = 9.sp), color = p.yellow)
                PixelText(made.uppercase(), Modifier.width(120.dp), style = PixelType.LabelBold.copy(fontSize = 12.sp), color = p.cardCream)
            }
        }
    }
}

private class MoveButton(val label: String, val variant: PixelButtonVariant, val says: String)

private val MOVES = listOf(
    MoveButton("FOLD", PixelButtonVariant.Gray, "FOLD: give up the hand. It costs nothing more."),
    MoveButton("CHECK", PixelButtonVariant.Call, "CHECK: pass, when nobody has bet yet."),
    MoveButton("CALL", PixelButtonVariant.Call, "CALL: match the bet to stay in the hand."),
    MoveButton("RAISE", PixelButtonVariant.Bet, "RAISE: bet more. A slider picks how much."),
)

/** The four betting buttons; tap one (or watch them take turns) to hear what it does. */
@Composable
private fun MoveArt() {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val tick = ticker(1800)
    var picked by remember { mutableStateOf<Int?>(null) }
    val lit = picked ?: (tick % MOVES.size)
    val glow = pulse(500)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MOVES.forEachIndexed { i, m ->
                val on = i == lit
                PixelButton(
                    m.label,
                    { picked = i },
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .semantics { stateDescription = if (on) "Explained below" else "" }
                        .drawBehind {
                            if (on) {
                                val u = unit
                                val c = androidx.compose.ui.graphics.lerp(p.goldDark, p.yellow, glow)
                                pxRect(-2 * u, -2 * u, size.width.toInt() + 3 * u, u, c)
                                pxRect(-2 * u, size.height.toInt() + u, size.width.toInt() + 3 * u, u, c)
                                pxRect(-2 * u, -2 * u, u, size.height.toInt() + 4 * u, c)
                                pxRect(size.width.toInt() + u, -2 * u, u, size.height.toInt() + 4 * u, c)
                            }
                        }
                        .alpha(if (on) 1f else 0.6f),
                    variant = m.variant,
                    textStyle = PixelType.Small.copy(fontSize = 9.sp),
                    sound = if (i >= 2) Sfx.Chip else Sfx.Blip,
                )
            }
        }
        BodyText(MOVES[lit].says, Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = p.cardCream, small = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelTag("HOLD", p.gold, textColor = p.ink, style = PixelType.Small.copy(fontSize = 9.sp))
            BodyText("Draw games: tap cards to HOLD the keepers, then DRAW the rest.", Modifier.weight(1f), color = p.cardCream, small = true)
        }
    }
}

/** QUICK GAME's six games beside POKER TOUR's ladder, a chip climbing it. */
@Composable
private fun ModesArt() {
    val p = PixelTheme.palette
    val tick = ticker(700)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Quick game: one table, any of six games. Poker tour: ten tables of rising stakes, chips carry over, and a daily tour."
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelPanel(Modifier.weight(1f).height(ART_HEIGHT - 8.dp), style = PanelStyle.Dark, paddingUnits = 2) {
            PixelTag("QUICK GAME", p.suitRed, textColor = p.yellow)
            VGap(6.dp)
            GameVariant.entries.forEachIndexed { i, v ->
                PixelText(
                    v.displayName.uppercase(),
                    style = PixelType.Label.copy(fontSize = 10.sp),
                    color = if (i == tick % GameVariant.entries.size) p.yellow else p.cardCream,
                    shadowUnits = 0,
                    maxLines = 1,
                )
            }
        }
        PixelPanel(Modifier.weight(1f).height(ART_HEIGHT - 8.dp), style = PanelStyle.Dark, paddingUnits = 2) {
            PixelTag("POKER TOUR", p.hotPink)
            VGap(6.dp)
            TourLadder(stop = if (PixelTheme.animated) tick % 12 else 6, Modifier.fillMaxWidth().weight(1f))
            VGap(4.dp)
            PixelText("CHIPS CARRY OVER", style = PixelType.Label.copy(fontSize = 9.sp), color = p.cardCream, shadowUnits = 0, maxLines = 1)
            PixelText("+ A DAILY TOUR", style = PixelType.Label.copy(fontSize = 9.sp), color = p.yellow, shadowUnits = 0, maxLines = 1)
        }
    }
}

/** Ten rising bars, the cleared ones lit and a chip on the current one. */
@Composable
private fun TourLadder(stop: Int, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Canvas(modifier) {
        val n = 10
        val w = size.width.toInt()
        val h = size.height.toInt()
        val gap = unit
        val bar = ((w - gap * (n - 1)) / n).coerceAtLeast(unit)
        val chip = unit * 4
        for (i in 0 until n) {
            val bh = ((h - chip - unit) * (i + 2) / (n + 1)).coerceAtLeast(unit * 2)
            val x = i * (bar + gap)
            val y = h - bh
            val color = when {
                i < stop -> p.gold
                i == stop -> p.yellow
                else -> p.inkSoft
            }
            pxRect(x, y, bar, bh, p.ink)
            pxRect(x + unit, y + unit, (bar - 2 * unit).coerceAtLeast(unit), bh - unit, color)
            if (i == stop.coerceAtMost(n - 1)) {
                val cx = x + bar / 2 - chip / 2
                pxRect(cx, y - chip - unit, chip, chip, p.ink)
                pxRect(cx + unit, y - chip, chip - 2 * unit, chip - 2 * unit, p.suitRed)
            }
        }
    }
}

/** House Bot holding the seats while a model downloads; PAUSE and HOW TO. */
@Composable
private fun BrainsArt() {
    val p = PixelTheme.palette
    val progress = if (PixelTheme.animated) {
        rememberInfiniteTransition(label = "download").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "d").value
    } else remember { mutableFloatStateOf(0.62f) }.floatValue
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "House Bot plays while a model downloads. Pause or Back stops a hand. How to has every rule."
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelAvatar(HOUSE_BOT_SEED, if (progress > 0.85f) Mood.Shocked else Mood.Smug, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelTag("HOUSE BOT", p.grayDark)
                    PixelText("PLAYS FOR NOW", style = PixelType.Label.copy(fontSize = 10.sp), color = p.cardCream, shadowUnits = 0, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelTag("MODELS", p.cabinetLight)
                    PixelMeter(progress, p.green, Modifier.weight(1f), height = 12.dp)
                }
                PixelText("A BRAIN IS ON ITS WAY", style = PixelType.Label.copy(fontSize = 10.sp), color = p.teal, shadowUnits = 0, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniKey { PixelGlyphBox(PixelGlyph.Pause, p.ink) }
            PixelText("PAUSE OR BACK: SPEED, QUIT", style = PixelType.Label.copy(fontSize = 10.sp), color = p.cardCream, shadowUnits = 0, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniKey { PixelText("?", style = PixelType.Small, color = p.ink, shadowUnits = 0) }
            PixelText("HOW TO: EVERY RULE + FAQ", style = PixelType.Label.copy(fontSize = 10.sp), color = p.cardCream, shadowUnits = 0, maxLines = 1)
        }
    }
}

/** A little gray key cap for a glyph: the look of the real button, without its tap. */
@Composable
private fun MiniKey(size: Dp = 26.dp, content: @Composable () -> Unit) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Box(
        Modifier.size(size).drawBehind {
            val w = this.size.width.toInt()
            pxBox(0, 0, w, w, unit, p.ink, p.gray)
            pxRect(unit, unit, w - 2 * unit, unit, Color(0xFFC9C6DD))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}
