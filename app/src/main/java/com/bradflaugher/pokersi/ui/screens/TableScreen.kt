package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import com.bradflaugher.pokersi.ui.pixel.cardDescription
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.ui.pixel.FeltColors
import com.bradflaugher.pokersi.ui.pixel.PayRow
import com.bradflaugher.pokersi.ui.pixel.PayTable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.pointerInput
import com.bradflaugher.pokersi.ui.pixel.pxRect
import com.bradflaugher.pokersi.ui.pixel.cardScaleFor
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.game.HumanTurn
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.hasBoard
import com.bradflaugher.pokersi.engine.isDraw
import com.bradflaugher.pokersi.game.CardUi
import com.bradflaugher.pokersi.game.RunUiState
import com.bradflaugher.pokersi.game.SeatUi
import com.bradflaugher.pokersi.game.TablePhase
import com.bradflaugher.pokersi.game.TableUiState
import com.bradflaugher.pokersi.game.formatChips
import com.bradflaugher.pokersi.ui.pixel.AnimatedPixelCard
import com.bradflaugher.pokersi.ui.pixel.BigBanner
import com.bradflaugher.pokersi.ui.pixel.BubbleTail
import com.bradflaugher.pokersi.ui.pixel.ConfettiBurst
import com.bradflaugher.pokersi.ui.pixel.LedColor
import com.bradflaugher.pokersi.ui.pixel.LedCounter
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelGlyph
import com.bradflaugher.pokersi.ui.pixel.PixelCardDefaults
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx
import com.bradflaugher.pokersi.ui.pixel.SpeechBubble
import com.bradflaugher.pokersi.ui.pixel.shake

/** What the table screen can ask the game to do. Defaults do nothing (screenshots, previews). */
class TableCallbacks(
    val onAct: (Decision) -> Unit = {},
    val onToggle: (Int) -> Unit = {},
    val onConfirmDiscard: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPlayAgain: () -> Unit = {},
    val onMenu: () -> Unit = {},
    val onPause: () -> Unit = {},
    /** Opens the debug console; null hides the "~" button (the setting is off). */
    val onConsole: (() -> Unit)? = null,
)

/** Sizes for one table layout, from the window size. */
data class TableMetrics(
    val wide: Boolean,
    val big: Boolean,
    val seatsPerRow: Int,
    val avatar: Dp,
    val seatCard: Dp,
    val boardCard: Dp,
    val handCard: Dp,
)

fun tableMetrics(width: Dp, height: Dp, opponents: Int, holeCards: Int, hasBoard: Boolean = true): TableMetrics {
    val wide = width > height * 1.15f
    val big = width >= 600.dp && height >= 600.dp
    val area = if (wide) width * 0.58f else width
    val perRow = when {
        opponents <= 3 -> opponents.coerceAtLeast(1)
        area >= 700.dp -> opponents
        opponents == 4 -> 2
        else -> 3
    }
    val cards = holeCards.coerceAtLeast(2)
    val handW = if (wide) width * 0.42f else width
    val boardCard = min((area - 36.dp) / 5 - 6.dp, height * (if (wide) 0.2f else 0.14f) / 1.4f)
    val rows = (opponents + perRow - 1) / perRow.coerceAtLeast(1)
    val share = when {
        wide -> 0.3f
        rows <= 1 || !hasBoard -> 0.2f
        else -> 0.17f
    }
    val handCard = min((handW - 28.dp - 3.dp * (cards - 1)) / cards, height * share / 1.4f)
    return TableMetrics(
        wide = wide,
        big = big,
        seatsPerRow = perRow,
        avatar = if (big) 60.dp else 40.dp,
        seatCard = if (big) 50.dp else 35.dp,
        boardCard = boardCard,
        handCard = handCard,
    )
}

/**
 * The poker table: opponents across the top, the board and pot in the
 * middle, your cards and the action buttons at the bottom; banners,
 * confetti and screen shake at showdown. [run] adds the Run mode HUD.
 */
