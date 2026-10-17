package com.bradflaugher.pokersi.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bradflaugher.pokersi.game.Coach
import com.bradflaugher.pokersi.game.CoachMarks
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.notchedRect
import com.bradflaugher.pokersi.ui.pixel.pxRect
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Which one-time hint is up, if any; [dismiss] takes it down. */
@Stable
class CoachState {
    var showing: Coach? by mutableStateOf(null)
        internal set
    internal var shownAt by mutableLongStateOf(0L)

    fun dismiss() {
        showing = null
    }
}

/**
 * Runs the one-time table hints: when nothing is up and [wanted] (from
 * [CoachMarks]) has one not [seen] yet, it shows after a beat and is
 * reported through [onShown] (seen from then on). It goes after
 * [CoachMarks.HOLD_MS], when its situation passes, or on [CoachState.dismiss].
 */
@Composable
fun rememberCoach(wanted: List<Coach>, seen: Set<Coach>, enabled: Boolean, onShown: (Coach) -> Unit): CoachState {
    val state = remember { CoachState() }
    val shown by rememberUpdatedState(onShown)
    val next = if (enabled) CoachMarks.next(wanted, seen) else null
    val showing = state.showing
    LaunchedEffect(showing, wanted, next, enabled) {
        if (showing != null) {
            if (!enabled || !CoachMarks.keeps(showing, wanted)) {
                state.showing = null
                return@LaunchedEffect
            }
            delay((CoachMarks.HOLD_MS - (SystemClock.uptimeMillis() - state.shownAt)).coerceAtLeast(0L))
            state.showing = null
        } else if (next != null) {
            delay(CoachMarks.DELAY_MS)
            state.shownAt = SystemClock.uptimeMillis()
            state.showing = next
            shown(next)
        }
    }
    return state
}

/**
 * Calls [onTouch] when a touch anywhere in this element ends, without taking
 * it: the tap still lands on whatever is under the finger (and has already
 * landed when a hint goes, so nothing moves under a press).
 */
fun Modifier.onAnyTouch(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        onTouch()
    }
}

/** Where a hint's arrow points: up, down or left at the control it is about, or nowhere. */
enum class CoachArrow { Up, Down, Left, None }

/**
 * A one-time hint: a yellow note in pixel chrome, a TIP tag, and an arrow at
 * [arrowFraction] along its edge pointing at the control it is about. The
 * arrow bobs; TalkBack reads the hint as it appears. It has no click of its
 * own, so a tap on it lands on what is under it.
 */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier, arrow: CoachArrow = CoachArrow.Down, arrowFraction: Float = 0.5f) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val density = LocalDensity.current
    val bob = if (PixelTheme.animated && arrow != CoachArrow.None) {
        rememberInfiniteTransition(label = "coach").animateFloat(0f, 6.283f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "bob").value
    } else 0f
    val tip = if (arrow == CoachArrow.None) 0 else unit * 5
    val spoken = "Tip. $text"
    fun Int.dp() = with(density) { toDp() }
    Box(
        modifier
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .graphicsLayer {
                // Nudges towards what it points at and back.
                val nudge = sin(bob) * unit
                when (arrow) {
                    CoachArrow.Up -> translationY = -nudge
                    CoachArrow.Down -> translationY = nudge
                    CoachArrow.Left -> translationX = -nudge
                    CoachArrow.None -> Unit
                }
            }
            .drawBehind {
                val left = if (arrow == CoachArrow.Left) tip else 0
                val top = if (arrow == CoachArrow.Up) tip else 0
                val w = size.width.toInt() - left
                val h = size.height.toInt() - if (arrow == CoachArrow.Up || arrow == CoachArrow.Down) tip else 0
                notchedRect(left + unit, top + unit, w - unit, h - unit, unit, 2, p.ink.copy(alpha = 0.45f))
                notchedRect(left, top, w - unit, h - unit, unit, 2, p.ink)
                notchedRect(left + unit, top + unit, w - 3 * unit, h - 3 * unit, unit, 1, p.yellow)
                pxRect(left + unit * 2, top + h - 3 * unit, w - 5 * unit, unit, p.gold)
                // A five-step staircase arrow in ink, filled yellow, opening into the note.
                for (i in 0 until 5) {
                    val half = (5 - i) * unit
                    when (arrow) {
                        CoachArrow.Up, CoachArrow.Down -> {
                            val cx = left + ((w - unit) * arrowFraction).toInt().coerceIn(unit * 8, w - unit * 9)
                            val y = if (arrow == CoachArrow.Down) top + h - 2 * unit + i * unit else top + unit - i * unit
                            pxRect(cx - half - unit, y, 2 * half + 2 * unit, unit, p.ink)
                            if (i < 4) pxRect(cx - half, y, 2 * half, unit, p.yellow)
                        }
                        CoachArrow.Left -> {
                            val cy = top + ((h - unit) * arrowFraction).toInt().coerceIn(unit * 7, h - unit * 8)
                            val x = left + unit - i * unit
                            pxRect(x, cy - half - unit, unit, 2 * half + 2 * unit, p.ink)
                            if (i < 4) pxRect(x, cy - half, unit, 2 * half, p.yellow)
                        }
                        CoachArrow.None -> Unit
                    }
                }
            }
            .padding(
                start = (unit * 4 + if (arrow == CoachArrow.Left) tip else 0).dp(),
                end = (unit * 5).dp(),
                top = (unit * 3 + if (arrow == CoachArrow.Up) tip else 0).dp(),
                bottom = (unit * 4 + if (arrow == CoachArrow.Down) tip else 0).dp(),
            ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            PixelTag("TIP", p.hotPink, Modifier.padding(top = 2.dp), textColor = p.white, style = PixelType.Small.copy(fontSize = 8.sp))
            PixelText(text, style = PixelType.BodySmall, color = p.ink, shadowUnits = 0)
        }
    }
}

