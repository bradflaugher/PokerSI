package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.game.QuickGameSetup
import com.bradflaugher.pokersi.game.formatChips
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx
import com.bradflaugher.pokersi.ui.pixel.pxRect
import androidx.compose.ui.graphics.lerp

/** Two short pay-table-style lines per game for the picker tiles. */
fun GameVariant.tileFacts(): Pair<String, String> = when (this) {
    GameVariant.TEXAS_HOLDEM -> "2 HOLE + 5 BOARD" to "NO LIMIT"
    GameVariant.OMAHA -> "4 HOLE, USE 2" to "POT LIMIT"
    GameVariant.FIVE_CARD_DRAW -> "5 CARDS, 1 DRAW" to "FIXED LIMIT"
    GameVariant.SEVEN_CARD_STUD -> "7 CARDS, 4 UP" to "FIXED LIMIT"
    GameVariant.SHORT_DECK -> "36 CARDS, 6 UP" to "FLUSH > BOAT"
    GameVariant.CRAZY_PINEAPPLE -> "3 HOLE, TOSS 1" to "NO LIMIT"
}

class PickerCallbacks(
    val onVariant: (GameVariant) -> Unit = {},
    val onOpponents: (Int) -> Unit = {},
    val onStack: (Int) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onDeal: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Quick game setup: the six games as cabinet tiles, opponents, stack, DEAL. */
@Composable
fun PickerScreen(setup: QuickGameSetup, callbacks: PickerCallbacks, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("PICK A GAME", callbacks.onBack)
            BoxWithConstraints(Modifier.weight(1f)) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.readable(900.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameVariant.entries.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { v -> VariantTile(v, v == setup.variant, { callbacks.onVariant(v) }, Modifier.weight(1f)) }
                            }
                        }
                    }
                    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeading("OPPONENTS", Modifier.weight(1f))
                            PixelButton("-", { callbacks.onOpponents(setup.opponentCount - 1) }, Modifier.size(50.dp, 48.dp), variant = PixelButtonVariant.Hold, enabled = setup.opponentCount > 1)
                            PixelText("${setup.opponentCount}", style = PixelType.Title, color = p.yellow, shadowUnits = 2)
                            PixelButton("+", { callbacks.onOpponents(setup.opponentCount + 1) }, Modifier.size(50.dp, 48.dp), variant = PixelButtonVariant.Hold, enabled = setup.opponentCount < 5)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                            setup.opponents.forEach { o ->
                                Column(Modifier.width(72.dp).clearAndSetSemantics { contentDescription = o.spoken }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    PixelAvatar(o.avatarSeed, Mood.Smug, size = 48.dp)
                                    PixelText(o.name.uppercase(), style = PixelType.LabelBold.copy(fontSize = nameSize(o.name, 9f)), color = p.yellow, textAlign = TextAlign.Center, maxLines = 2)
                                    PixelText(o.style.name.uppercase(), style = PixelType.Label.copy(fontSize = 8.sp), color = p.cardCream, textAlign = TextAlign.Center, shadowUnits = 0, maxLines = 2)
                                }
                            }
                        }
                        SmallButton("SHUFFLE OPPONENTS", callbacks.onShuffle, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Pink, sound = Sfx.Flip)
                        SectionHeading("STACK")
                        ChoiceRow(QuickGameSetup.STACKS, setup.stack, { formatChips(it) }, callbacks.onStack)
                        PixelText(
                            "BLINDS ${formatChips(setup.config().bigBlind / 2)}/${formatChips(setup.config().bigBlind)}, UP EVERY 10 HANDS",
                            style = PixelType.Label.copy(fontSize = 11.sp), color = p.cardCream, shadowUnits = 0,
                        )
                    }
                    VGap(8.dp)
                }
            }
            PixelButton(
                "DEAL", callbacks.onDeal,
                Modifier.readable(420.dp).padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.CenterHorizontally),
                variant = PixelButtonVariant.Deal, sound = Sfx.Deal,
            )
        }
    }
}

@Composable
private fun VariantTile(variant: GameVariant, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    val glow = if (selected) pulse(600) else 0f
    val (a, b) = variant.tileFacts()
    Column(
        modifier
            .height(104.dp)
            .arcadeClick(onClick = onClick)
            .drawBehind {
                val w = size.width.toInt(); val h = size.height.toInt()
                val border = if (selected) lerp(p.hotPinkDark, p.hotPink, glow) else p.gold
                pxBox(0, 0, w, h, unit, p.ink, border)
                pxBox(unit * 2, unit * 2, w - unit * 4, h - unit * 4, unit, p.ink, if (selected) Color(0xFF241060) else p.cabinetDeep)
                pxRect(unit * 3, unit * 3, w - unit * 6, unit, p.cabinet)
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PixelText(variant.displayName.uppercase(), style = PixelType.Small.copy(fontSize = 9.sp), color = if (selected) p.hotPink else p.yellow, maxLines = 2)
        PixelText(variant.tagline, Modifier.weight(1f), style = PixelType.BodySmall.copy(fontSize = 16.sp), color = p.white, shadowUnits = 0, maxLines = 2)
        FactRow(a)
        FactRow(b)
    }
}

@Composable
private fun FactRow(text: String) {
    val p = PixelTheme.palette
    PixelText(text, style = PixelType.LabelBold.copy(fontSize = 10.sp), color = p.ledAmber, shadowUnits = 0, maxLines = 1)
}
