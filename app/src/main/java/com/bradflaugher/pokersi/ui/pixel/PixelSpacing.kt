package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Gaps between things you can tap are at least [Tap],
 * so a thumb that misses one button lands on felt, not on its neighbour.
 */
object PixelSpacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp

    /** Between two tap targets. */
    val Tap = S

    /** Between FOLD and the buttons that put chips in: a moat, not a seam. */
    val Guard = 20.dp
}
