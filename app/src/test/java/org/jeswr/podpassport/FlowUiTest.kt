package org.jeswr.podpassport

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.jeswr.podpassport.ui.AppRoot
import org.jeswr.podpassport.ui.AppViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the entire issuance flow on the JVM with the mock chip reader and mock
 * uploader (both mocks + manual-entry paths + zero delays). No NFC/camera/server
 * hardware is needed — this is the Android counterpart of the iOS FlowUITests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullIssuanceFlowThroughMocks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = AppViewModel(
            application = app,
            mockChip = true,
            mockUpload = true,
            fast = true,
        )

        composeRule.setContent { AppRoot(model) }

        // Home → start (button sits below the fold; scroll it in, then click)
        composeRule.onNodeWithTag("home.start").assertExists()
        composeRule.onNodeWithTag("home.start").performScrollTo().performClick()

        // Step 1: issuer session (manual entry — no camera permission under test)
        composeRule.onNodeWithTag("qr.endpointField").assertExists()
        composeRule.onNodeWithTag("qr.endpointField")
            .performTextInput("https://issuer.example/api/emrtd/sessions/uitest-1")
        composeRule.onNodeWithTag("qr.sessionIdField").performTextInput("uitest-1")
        composeRule.onNodeWithTag("qr.secretField").performTextInput("uitest-secret")
        composeRule.onNodeWithTag("qr.continueButton").performScrollTo().performClick()

        // Step 2: MRZ key (manual entry; ICAO specimen values)
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTagSafe("mrz.docNumberField") }
        composeRule.onNodeWithTag("mrz.docNumberField").performTextInput("L898902C3")
        composeRule.onNodeWithTag("mrz.dobField").performTextInput("740812")
        composeRule.onNodeWithTag("mrz.expiryField").performTextInput("120415")
        composeRule.onNodeWithTag("mrz.continueButton").performScrollTo().performClick()

        // Step 3: chip read (mock reads the bundled JANE DOE fixture)
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTagSafe("nfc.startButton") }
        composeRule.onNodeWithTag("nfc.startButton").performClick()

        // Step 4: review shows the sample passport's parsed MRZ
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTagSafe("review.sendButton")
        }
        composeRule.onNodeWithTag("review.fullName").assertExists()
        composeRule.onNodeWithTag("review.documentNumber").assertExists()
        composeRule.onNodeWithTag("review.sendButton").performClick()

        // Step 5/6: upload (mock succeeds) then done
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagSafe("done.title")
        }
        composeRule.onNodeWithTag("done.title").assertExists()

        // Reset returns home
        composeRule.onNodeWithTag("done.resetButton").performClick()
        composeRule.onNodeWithTag("home.start").assertExists()
    }
}

/** True once at least one node with [tag] exists (for waitUntil). */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTag(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag))
