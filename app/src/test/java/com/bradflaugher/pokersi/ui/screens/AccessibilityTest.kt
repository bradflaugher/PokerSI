package com.bradflaugher.pokersi.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.ui.pixel.LocalPixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.screenshot.registerComponentActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** TalkBack can read the table: cards, seats, counters, and action buttons are big enough to hit. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-port-420dpi")
class AccessibilityTest {
    @Test
    fun tableIsReadable() {
        registerComponentActivity()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme { TableScreen(ScreenshotScenes.holdemMidHand(), TableCallbacks()) }
                }
            }
            onNodeWithContentDescription("Ace of hearts").assertExists()
            onNodeWithContentDescription("King of spades").assertExists()
            onNodeWithContentDescription("Pot 410").assertExists()
            onNodeWithContentDescription("Your chips", substring = true).assertExists()
            onNodeWithContentDescription("Pause").assertExists()
            val dot = onNodeWithContentDescription("Lady Dot, rock", substring = true).fetchSemanticsNode()
            val said = dot.config.toString()
            assertTrue(said, said.contains("1,130 chips") && said.contains("last action check"))
            onNodeWithContentDescription("Captain Chip, maniac", substring = true).assertExists()
            actionButtonsAreTall()
        }
    }

    @Test
    fun heldCardsSayHeld() {
        registerComponentActivity()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme { TableScreen(ScreenshotScenes.drawWithHolds(), TableCallbacks()) }
                }
            }
            onNodeWithContentDescription("Queen of spades, held").assertExists()
            onNodeWithContentDescription("Four of clubs").assertExists()
        }
    }

    @Test
    fun tourIsReadableAndItsButtonsAreTall() {
        registerComponentActivity()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme { TourOverlay(onDismiss = {}) }
                }
            }
            // The page is read in full (the bubble types out, so the header carries the words).
            onNodeWithContentDescription("Step 1 of 5", substring = true).assertExists()
            for (label in listOf("SKIP", "BACK", "NEXT")) {
                onNode(hasText(label).and(hasClickAction())).assertHeightIsAtLeast(48.dp)
            }
            onNode(hasText("NEXT").and(hasClickAction())).performClick()
            onNodeWithContentDescription("Step 2 of 5", substring = true).assertExists()
        }
    }

    @Test
    fun faqQuestionsAreTallButtons() {
        registerComponentActivity()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme { HowToPlayScreen({}, openFaq = 0) }
                }
            }
            onNode(hasText(Faqs[0].question, substring = true).and(hasClickAction()), useUnmergedTree = false).assertHeightIsAtLeast(48.dp)
            onNodeWithText(Faqs[0].answer).assertExists()
            onNode(hasText("TAKE THE TOUR").and(hasClickAction())).assertHeightIsAtLeast(48.dp)
        }
    }

    private fun SemanticsNodeInteractionsProvider.actionButtonsAreTall() {
        for (label in listOf("FOLD", "RAISE")) {
            onNode(hasText(label).and(hasClickAction()), useUnmergedTree = false).assertHeightIsAtLeast(48.dp)
        }
        onNodeWithContentDescription("Pause").assertHeightIsAtLeast(48.dp)
    }
}
