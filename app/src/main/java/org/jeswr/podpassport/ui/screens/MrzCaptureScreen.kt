package org.jeswr.podpassport.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.ui.AppViewModel
import org.jeswr.podpassport.ui.camera.MrzCameraScanner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MrzCaptureScreen(model: AppViewModel, modifier: Modifier = Modifier) {
    var manualEntry by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (manualEntry || !cameraPermission.status.isGranted) {
        ManualMrzEntry(
            modifier = modifier,
            showCameraHint = !cameraPermission.status.isGranted && !manualEntry,
            onRequestCamera = { cameraPermission.launchPermissionRequest() },
            onKey = { model.mrzCaptured(it) },
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        MrzCameraScanner(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("mrz.cameraPreview"),
            onMrz = { model.mrzCaptured(it.key) },
        )
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Frame the two lines of letters and numbers at the bottom of your passport's photo page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { manualEntry = true },
                modifier = Modifier.testTag("mrz.manualButton"),
            ) {
                Text("Enter details manually")
            }
        }
    }
}

@Composable
private fun ManualMrzEntry(
    modifier: Modifier,
    showCameraHint: Boolean,
    onRequestCamera: () -> Unit,
    onKey: (MrzKey) -> Unit,
) {
    var documentNumber by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var dateOfExpiry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Passport details", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exactly as printed in the machine-readable zone. These derive the key that unlocks the chip — they never leave your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showCameraHint) {
            TextButton(onClick = onRequestCamera, modifier = Modifier.testTag("mrz.enableCamera")) {
                Text("Or grant camera access to scan the MRZ")
            }
        }

        OutlinedTextField(
            value = documentNumber,
            onValueChange = { documentNumber = it.uppercase() },
            label = { Text("Document number") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mrz.docNumberField"),
        )
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it.filter(Char::isDigit).take(6) },
            label = { Text("Date of birth (YYMMDD)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mrz.dobField"),
        )
        OutlinedTextField(
            value = dateOfExpiry,
            onValueChange = { dateOfExpiry = it.filter(Char::isDigit).take(6) },
            label = { Text("Expiry date (YYMMDD)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mrz.expiryField"),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                val key = MrzKey(
                    documentNumber = documentNumber.trim().uppercase(),
                    dateOfBirth = dateOfBirth.trim(),
                    dateOfExpiry = dateOfExpiry.trim(),
                )
                if (key.bacKeyString == null) {
                    error =
                        "Check the values: dates must be six digits (YYMMDD) and the document number may only contain letters and digits."
                } else {
                    onKey(key)
                }
            },
            enabled = documentNumber.isNotBlank() && dateOfBirth.length == 6 && dateOfExpiry.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mrz.continueButton"),
        ) {
            Text("Continue")
        }
    }
}