@Composable
fun TableScreen(
    state: TableUiState,
    callbacks: TableCallbacks,
    modifier: Modifier = Modifier,
    run: RunUiState? = null,
) {
    ArcadeScreen(modifier, felt = FeltColors.Table) {
        BoxWithConstraints(Modifier.fillMaxSize().shake(state.celebrate.takeIf { it > 0 }, strength = 10.dp)) {
            val m = tableMetrics(
                maxWidth, maxHeight, state.opponents.size,
                state.human?.cards?.size ?: state.variant.holeCards, state.variant.hasBoard,
            )
            // Where things landed (relative to this box), for the bubbles and the banner.
            val spots = remember { mutableStateMapOf<Int, Rect>() }
            val screenWidth = maxWidth
            val origin = remember { mutableStateOf(Offset.Zero) }
            val roomForOdds = !state.variant.hasBoard && (m.wide || state.opponents.size <= m.seatsPerRow || maxHeight > 860.dp)
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin.value = it.positionInRoot() }) {
                val track = { key: Int -> Modifier.onGloballyPositioned { c -> spots[key] = Rect(c.positionInRoot() - origin.value, c.size.toSize()) } }
                if (m.wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1.4f).fillMaxHeight()) {
                            TableHeader(state, callbacks)
                            if (run != null) RunHud(run, Modifier.padding(bottom = 6.dp))
                            OpponentsArea(state, m, track)
                            Spacer(Modifier.weight(1f))
                            BoardArea(state, m, roomForOdds, track(SPOT_BOARD))
                            Spacer(Modifier.weight(1f))
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = 6.dp), verticalArrangement = Arrangement.Bottom) {
                            LogPanel(state, Modifier.weight(1f).padding(top = 8.dp))
                            HumanArea(state, m, callbacks, track(SPOT_HUMAN))
                            ActionArea(state, callbacks, run)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 6.dp)) {
                        TableHeader(state, callbacks)
                        if (run != null) RunHud(run, Modifier.padding(bottom = 6.dp))
                        OpponentsArea(state, m, track)
                        Spacer(Modifier.weight(1f))
                        BoardArea(state, m, roomForOdds, track(SPOT_BOARD))
                        Spacer(Modifier.weight(1.3f))
                        HumanArea(state, m, callbacks, track(SPOT_HUMAN))
                        ActionArea(state, callbacks, run)
                    }
                }
                TableAnnouncer(state)
                BubbleLayer(state, spots, compact = !m.big, wide = m.wide)
                BannerLayer(state, screenWidth, spots, m.wide)
                ConfettiBurst(state.celebrate.takeIf { it > 0 && state.banner?.humanWon == true }, Modifier.fillMaxSize().zIndex(5f))
            }
        }
    }
}

private const val SPOT_BOARD = -1
private const val SPOT_HUMAN = -2

// ---------------------------------------------------------------- header

