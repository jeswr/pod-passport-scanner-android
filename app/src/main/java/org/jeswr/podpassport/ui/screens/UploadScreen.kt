package org.jeswr.podpassport.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jeswr.podpassport.service.UploadException
import org.jeswr.podpassport.ui.AppViewModel

@Composable
fun UploadScreen(model: AppViewModel, modifier: Modifier = Modifier) {
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Block back navigation while a PUT is in flight.
    BackHandler(enabled = uploading) {}

    fun upload() {
        val bundle = model.chipResult?.bundle ?: return
        val session = model.session ?: return
        uploading = true
        error = null
        scope.launch {
            try {
                model.uploader.upload(bundle, session)
                uploading = false
                model.uploadSucceeded()
            } catch (e: UploadException) {
                uploading = false
                error = e.displayMessage
            } catch (e: Exception) {
                uploading = false
                error = e.message ?: "Upload failed"
            }
        }
    }

    LaunchedEffect(Unit) { upload() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        when {
            uploading -> {
                CircularProgressIndicator()
                Text(
                    "Sending to the issuer…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("upload.progressLabel"),
                )
            }
            error != null -> {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp),
                )
                Text("Upload failed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("upload.errorLabel"),
                )
                Button(
                    onClick = { upload() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload.retryButton"),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
