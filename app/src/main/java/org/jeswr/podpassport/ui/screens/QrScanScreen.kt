package org.jeswr.podpassport.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.jeswr.podpassport.model.QrPayloadDecoder
import org.jeswr.podpassport.model.QrPayloadException
import org.jeswr.podpassport.ui.AppViewModel
import org.jeswr.podpassport.ui.camera.QrCameraScanner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScanScreen(model: AppViewModel, modifier: Modifier = Modifier) {
    var manualEntry by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    fun handle(payload: String) {
        scanError = try {
            model.sessionCaptured(QrPayloadDecoder.decode(payload))
            null
        } catch (e: QrPayloadException) {
            e.displayMessage
        }
    }

    if (manualEntry || !cameraPermission.status.isGranted) {
        ManualSessionEntry(
            modifier = modifier,
            showCameraHint = !cameraPermission.status.isGranted && !manualEntry,
            onRequestCamera = { cameraPermission.launchPermissionRequest() },
            onSubmit = ::handle,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        QrCameraScanner(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("qr.cameraPreview"),
            onPayload = ::handle,
        )
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            scanError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Point the camera at the QR code on the issuer page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { manualEntry = true },
                modifier = Modifier.testTag("qr.manualButton"),
            ) {
                Text("Enter details manually")
            }
        }
    }
}

@Composable
private fun ManualSessionEntry(
    modifier: Modifier,
    showCameraHint: Boolean,
    onRequestCamera: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var endpoint by remember { mutableStateOf("") }
    var sessionId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Issuer session", style = MaterialTheme.typography.titleMedium)
        Text(
            "The issuer page shows these values next to the QR code.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showCameraHint) {
            TextButton(onClick = onRequestCamera, modifier = Modifier.testTag("qr.enableCamera")) {
                Text("Or grant camera access to scan the QR code")
            }
        }

        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Upload URL (https://…)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr.endpointField"),
        )
        OutlinedTextField(
            value = sessionId,
            onValueChange = { sessionId = it },
            label = { Text("Session ID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr.sessionIdField"),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text("Secret") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr.secretField"),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                error = try {
                    onSubmit(buildPayload(endpoint, sessionId, secret))
                    null
                } catch (e: QrPayloadException) {
                    e.displayMessage
                }
            },
            enabled = endpoint.isNotBlank() && sessionId.isNotBlank() && secret.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr.continueButton"),
        ) {
            Text("Continue")
        }
    }
}

/** Route manual entry through the same decoder so validation is identical. */
private fun buildPayload(endpoint: String, sessionId: String, secret: String): String {
    // Validate eagerly so the button handler can surface QrPayloadException.
    QrPayloadDecoder.fromManualEntry(endpoint, sessionId, secret)
    return org.json.JSONObject()
        .put("v", 1)
        .put("endpoint", endpoint.trim())
        .put("sessionId", sessionId.trim())
        .put("secret", secret.trim())
        .toString()
}