@Composable
private fun TableHeader(state: TableUiState, callbacks: TableCallbacks) {
    val p = PixelTheme.palette
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton("PAUSE", callbacks.onPause, Modifier.size(48.dp, 48.dp).semantics { contentDescription = "Pause" }, variant = PixelButtonVariant.Gray, icon = PixelGlyph.Pause)
        callbacks.onConsole?.let { ConsoleButton(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PixelText(state.variant.displayName.uppercase(), style = PixelType.Small, color = p.yellow, maxLines = 1)
            val blinds = state.blinds
            val stakes = if (blinds.smallBlind > 0) "${formatChips(blinds.smallBlind)}/${formatChips(blinds.bigBlind)}" else "BET ${formatChips(blinds.bigBlind)}"
            val hand = if (state.handNumber > 0) "HAND ${state.handNumber}  " else ""
            PixelText("$hand$stakes  ${state.street.uppercase()}", style = PixelType.Label.copy(fontSize = 11.sp), color = p.white, shadowUnits = 1, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PixelText("AI", style = PixelType.Label.copy(fontSize = 9.sp), color = p.gray, shadowUnits = 0)
            PixelText(state.brainLabel.uppercase(), Modifier.widthIn(max = 130.dp), style = PixelType.Label.copy(fontSize = 10.sp), color = p.teal, maxLines = 2, textAlign = TextAlign.End)
        }
    }
}

// ---------------------------------------------------------------- opponents

@Composable
private fun OpponentsArea(state: TableUiState, m: TableMetrics, track: (Int) -> Modifier) {
    val opponents = state.opponents
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opponents.chunked(m.seatsPerRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                row.forEach { seat ->
                    OpponentSeat(
                        seat, m, state,
                        Modifier
                            .weight(1f, fill = true)
                            .widthIn(max = 220.dp)
                            .then(track(seat.seat)),
                    )
                }
                // Keep a lone last-row seat the same width as the others.
                repeat(m.seatsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun moodFor(seat: SeatUi): Mood = when {
    seat.isWinner -> Mood.Happy
    seat.busted || seat.folded -> Mood.Sad
    seat.thinking -> Mood.Thinking
    seat.allIn -> Mood.Shocked
    (seat.net ?: 0) < 0 -> Mood.Angry
    seat.lastAction?.startsWith("Raise") == true || seat.lastAction?.startsWith("Bet") == true -> Mood.Smug
    else -> Mood.Neutral
}

@Composable
private fun OpponentSeat(seat: SeatUi, m: TableMetrics, state: TableUiState, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val glow = if (seat.isToAct || seat.thinking) pulse() else 1f
    val border = when {
        seat.isWinner -> p.gold
        seat.isToAct || seat.thinking -> lerp(p.goldDark, p.yellow, glow)
        else -> p.inkSoft
    }
    val dim = seat.folded || seat.busted
    val spoken = seatDescription(seat)
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .alpha(if (dim) 0.55f else 1f)
            .drawBehind {
                val w = size.width.toInt(); val h = size.height.toInt()
                if (seat.isWinner) pxBox(-unit * 2, -unit * 2, w + unit * 4, h + unit * 4, unit, p.yellow.copy(alpha = 0.35f), p.yellow.copy(alpha = 0.35f))
                pxBox(0, 0, w, h, unit, p.ink, border)
                pxBox(unit, unit, w - 2 * unit, h - 2 * unit, unit, p.ink, Color(0xD00A1030))
            }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PixelText(
            seat.name.uppercase(),
            style = PixelType.LabelBold.copy(fontSize = (if (m.big) 13f else 10f).let { if (seat.name.length > 12) it * 0.84f else it }.sp),
            color = p.yellow,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box {
                PixelAvatar(seat.profile?.avatarSeed ?: seat.seat.toLong(), moodFor(seat), size = m.avatar)
                if (seat.isDealer) DealerPuck(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp))
            }
            SeatCards(seat.cards, m.seatCard, state.handNumber, Modifier.weight(1f), dim = seat.folded)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PixelText(formatChips(seat.chips), style = PixelType.Small.copy(fontSize = if (m.big) 11.sp else 8.sp), color = p.white, maxLines = 1)
            val net = seat.net
            if (net != null && net != 0) {
                PixelText(if (net > 0) "+${formatChips(net)}" else "-${formatChips(-net)}", style = PixelType.Small.copy(fontSize = 8.sp), color = if (net > 0) p.green else p.red, maxLines = 1)
            }
        }
        when {
            seat.busted -> PixelTag("BUSTED", p.redDark)
            seat.shownHand != null -> PixelText(seat.shownHand.uppercase(), style = PixelType.Label.copy(fontSize = 9.sp), color = if (seat.isWinner) p.yellow else p.cardShade, maxLines = 2)
            seat.lastAction != null -> ActionTag(seat)
            else -> Box(Modifier.height(16.dp))
        }
    }
}

/** What TalkBack says for an opponent's seat: who, chips, what they did, what they show and say. */
internal fun seatDescription(seat: SeatUi): String = buildList {
    add(seat.profile?.spoken ?: seat.name)
    if (seat.isDealer) add("dealer")
    add(if (seat.busted) "busted" else "${formatChips(seat.chips)} chips")
    if (seat.bet > 0) add("bet ${formatChips(seat.bet)}")
    seat.net?.takeIf { it != 0 }?.let { add(if (it > 0) "won ${formatChips(it)}" else "lost ${formatChips(-it)}") }
    when {
        seat.folded -> add("folded")
        seat.allIn -> add("all in")
        seat.thinking -> add("thinking")
        seat.isToAct -> add("to act")
    }
    seat.lastAction?.takeIf { !seat.folded && !seat.allIn }?.let { add("last action ${it.lowercase()}") }
    val shown = seat.cards.mapNotNull { it.card }
    if (shown.isNotEmpty()) add("shows " + shown.joinToString(", ") { cardDescription(it, faceUp = true) })
    seat.shownHand?.let { add(it) }
    if (seat.isWinner) add("winner")
    seat.chatter?.takeIf { it.isNotBlank() && !seat.chatterStreaming }?.let { add("says: $it") }
}.joinToString(", ")

/**
 * Reads the table's news aloud for TalkBack: the latest history line (who bet,
 * who folded, who won) and the showdown banner, as a polite live region.
 */
@Composable
private fun TableAnnouncer(state: TableUiState) {
    val news = listOfNotNull(state.log.lastOrNull(), state.banner?.let { b -> listOfNotNull(b.text, b.subtitle).joinToString(". ") })
        .joinToString(". ")
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = news
            },
    )
}

