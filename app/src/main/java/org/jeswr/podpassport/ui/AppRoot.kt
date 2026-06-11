package org.jeswr.podpassport.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jeswr.podpassport.ui.screens.DoneScreen
import org.jeswr.podpassport.ui.screens.HomeScreen
import org.jeswr.podpassport.ui.screens.MrzCaptureScreen
import org.jeswr.podpassport.ui.screens.NfcReadScreen
import org.jeswr.podpassport.ui.screens.QrScanScreen
import org.jeswr.podpassport.ui.screens.ReviewScreen
import org.jeswr.podpassport.ui.screens.UploadScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(model: AppViewModel) {
    val step = model.currentStep
    val canGoBack = step != FlowStep.Home && step != FlowStep.Done && step != FlowStep.Upload

    BackHandler(enabled = canGoBack) { model.back() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (step != FlowStep.Home && step != FlowStep.Done) {
                    TopAppBar(
                        title = { Text(titleFor(step)) },
                        navigationIcon = {
                            if (canGoBack) {
                                IconButton(onClick = { model.back() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "flow-step",
                modifier = Modifier.testTag("flow.container"),
            ) { current ->
                val content = Modifier.padding(padding)
                when (current) {
                    FlowStep.Home -> HomeScreen(model, content)
                    FlowStep.ScanQr -> QrScanScreen(model, content)
                    FlowStep.MrzCapture -> MrzCaptureScreen(model, content)
                    FlowStep.NfcRead -> NfcReadScreen(model, content)
                    FlowStep.Review -> ReviewScreen(model, content)
                    FlowStep.Upload -> UploadScreen(model, content)
                    FlowStep.Done -> DoneScreen(model, content)
                }
            }
        }
    }
}

private fun titleFor(step: FlowStep): String = when (step) {
    FlowStep.ScanQr -> "Scan issuer code"
    FlowStep.MrzCapture -> "Scan passport page"
    FlowStep.NfcRead -> "Chip"
    FlowStep.Review -> "Review"
    FlowStep.Upload -> "Upload"
    else -> ""
}
