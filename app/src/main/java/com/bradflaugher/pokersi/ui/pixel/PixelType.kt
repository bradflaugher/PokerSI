package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.R

/** The three bundled OFL pixel fonts (see `licenses/`). */
object PixelFonts {
    /** Press Start 2P: chunky 8x8 arcade caps. Headings, buttons, banners. */
    val Arcade = FontFamily(Font(R.font.press_start_2p))

    /** VT323: tall terminal font. Body text, speech bubbles, long labels. */
    val Terminal = FontFamily(Font(R.font.vt323))

    /** Silkscreen: small, square caps. Labels, tags, pay table rows. */
    val Label = FontFamily(Font(R.font.silkscreen), Font(R.font.silkscreen_bold, FontWeight.Bold))
}

private val tightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * Text styles. Pixel fonts look best large and with a hard drop shadow:
 * use [PixelText] (or [hardShadow]) rather than plain `BasicText`.
 */
object PixelType {
    /** Huge banner text ("FULL HOUSE!"). */
    val Banner = TextStyle(fontFamily = PixelFonts.Arcade, fontSize = 30.sp, lineHeight = 1.2.em, lineHeightStyle = tightLines)

    /** Screen titles. */
    val Title = TextStyle(fontFamily = PixelFonts.Arcade, fontSize = 22.sp, lineHeight = 1.25.em, lineHeightStyle = tightLines)

    /** Section headings, button captions. */
    val Heading = TextStyle(fontFamily = PixelFonts.Arcade, fontSize = 14.sp, lineHeight = 1.3.em, lineHeightStyle = tightLines)

    /** Small arcade caps: tags, button captions on small buttons. */
    val Small = TextStyle(fontFamily = PixelFonts.Arcade, fontSize = 10.sp, lineHeight = 1.4.em, lineHeightStyle = tightLines)

    /** Big chunky numbers (chips, scores). */
    val Score = TextStyle(fontFamily = PixelFonts.Arcade, fontSize = 26.sp, lineHeight = 1.1.em, lineHeightStyle = tightLines)

    /** Readable body text. */
    val Body = TextStyle(fontFamily = PixelFonts.Terminal, fontSize = 24.sp, lineHeight = 1.0.em)

    /** Smaller body text. */
    val BodySmall = TextStyle(fontFamily = PixelFonts.Terminal, fontSize = 20.sp, lineHeight = 1.0.em)

    /** Square labels (pay table rows, captions). */
    val Label = TextStyle(fontFamily = PixelFonts.Label, fontSize = 14.sp, lineHeight = 1.2.em, lineHeightStyle = tightLines)

    /** Bold square labels. */
    val LabelBold = Label.copy(fontWeight = FontWeight.Bold)
}

/**
 * Adds a hard (unblurred) drop shadow of [units] chrome pixels down and to
 * the right, the arcade look.
 */
@Composable
fun TextStyle.hardShadow(color: Color = LocalPixelPalette.current.ink, units: Int = 1): TextStyle {
    val px = (LocalPixelUnit.current * units).toFloat()
    return copy(shadow = Shadow(color = color, offset = Offset(px, px), blurRadius = 0f))
}

/**
 * Pixel text with a hard drop shadow. [shadowUnits] 0 disables the shadow.
 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PixelType.Heading,
    color: Color = LocalPixelPalette.current.white,
    shadowColor: Color = LocalPixelPalette.current.ink,
    shadowUnits: Int = 1,
    textAlign: TextAlign? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    var s = style.copy(color = color)
    if (fontSize != TextUnit.Unspecified) s = s.copy(fontSize = fontSize)
    if (textAlign != null) s = s.copy(textAlign = textAlign)
    if (shadowUnits > 0) s = s.hardShadow(shadowColor, shadowUnits)
    BasicText(text, modifier, style = s, maxLines = maxLines, overflow = overflow)
}
