package org.jeswr.podpassport.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jeswr.podpassport.service.ChipReadStage
import org.jeswr.podpassport.service.ChipReaderException
import org.jeswr.podpassport.ui.AppViewModel

@Composable
fun NfcReadScreen(model: AppViewModel, modifier: Modifier = Modifier) {
    var stage by remember { mutableStateOf<ChipReadStage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun read() {
        val mrzKey = model.mrzKey ?: return
        reading = true
        error = null
        val reader = model.provideChipReader()
        scope.launch {
            try {
                val result = reader.readChip(mrzKey) { s ->
                    stage = s
                }
                reading = false
                model.chipRead(result)
            } catch (e: ChipReaderException) {
                reading = false
                stage = null
                error = e.displayMessage
            } catch (e: Exception) {
                reading = false
                stage = null
                error = e.message ?: "Chip read failed"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GuidanceAnimation()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Read the chip", style = MaterialTheme.typography.titleLarge)
            Text(
                "Close the passport, remove any thick case, and rest the top half of your phone flat on the front cover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        stage?.let { s ->
            Row(
                stage = s,
                modifier = Modifier.testTag("nfc.progressLabel"),
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("nfc.errorLabel"),
            )
        }

        Button(
            onClick = { read() },
            enabled = !reading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nfc.startButton"),
        ) {
            Text(if (reading) "Reading…" else if (error == null) "Start reading" else "Try again")
        }
    }
}

@Composable
private fun Row(stage: ChipReadStage, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.semantics { contentDescription = stage.message },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val done = stage == ChipReadStage.Done
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.Wifi,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stage.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuidanceAnimation() {
    val transition = rememberInfiniteTransition(label = "phone-lower")
    val offset by transition.animateFloat(
        initialValue = -90f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "offset",
    )

    Box(
        modifier = Modifier
            .height(240.dp)
            .semantics { contentDescription = "Illustration: phone resting on a passport" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF731C38),
            modifier = Modifier.size(width = 150.dp, height = 200.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    "PASSPORT",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.offset(y = offset.dp),
        ) {
            Icon(
                Icons.Outlined.Smartphone,
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp)
                    .padding(8.dp),
            )
        }
    }
}
