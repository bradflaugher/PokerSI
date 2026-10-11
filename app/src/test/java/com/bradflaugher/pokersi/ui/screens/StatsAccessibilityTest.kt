package com.bradflaugher.pokersi.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.game.HandTally
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.screenshot.registerComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** TalkBack can step through the Hold'em chart's hands. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-port-420dpi")
class StatsAccessibilityTest {
    @Test
    fun nextAndPreviousHandReadEachDealtHand() {
        registerComponentActivity()
        runComposeUiTest {
            val hands = mapOf("AA" to HandTally(4, 4), "AKs" to HandTally(5, 4), "72o" to HandTally(11, 0))
            setContent { PixelTheme { HandChart(hands) } }
            val chart = onNodeWithContentDescription("Hold'em starting-hand chart", substring = true)
            fun act(label: String) {
                val action = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
                runOnIdle { action.action() }
                waitForIdle()
            }
            act("Next hand")
            chart.assertContentDescriptionContains("Selected: Pair of Aces, dealt 4, played 4", substring = true)
            act("Next hand")
            chart.assertContentDescriptionContains("Selected: Ace King suited", substring = true)
            act("Previous hand")
            act("Previous hand")
            chart.assertContentDescriptionContains("Selected: Seven Two offsuit, dealt 11, played 0", substring = true)
        }
    }

    @Test
    fun anEmptyChartOffersNothingToStepThrough() {
        registerComponentActivity()
        runComposeUiTest {
            setContent { PixelTheme { HandChart(emptyMap()) } }
            val node = onNodeWithContentDescription("Hold'em starting-hand chart: no hands yet.").fetchSemanticsNode()
            assertFalse(node.config.contains(SemanticsActions.CustomActions))
        }
    }
}