@Composable
private fun ActionTag(seat: SeatUi) {
    val p = PixelTheme.palette
    val a = seat.lastAction ?: return
    val (label, color, text) = when {
        a == "Fold" -> Triple("FOLD", p.grayDark, p.white)
        a == "Check" -> Triple("CHECK", p.cabinetLight, p.white)
        a == "Call" -> Triple(if (seat.bet > 0) "CALL ${formatChips(seat.bet)}" else "CALL", p.greenDark, p.white)
        a == "All-in" -> Triple("ALL IN!", p.red, p.yellow)
        a.startsWith("Raise") -> Triple("RAISE ${a.removePrefix("Raise ").toIntOrNull()?.let(::formatChips) ?: ""}", p.gold, p.ink)
        a.startsWith("Bet") -> Triple("BET ${a.removePrefix("Bet ").toIntOrNull()?.let(::formatChips) ?: ""}", p.gold, p.ink)
        a == "Small blind" -> Triple("SB ${formatChips(seat.bet)}", p.purpleDark, p.white)
        a == "Big blind" -> Triple("BB ${formatChips(seat.bet)}", p.purple, p.white)
        a == "Bring-in" -> Triple("BRING-IN", p.purple, p.white)
        else -> Triple(a.uppercase(), p.cabinet, p.white)
    }
    PixelTag(label.trim(), color, textColor = text)
}

@Composable
private fun DealerPuck(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Box(
        modifier.size(18.dp).drawBehind {
            val w = size.width.toInt()
            pxBox(0, 0, w, w, unit, p.ink, p.white)
        },
        contentAlignment = Alignment.Center,
    ) {
        PixelText("D", style = PixelType.Small.copy(fontSize = 8.sp), color = p.ink, shadowUnits = 0)
    }
}

