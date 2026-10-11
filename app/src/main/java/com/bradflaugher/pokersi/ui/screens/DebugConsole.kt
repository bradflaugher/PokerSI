package com.bradflaugher.pokersi.ui.screens

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogEntry
import com.bradflaugher.pokersi.debug.AiLogKind
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelFonts
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.pxRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.TimeZone

/** The console's filter chips. */
enum class ConsoleFilter(val label: String, val kinds: Set<AiLogKind>) {
    ALL("ALL", AiLogKind.entries.toSet()),
    PROMPTS("PROMPTS", setOf(AiLogKind.PROMPT, AiLogKind.GRAMMAR)),
    REPLIES("REPLIES", setOf(AiLogKind.REPLY, AiLogKind.DECISION)),
    ERRORS("ERRORS", setOf(AiLogKind.ERROR)),
}

/** Terminal phosphor colors. */
private object Phosphor {
    val bg = Color(0xE8020A05)
    val green = Color(0xFF3DFF6E)
    val dim = Color(0xFF1E9E45)
    val amber = Color(0xFFFFB21E)
    val red = Color(0xFFFF4A3D)
    val cyan = Color(0xFF4FE3D2)
    val gray = Color(0xFF7FA58A)
    val white = Color(0xFFE8FFE8)
    val line = Color(0x1A3DFF6E)
}

private val consoleText = TextStyle(fontFamily = PixelFonts.Terminal, fontSize = 18.sp, lineHeight = 1.0.em)

private fun colorFor(kind: AiLogKind): Color = when (kind) {
    AiLogKind.PROMPT -> Phosphor.amber
    AiLogKind.GRAMMAR -> Phosphor.gray
    AiLogKind.REPLY -> Phosphor.green
    AiLogKind.DECISION -> Phosphor.white
    AiLogKind.ERROR -> Phosphor.red
    AiLogKind.ENGINE -> Phosphor.cyan
    AiLogKind.TABLE -> Phosphor.dim
}

/** Body lines shown before "tap to expand": grammars stay folded, replies show in full. */
private fun previewLines(kind: AiLogKind): Int = when (kind) {
    AiLogKind.GRAMMAR -> 0
    AiLogKind.PROMPT -> 2
    AiLogKind.REPLY -> 6
    else -> 2
}

/**
 * The console's one "~" toggle, pinned to the top right of the screen over
 * the screens that offer the console (the table and the model bay) and over
 * the console itself, so it opens and closes it in the same place. Laid out
 * like the screens (safe area, tablet scaling): it lands in the slot their
 * headers keep free for it ([CONSOLE_SLOT]).
 */
@Composable
fun ConsoleToggle(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    TabletScaled(modifier.fillMaxSize().safeDrawingPadding()) {
        ConsoleButton(open, onToggle, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp))
    }
}

/** The width headers keep free, at their top right, for [ConsoleToggle] (in a tablet-scaled screen, see [ConsoleSlot]). */
val CONSOLE_SLOT = 48.dp

/**
 * The room [ConsoleToggle] takes, for a header that is not tablet-scaled
 * (the model bay, the console): [CONSOLE_SLOT] at the toggle's own scale.
 */
@Composable
fun ConsoleSlot(modifier: Modifier = Modifier) {
    val screen = LocalConfiguration.current
    val scale = tabletScale(screen.screenWidthDp.dp, screen.screenHeightDp.dp)
    Spacer(modifier.size(CONSOLE_SLOT * scale))
}

/** The "~" itself, drawn as pixel art (no pixel font has a readable tilde); lit green while the console is [open]. */
@Composable
fun ConsoleButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = PixelTheme.palette.ink
    val unit = PixelTheme.unit
    Box(modifier.size(CONSOLE_SLOT, CONSOLE_SLOT)) {
        PixelButton(
            " ", onClick,
            Modifier.fillMaxSize().semantics { contentDescription = if (open) "Close debug console" else "Open debug console" },
            variant = if (open) PixelButtonVariant.Call else PixelButtonVariant.Gray,
        )
        Canvas(Modifier.matchParentSize()) {
            val px = unit * 2
            val left = (size.width.toInt() - TILDE[0].length * px) / 2
            val top = (size.height.toInt() - TILDE.size * px) / 2 - unit
            TILDE.forEachIndexed { row, line ->
                line.forEachIndexed { col, c -> if (c == '#') pxRect(left + col * px, top + row * px, px, px, ink) }
            }
        }
    }
}

private val TILDE = listOf(
    ".##....",
    "#..#..#",
    "....##.",
)

/**
 * The Quake-style debug console: drops down from the top over whatever is
 * showing, about 60% of the screen, and lists [entries] (from [AiLog]) in a
 * green-and-amber pixel terminal. Tap an entry to expand it; it follows the
 * newest line unless scrolled up. Filter chips, CLEAR and COPY (plain text).
 */
@Composable
fun DebugConsole(
    entries: List<AiLogEntry>,
    open: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.getDefault(),
    initialFilter: ConsoleFilter = ConsoleFilter.ALL,
    initiallyExpanded: Set<Long> = emptySet(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight * 0.6f
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            ConsolePanel(entries, onClose, onClear, Modifier.fillMaxWidth().height(height), zone, initialFilter, initiallyExpanded)
        }
    }
}

