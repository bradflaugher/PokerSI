package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.ui.pixel.LocalPixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.screenshot.registerComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "are you sure?" dialog: TalkBack hears a pane and a heading, both choices are big, each does its one thing. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-320dpi")
class ConfirmDialogTest {
    @Test
    fun staysOrConfirms() {
        registerComponentActivity()
        var stayed = 0
        var confirmed = 0
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme {
                        ConfirmDialog("LEAVE THE CABINET?", "The cards will miss you.", "EXIT", "STAY", onConfirm = { confirmed++ }, onStay = { stayed++ })
                    }
                }
            }
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "LEAVE THE CABINET?")).assertExists()
            onNode(hasText("LEAVE THE CABINET?").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))).assertExists()
            val stay = onNode(hasText("STAY").and(hasClickAction()))
            val exit = onNode(hasText("EXIT").and(hasClickAction()))
            stay.assertHeightIsAtLeast(48.dp)
            exit.assertHeightIsAtLeast(48.dp)
            stay.performClick()
            assertEquals(1 to 0, stayed to confirmed)
            exit.performClick()
            assertEquals(1 to 1, stayed to confirmed)
        }
    }

    @Test
    fun whatIsBehindIsOutOfTalkBacksReach() {
        registerComponentActivity()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalPixelRenderMode provides PixelRenderMode.Static) {
                    PixelTheme {
                        Box {
                            PixelButton("NEW TOUR", {}, Modifier.modalBehind(true))
                            ConfirmDialog("START A NEW TOUR?", "Your saved tour is thrown away.", "NEW TOUR?", "KEEP MY TOUR", onConfirm = {}, onStay = {})
                        }
                    }
                }
            }
            onNodeWithText("NEW TOUR").assertDoesNotExist()
            onNodeWithText("KEEP MY TOUR").assertExists()
        }
    }
}
