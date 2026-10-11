package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.R
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.ui.pixel.BubbleTail
import com.bradflaugher.pokersi.ui.pixel.FeltBackground
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.MarqueeFrame
import com.bradflaugher.pokersi.ui.pixel.PixelCardFan
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.SpeechBubble
import com.bradflaugher.pokersi.ui.pixel.pxRect

/** Google Play's feature graphic size, in pixels. */
const val FEATURE_W = 1024
const val FEATURE_H = 500

/**
 * Rendered at 320dpi so every piece of art lands on the same 3-pixel grid:
 * the chrome pixel unit is 3px, the cards draw at 3x, and the launcher
 * icon's 1.5dp pixels come out at 3px too.
 */
const val FEATURE_DPI = 320

/**
 * The Play feature graphic, drawn with the app's own pixel design system:
 * a crisp royal flush fanned across the right, the marquee logo over its
 * first card, the tagline below, and the robot chip talking trash;
 * all on the lamp-lit felt.
 */
@Composable
fun FeatureGraphic() {
    PixelTheme {
        val p = PixelTheme.palette
        val cards = remember { Card.parseList("Th Jh Qh Kh Ah") }
        FeltBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                PixelCardFan(cards, Modifier.align(Alignment.TopEnd).padding(top = 2.dp), cardWidth = 68.dp, degrees = 8f)
                val unit = LocalPixelUnit.current
                MarqueeFrame(
                    Modifier.align(Alignment.TopStart).width(232.dp).drawBehind {
                        // A hard shadow onto the cards it overlaps.
                        pxRect(unit * 3, unit * 3, size.width.toInt(), size.height.toInt(), p.ink.copy(alpha = 0.6f))
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelText("POKER", style = PixelType.Banner, color = p.yellow, shadowUnits = 2, fontSize = 38.sp)
                        PixelText("S.I.", style = PixelType.Banner, color = p.hotPink, shadowUnits = 2, fontSize = 38.sp)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).width(232.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelText(
                        "Poker vs. a tiny AI\nthat lives on your phone",
                        style = PixelType.Body.copy(fontSize = 22.sp, lineHeight = 21.sp),
                        color = p.cardCream, textAlign = TextAlign.Center, shadowUnits = 1,
                    )
                    PixelText(
                        "SIX GAMES * POKER TOUR * NO ADS",
                        style = PixelType.LabelBold.copy(fontSize = 9.sp), color = p.gold, textAlign = TextAlign.Center,
                    )
                }
                Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.Bottom) {
                    SpeechBubble(
                        "I'm all in, meatbag.", tail = BubbleTail.BottomEnd,
                        textStyle = PixelType.Body.copy(fontSize = 17.sp), modifier = Modifier.padding(bottom = 30.dp),
                    )
                    // The launcher icon's chip; its 1.5dp pixels are 3px here.
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(108.dp))
                    }
                }
            }
        }
    }
}