/** Fades [coach] in and out (keeping the last one while it fades), drawing it with [content]. */
@Composable
fun CoachFade(coach: Coach?, content: @Composable (Coach, Float) -> Unit) {
    var last by remember { mutableStateOf(coach) }
    if (coach != null) last = coach
    val alpha by animateFloatAsState(
        targetValue = if (coach != null) 1f else 0f,
        animationSpec = if (PixelTheme.animated) tween(if (coach != null) 220 else 380) else androidx.compose.animation.core.snap(),
        label = "coach",
        finishedListener = { if (it == 0f) last = null },
    )
    val c = last ?: return
    if (alpha > 0f) content(c, alpha)
}

/**
 * The hint layer of the table: [coach]'s note, placed from where things
 * landed ([spots], as tracked by TableScreen) so it never covers the action
 * buttons or the cards you are playing: above your cards for decisions
 * (pointing down at them and the buttons), beside the pause button over the
 * header for PAUSE, and in the gap under the opponents at the end of a hand.
 */
@Composable
internal fun CoachLayer(coach: Coach?, spots: Map<Int, Rect>, wide: Boolean) {
    CoachFade(coach) { c, alpha ->
        val arrow = when (c) {
            Coach.Pause -> CoachArrow.Left
            Coach.HandOver -> CoachArrow.None
            else -> CoachArrow.Down
        }
        val pause = spots[SPOT_PAUSE]
        val human = spots[SPOT_HUMAN]
        Layout(
            content = { CoachBubble(c.text, Modifier.graphicsLayer { this.alpha = alpha }, arrow = arrow) },
            modifier = Modifier.fillMaxSize().zIndex(6f),
        ) { measurables, constraints ->
            val margin = 8.dp.roundToPx()
            // Decisions in a wide layout keep to your column; everything else spans the screen.
            val (left, right) = when {
                c == Coach.Pause && pause != null -> pause.right.toInt() + 2.dp.roundToPx() to constraints.maxWidth - margin
                human != null && wide -> human.left.toInt() to human.right.toInt()
                else -> margin to constraints.maxWidth - margin
            }
            val width = minOf(right - left, 360.dp.roundToPx())
            val bubble = measurables[0].measure(Constraints(maxWidth = width))
            val x = if (c == Coach.Pause) left else left + (right - left - bubble.width) / 2
            val y = when {
                c == Coach.Pause && pause != null -> pause.center.y.toInt() - bubble.height / 2
                c == Coach.HandOver && !wide -> spots[SPOT_OPPONENTS]?.bottom?.toInt()?.plus(margin) ?: margin
                human != null -> human.top.toInt() - bubble.height
                else -> constraints.maxHeight - bubble.height - margin
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                bubble.place(x, y.coerceIn(0, (constraints.maxHeight - bubble.height).coerceAtLeast(0)))
            }
        }
    }
}