/** An opponent's cards: small and overlapped to fit. Face-down cards show the back. */
@Composable
private fun SeatCards(cards: List<CardUi>, cardSize: Dp, handNumber: Int, modifier: Modifier = Modifier, dim: Boolean) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(PixelCardDefaults.heightFor(cardSize) + 6.dp)) {
        if (cards.isEmpty()) return@BoxWithConstraints
        val scale = with(density) { cardScaleFor(cardSize.toPx()) }
        val cardW = with(density) { (scale * 45).toDp() }
        val n = cards.size
        val step = if (n <= 1) 0.dp else min(cardW + 2.dp, (maxWidth - cardW) / (n - 1))
        cards.forEachIndexed { i, c ->
            key(handNumber, i) {
                Box(Modifier.offset(x = step * i).zIndex(i.toFloat())) {
                    AnimatedPixelCard(
                        card = c.card,
                        faceUp = c.card != null,
                        size = cardSize,
                        sway = false,
                        dimmed = dim,
                        highlighted = c.highlighted,
                        dealFrom = DpOffset(0.dp, 120.dp),
                        dealDelayMillis = 60 * i,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bubbles

/**
 * Speech bubbles hang under their speaker's seat, tail pointing at the speaker.
 * They are laid out together so two opponents talking at once never overlap:
 * placed top to bottom, a bubble that would land on an earlier one drops just
 * below it ([stackBubbles]).
 */
@Composable
private fun BubbleLayer(state: TableUiState, spots: Map<Int, Rect>, compact: Boolean, wide: Boolean) {
    val seatsRight = spots.filterKeys { it > 0 }.values.maxOfOrNull { it.right } ?: 0f
    val speakers = state.opponents.mapNotNull { seat ->
        val rect = spots[seat.seat] ?: return@mapNotNull null
        val text = seat.chatter
        val thinking = seat.thinking && text.isNullOrEmpty()
        if (text.isNullOrEmpty() && !thinking) return@mapNotNull null
        Triple(seat, rect, thinking)
    }
    if (speakers.isEmpty()) return
    Layout(
        content = {
            speakers.forEach { (seat, rect, thinking) ->
                val onRight = rect.center.x > seatsRight / 2f + 1f
                SpeechBubble(
                    text = seat.chatter.orEmpty(),
                    streaming = seat.chatterStreaming,
                    thinking = thinking,
                    tail = if (onRight) BubbleTail.TopEnd else BubbleTail.TopStart,
                    textStyle = if (compact) PixelType.BodySmall else PixelType.Body,
                )
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(3f),
    ) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        // Wide layouts keep bubbles over the opponents' column, clear of your cards.
        val right = if (wide) (spots[SPOT_BOARD]?.right?.toInt() ?: constraints.maxWidth) + margin else constraints.maxWidth
        val maxW = minOf(right - 2 * margin, (if (compact) 230 else 300).dp.roundToPx())
        val nudge = 6.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxW)) }
        val wanted = speakers.mapIndexed { i, (_, rect, _) ->
            val bubble = placeables[i]
            val onRight = rect.center.x > seatsRight / 2f + 1f
            val x = if (onRight) rect.right.toInt() - bubble.width - nudge else rect.left.toInt() + nudge
            IntRect(
                IntOffset(x.coerceIn(margin, (right - bubble.width - margin).coerceAtLeast(margin)), rect.bottom.toInt() - 12.dp.roundToPx()),
                IntSize(bubble.width, bubble.height),
            )
        }
        val placed = stackBubbles(wanted, gap = 4.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, bubble -> bubble.place(placed[i].topLeft) }
        }
    }
}

/**
 * Where each bubble goes so none overlap: each keeps its x and its wanted y
 * unless an already-placed bubble (taken top to bottom, then left to right)
 * is in the way, in which case it drops to just below that one, [gap] apart.
 * Returns the rectangles in the order given.
 */
internal fun stackBubbles(wanted: List<IntRect>, gap: Int): List<IntRect> {
    val order = wanted.indices.sortedWith(compareBy({ wanted[it].top }, { wanted[it].left }))
    val placed = arrayOfNulls<IntRect>(wanted.size)
    val done = mutableListOf<IntRect>()
    for (i in order) {
        var r = wanted[i]
        while (true) {
            val hit = done.filter { it.left < r.right && r.left < it.right && it.top < r.bottom + gap && r.top < it.bottom + gap }
            if (hit.isEmpty()) break
            r = r.translate(0, hit.maxOf { it.bottom } + gap - r.top)
        }
        placed[i] = r
        done += r
    }
    return placed.map { it!! }
}

// ---------------------------------------------------------------- board

@Composable
private fun BoardArea(state: TableUiState, m: TableMetrics, showOdds: Boolean, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showOdds) OddsTable(state.madeCategory, Modifier.widthIn(max = 360.dp))
        if (state.variant.hasBoard || state.board.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until 5) {
                    val card = state.board.getOrNull(i)
                    if (card != null) {
                        key(state.handNumber, i) {
                            AnimatedPixelCard(
                                card, faceUp = true, size = m.boardCard,
                                sway = LocalCardSway.current, swayPhase = i * 0.9f,
                                highlighted = card in state.winningCards,
                                dealFrom = DpOffset(0.dp, (-60).dp), flipOnDeal = true, dealDelayMillis = (i % 3) * 110,
                            )
                        }
                    } else {
                        EmptyCardSlot(m.boardCard)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedCounter(state.pot, label = "POT", color = LedColor.Amber, minDigits = 5, segmentUnits = if (m.big) 2 else 1, sound = Sfx.Chip)
            if (!state.variant.hasBoard) {
                PixelTag(state.street.uppercase().ifEmpty { "DEAL" }, p.purple, style = PixelType.Small)
            }
        }
    }
}

/** Video poker's pay table, as five-card odds: your current hand flashes. */
@Composable
private fun OddsTable(made: HandCategory?, modifier: Modifier = Modifier) {
    val rows = listOf(
        HandCategory.ROYAL_FLUSH to "1 IN 649,740",
        HandCategory.STRAIGHT_FLUSH to "1 IN 72,193",
        HandCategory.FOUR_OF_A_KIND to "1 IN 4,165",
        HandCategory.FULL_HOUSE to "1 IN 694",
        HandCategory.FLUSH to "1 IN 509",
        HandCategory.STRAIGHT to "1 IN 255",
        HandCategory.THREE_OF_A_KIND to "1 IN 47",
        HandCategory.TWO_PAIR to "1 IN 21",
        HandCategory.PAIR to "1 IN 2.4",
    )
    PayTable(
        rows.map { PayRow(it.first.label.uppercase(), it.second) },
        modifier,
        highlight = rows.indexOfFirst { it.first == made }.takeIf { it >= 0 },
    )
}

@Composable
private fun EmptyCardSlot(size: Dp) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val density = LocalDensity.current
    val scale = with(density) { cardScaleFor(size.toPx()) }
    val w = with(density) { (scale * 45).toDp() }
    val h = with(density) { (scale * 63).toDp() }
    Canvas(Modifier.size(w, h)) {
        val iw = this.size.width.toInt(); val ih = this.size.height.toInt()
        pxBox(0, 0, iw, ih, unit, p.feltLight.copy(alpha = 0.55f), p.feltDark.copy(alpha = 0.55f))
    }
}

// ---------------------------------------------------------------- the human