@Composable
private fun ConsolePanel(
    entries: List<AiLogEntry>,
    onClose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
    zone: TimeZone,
    initialFilter: ConsoleFilter,
    initiallyExpanded: Set<Long>,
) {
    val unit = PixelTheme.unit.toFloat()
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    val expanded = remember { initiallyExpanded.toMutableStateList() }
    val shown = remember(entries, filter) { entries.filter { it.kind in filter.kinds } }
    // Opens at the newest line.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = shown.size)
    var follow by remember { mutableStateOf(true) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    // Follow the newest line, unless the reader scrolled up.
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.drop(1).collect { scrolling -> if (!scrolling) follow = !list.canScrollForward }
    }
    LaunchedEffect(shown.size, shown.lastOrNull()?.body?.length, follow) {
        if (follow && shown.isNotEmpty()) list.scrollToItem(shown.lastIndex, Int.MAX_VALUE / 2)
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Column(
        modifier
            .background(Phosphor.bg)
            .drawBehind {
                // Scanlines and a hard phosphor rule along the bottom edge.
                var y = 0f
                while (y < size.height) {
                    drawRect(Phosphor.line, Offset(0f, y), Size(size.width, unit / 2))
                    y += unit * 2
                }
                drawRect(Phosphor.green, Offset(0f, size.height - unit), Size(size.width, unit))
                drawRect(Phosphor.dim, Offset(0f, size.height - unit * 2), Size(size.width, unit))
            }
            // The same safe area (top and sides) as ConsoleToggle, so its slot lines up.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp)
            .padding(bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("> POKERSI AI CONSOLE", style = consoleText.copy(color = Phosphor.green, fontSize = 22.sp))
                BasicText(
                    "${entries.size}/${AiLog.CAPACITY} LINES · TAP A LINE TO EXPAND",
                    style = consoleText.copy(color = Phosphor.dim, fontSize = 16.sp),
                )
            }
            // The toggle ([ConsoleToggle]) sits here, over the console.
            ConsoleSlot()
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConsoleFilter.entries.forEach { f -> ConsoleChip(f.label, selected = f == filter) { filter = f } }
            Spacer(Modifier.size(6.dp))
            ConsoleChip("CLEAR", color = Phosphor.red) { onClear(); expanded.clear() }
            ConsoleChip(if (copied) "COPIED" else "COPY", color = Phosphor.amber) {
                val text = AiLog.plainText(shown, zone)
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PokerSI console", text))) }
                copied = true
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                if (shown.isEmpty()) {
                    item {
                        BasicText(
                            if (entries.isEmpty()) "Nothing yet. Deal a hand or load a model." else "Nothing here for this filter.",
                            Modifier.padding(top = 8.dp),
                            style = consoleText.copy(color = Phosphor.dim),
                        )
                    }
                }
                items(shown, key = { it.id }) { e ->
                    ConsoleLine(e, e.id in expanded, zone) { if (e.id in expanded) expanded.remove(e.id) else expanded.add(e.id) }
                }
                item { Cursor() }
            }
            if (!follow) {
                Box(Modifier.align(Alignment.BottomEnd)) {
                    ConsoleChip("v NEWEST", color = Phosphor.green) { follow = true }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLine(e: AiLogEntry, expanded: Boolean, zone: TimeZone, onToggle: () -> Unit) {
    val color = colorFor(e.kind)
    val lines = if (e.body.isEmpty()) emptyList() else e.body.trimEnd().lines()
    val preview = previewLines(e.kind)
    val clickable = lines.size > preview
    Column(
        Modifier
            .fillMaxWidth()
            // A tappable entry is a touch target: at least 48dp tall.
            .then(if (clickable) Modifier.heightIn(min = 48.dp).clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 2.dp),
    ) {
        Row {
            BasicText("${AiLog.timeLabel(e.timeMs, zone).drop(3)} ", style = consoleText.copy(color = Phosphor.dim))
            BasicText(
                "${e.kind.tag.padEnd(7)}${if (e.source == e.kind.tag) "" else e.source.uppercase() + ": "}${e.title}",
                style = consoleText.copy(color = color),
            )
        }
        val visible = if (expanded) lines else lines.take(preview)
        if (visible.isNotEmpty()) {
            BasicText(
                visible.joinToString("\n"),
                Modifier.padding(start = 12.dp),
                style = consoleText.copy(color = color.copy(alpha = 0.8f), fontSize = 16.sp),
                overflow = TextOverflow.Clip,
            )
        }
        if (clickable) {
            val more = lines.size - preview
            BasicText(
                if (expanded) "  [-] collapse" else "  [+] $more more line${if (more == 1) "" else "s"}",
                style = consoleText.copy(color = Phosphor.dim, fontSize = 16.sp),
            )
        }
    }
}

/** A blinking block cursor (steady in screenshots). */
@Composable
private fun Cursor() {
    var on by remember { mutableStateOf(true) }
    val animated = PixelTheme.animated
    if (animated) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(530)
                on = !on
            }
        }
    }
    BasicText(if (on) "> _" else ">  ", Modifier.padding(vertical = 4.dp), style = consoleText.copy(color = Phosphor.green))
}

@Composable
private fun ConsoleChip(text: String, selected: Boolean = false, color: Color = Phosphor.green, onClick: () -> Unit) {
    val unit = PixelTheme.unit
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stroke = with(density) { unit.toDp() }
    Box(
        Modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(if (selected) color else Color.Transparent)
                .border(stroke, color)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            BasicText(text, style = consoleText.copy(color = if (selected) Color.Black else color, fontSize = 20.sp))
        }
    }
}