@Composable
private fun HumanArea(state: TableUiState, m: TableMetrics, callbacks: TableCallbacks, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val me = state.human ?: return
    val turn = state.humanTurn
    val discarding = turn?.isDiscard == true
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelText("YOU", style = PixelType.Small, color = p.yellow)
                    if (me.isDealer) DealerPuck()
                    if (me.lastAction != null && !me.folded && state.phase == TablePhase.Playing) ActionTag(me)
                    if (me.folded) PixelTag("FOLDED", p.grayDark)
                }
                val made = state.madeHand
                if (made != null && !me.folded) {
                    PixelText(made.uppercase(), style = PixelType.LabelBold.copy(fontSize = 12.sp), color = if (me.isWinner) p.yellow else p.cardCream, maxLines = 1)
                }
            }
            LedCounter(me.chips, label = "CREDIT", color = LedColor.Red, minDigits = 5, segmentUnits = if (m.big) 2 else 1, contentDescription = "Your chips ${formatChips(me.chips)}")
        }
        Box(Modifier.fillMaxWidth().padding(top = if (state.variant.isDraw) 18.dp else 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                me.cards.forEachIndexed { i, c ->
                    val held = state.variant.isDraw && discarding && i in state.selected
                    val tossing = !state.variant.isDraw && discarding && i in state.selected
                    key(state.handNumber, c.card?.toString() ?: "x$i") {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedPixelCard(
                                card = c.card,
                                faceUp = c.card != null,
                                size = m.handCard,
                                held = held,
                                highlighted = c.highlighted,
                                dimmed = me.folded || tossing,
                                sway = LocalCardSway.current,
                                swayPhase = i * 1.3f,
                                dealFrom = DpOffset(0.dp, (-320).dp),
                                dealDelayMillis = 120 * i,
                                flipOnDeal = true,
                                onClick = if (discarding) ({ callbacks.onToggle(i) }) else null,
                            )
                            if (tossing) PixelTag("TOSS", p.red, textColor = p.yellow, style = PixelType.Small)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- actions

@Composable
private fun ActionArea(state: TableUiState, callbacks: TableCallbacks, run: RunUiState?) {
    val turn = state.humanTurn
    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp), contentAlignment = Alignment.BottomCenter) {
        when {
            turn != null && turn.isDiscard -> DiscardBar(state, callbacks)
            turn != null -> BettingBar(state, callbacks)
            state.phase == TablePhase.Playing || state.phase == TablePhase.Idle -> WaitingBar(state)
            else -> HandOverBar(state, callbacks, run)
        }
    }
}

@Composable
private fun WaitingBar(state: TableUiState) {
    val p = PixelTheme.palette
    val who = state.seats.firstOrNull { it.thinking || it.isToAct }
    val text = when {
        state.phase == TablePhase.Idle -> "SHUFFLING..."
        state.human?.folded == true -> "YOU FOLDED. WATCH 'EM SWEAT."
        who != null && who.seat != 0 -> "${who.name.uppercase()} IS THINKING..."
        else -> "DEALING..."
    }
    Blinking(1400) {
        PixelText(text, Modifier.padding(bottom = 18.dp), style = PixelType.Small, color = p.cardCream, textAlign = TextAlign.Center)
    }
}

/** How much a quick-size button raises to: a fraction of the pot after calling. */
private fun raiseFor(fraction: Float, turn: HumanTurn): Int {
    val legal = turn.legal
    val min = legal.minRaiseTo ?: return 0
    val max = legal.maxRaiseTo ?: return min
    val target = turn.betThisRound + legal.toCall + (fraction * (turn.pot + legal.toCall)).toInt()
    return target.coerceIn(min, max)
}

@Composable
private fun BettingBar(state: TableUiState, callbacks: TableCallbacks) {
    val turn = state.humanTurn ?: return
    val legal = turn.legal
    var raising by remember(state.handNumber, state.street, legal) { mutableStateOf(false) }
    val min = legal.minRaiseTo
    val max = legal.maxRaiseTo
    var amount by remember(state.handNumber, state.street, legal) { mutableStateOf(min ?: 0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (raising && min != null && max != null) {
            RaisePanel(turn, amount, { amount = it.coerceIn(min, max) }, onCancel = { raising = false }, onConfirm = {
                raising = false
                callbacks.onAct(if (amount >= max && max == turn.betThisRound + turn.stack) Decision.AllIn else Decision.RaiseTo(amount))
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (legal.canFold && !legal.canCheck) {
                PixelButton("FOLD", { callbacks.onAct(Decision.Fold) }, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
            }
            val callAllIn = !legal.canCheck && legal.toCall >= turn.stack
            val callLabel = when {
                legal.canCheck -> "CHECK"
                callAllIn -> "ALL IN\n${formatChips(legal.toCall)}"
                else -> "CALL\n${formatChips(legal.toCall)}"
            }
            PixelButton(callLabel, { callbacks.onAct(if (legal.canCheck) Decision.Check else Decision.Call) }, Modifier.weight(1.2f), variant = PixelButtonVariant.Call, textStyle = PixelType.Small, sound = Sfx.Chip)
            if (min != null && max != null) {
                val word = if (state.seats.all { it.bet == 0 }) "BET" else "RAISE"
                if (min == max) {
                    PixelButton("$word\n${formatChips(min)}", { callbacks.onAct(Decision.RaiseTo(min)) }, Modifier.weight(1.2f), variant = PixelButtonVariant.Bet, textStyle = PixelType.Small, sound = Sfx.Chip)
                } else {
                    PixelButton(if (raising) "$word..." else word, { raising = !raising }, Modifier.weight(1.2f), variant = PixelButtonVariant.Bet, textStyle = PixelType.Small)
                }
            }
        }
    }
}

@Composable
private fun RaisePanel(turn: HumanTurn, amount: Int, onAmount: (Int) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val legal = turn.legal
    val min = legal.minRaiseTo ?: return
    val max = legal.maxRaiseTo ?: return
    val step = turn.bigBlind.coerceAtLeast(1)
    PixelPanel(Modifier.fillMaxWidth(), style = PanelStyle.Dark, paddingUnits = 2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelButton("-", { onAmount(amount - step) }, Modifier.size(52.dp, 50.dp), variant = PixelButtonVariant.Hold, enabled = amount > min)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                LedCounter(amount, label = "RAISE TO", color = LedColor.Green, minDigits = 5, segmentUnits = 1)
            }
            PixelButton("+", { onAmount(amount + step) }, Modifier.size(52.dp, 50.dp), variant = PixelButtonVariant.Hold, enabled = amount < max)
        }
        VGap(6.dp)
        PixelSlider(
            fraction = if (max > min) (amount - min).toFloat() / (max - min) else 1f,
            onFraction = { f -> onAmount(min + ((max - min) * f).toInt()) },
            modifier = Modifier.fillMaxWidth(),
        )
        VGap(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SmallButton("1/2 POT", { onAmount(raiseFor(0.5f, turn)) }, Modifier.weight(1f))
            SmallButton("POT", { onAmount(raiseFor(1f, turn)) }, Modifier.weight(1f))
            SmallButton("ALL IN", { onAmount(max) }, Modifier.weight(1f), variant = PixelButtonVariant.Deal)
        }
        VGap(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("CANCEL", onCancel, Modifier.weight(1f))
            PixelButton(
                if (amount >= max && max == turn.betThisRound + turn.stack) "ALL IN!" else "RAISE ${formatChips(amount)}",
                onConfirm, Modifier.weight(1.6f).height(48.dp), variant = PixelButtonVariant.Bet, textStyle = PixelType.Small, sound = Sfx.Chip,
            )
        }
    }
}

/** A chunky pixel slider: drag or tap anywhere on the track. */
@Composable
fun PixelSlider(fraction: Float, onFraction: (Float) -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Canvas(
        modifier
            // A 48dp touch target around the 30dp track and knob.
            .height(48.dp)
            .semantics {
                contentDescription = "Raise amount"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                setProgress { f ->
                    onFraction(f.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                fun report(x: Float) = onFraction((x / size.width).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    report(down.position.x)
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        report(change.position.x)
                    }
                }
            },
    ) {
        val w = size.width.toInt()
        val h = 30.dp.roundToPx()
        val top = (size.height.toInt() - h) / 2
        val trackH = unit * 4
        val ty = top + (h - trackH) / 2
        pxBox(0, ty, w, trackH, unit, p.ink, p.inkSoft)
        val knobW = unit * 7
        val x = ((w - knobW) * fraction.coerceIn(0f, 1f)).toInt()
        pxRect(unit, ty + unit, x + knobW / 2, trackH - 2 * unit, p.green)
        pxBox(x, top, knobW, h, unit, p.ink, p.yellow)
        pxRect(x + unit, top + unit, knobW - 2 * unit, unit, Color.White)
        pxRect(x + unit * 3, top + unit * 3, unit, h - unit * 6, p.goldDark)
    }
}

@Composable
private fun DiscardBar(state: TableUiState, callbacks: TableCallbacks) {
    val p = PixelTheme.palette
    val turn = state.humanTurn ?: return
    val rule = turn.legal.discard ?: return
    val count = state.human?.cards?.size ?: 0
    if (state.variant.isDraw) {
        val discards = count - state.selected.size
        val ok = discards in rule.min..rule.max
        val needHold = count - rule.max
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PixelText(if (ok) "TAP CARDS TO HOLD" else "HOLD AT LEAST $needHold", style = PixelType.Small.copy(fontSize = 9.sp), color = if (ok) p.cardCream else p.red)
            PixelButton(
                when (discards) { 0 -> "STAND PAT"; else -> "DRAW $discards" },
                callbacks.onConfirmDiscard,
                Modifier.fillMaxWidth(0.7f),
                variant = PixelButtonVariant.Deal,
                enabled = ok,
                sound = Sfx.Deal,
            )
        }
    } else {
        val ok = state.selected.size in rule.min..rule.max
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PixelText("TAP ONE CARD TO TOSS", style = PixelType.Small.copy(fontSize = 9.sp), color = p.cardCream)
            PixelButton("TOSS IT", callbacks.onConfirmDiscard, Modifier.fillMaxWidth(0.7f), variant = PixelButtonVariant.Deal, enabled = ok, sound = Sfx.Flip)
        }
    }
}

@Composable
private fun HandOverBar(state: TableUiState, callbacks: TableCallbacks, run: RunUiState?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.phase) {
            TablePhase.Busted -> {
                SmallButton("MENU", callbacks.onMenu, Modifier.weight(1f).height(56.dp))
                PixelButton("REBUY", callbacks.onNext, Modifier.weight(2f), variant = PixelButtonVariant.Deal, sound = Sfx.Chip)
            }
            TablePhase.TableWon -> {
                SmallButton("MENU", callbacks.onMenu, Modifier.weight(1f).height(56.dp))
                PixelButton("NEW TABLE", callbacks.onPlayAgain, Modifier.weight(2f), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
            }
            TablePhase.RunTableDone -> {
                val cleared = run?.state?.phase == RunPhase.CHOOSE
                PixelButton(if (cleared) "CASH OUT" else "GAME OVER", callbacks.onNext, Modifier.weight(1f), variant = if (cleared) PixelButtonVariant.Bet else PixelButtonVariant.Deal, sound = Sfx.Blip)
            }
            else -> PixelButton("NEXT HAND", callbacks.onNext, Modifier.weight(1f), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
        }
    }
}

// ---------------------------------------------------------------- banner, log

/** The showdown banner, centred in the gap between the board and your cards (over the board when tight). */
@Composable
private fun BannerLayer(state: TableUiState, maxWidth: Dp, spots: Map<Int, Rect>, wide: Boolean) {
    val p = PixelTheme.palette
    val text: String
    val sub: String?
    val won: Boolean
    when (state.phase) {
        TablePhase.Busted -> { text = "BUSTED!"; sub = "Out of chips"; won = false }
        TablePhase.TableWon -> { text = "TABLE CLEARED!"; sub = "Every opponent is broke"; won = true }
        else -> {
            val b = state.banner ?: return
            text = b.text; sub = b.subtitle; won = b.humanWon
        }
    }
    val fontSize = min(30.dp, (maxWidth * (if (wide) 0.55f else 1f) - 72.dp) / text.length.coerceAtLeast(1))
    val density = LocalDensity.current
    val board = spots[SPOT_BOARD]
    val human = spots[SPOT_HUMAN]
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigBanner(
                    text,
                    style = PixelType.Banner.copy(fontSize = with(density) { fontSize.toSp() }),
                    ribbon = if (won) p.hotPink else p.cabinet,
                    color = if (won) p.yellow else p.white,
                    sound = if (won) Sfx.Win else Sfx.Lose,
                )
                if (sub != null) PixelTag(sub.uppercase(), p.ink.copy(alpha = 0.85f), textColor = if (won) p.yellow else p.cardCream, style = PixelType.Small.copy(fontSize = 10.sp))
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(4f),
    ) { measurables, constraints ->
        val banner = measurables[0].measure(Constraints(maxWidth = constraints.maxWidth))
        val cx = if (wide && board != null) board.center.x else constraints.maxWidth / 2f
        val cy = when {
            wide && board != null -> board.center.y
            board != null && human != null && human.top - board.bottom >= banner.height * 0.8f -> (board.bottom + human.top) / 2f
            board != null -> board.center.y
            else -> constraints.maxHeight / 2f
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            banner.place((cx - banner.width / 2f).toInt(), (cy - banner.height / 2f).toInt())
        }
    }
}

@Composable
private fun LogPanel(state: TableUiState, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)) {
        state.log.forEach { line ->
            PixelText(line, style = PixelType.BodySmall, color = p.cardCream.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}
