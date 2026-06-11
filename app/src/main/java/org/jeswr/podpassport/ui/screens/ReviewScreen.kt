package org.jeswr.podpassport.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReviewScreen(model: AppViewModel, modifier: Modifier = Modifier) {
    val summary = model.chipResult?.summary
    val bundle = model.chipResult?.bundle
    val session = model.session

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summary != null) {
                SectionCard("Passport") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Photo(summary.photoJpeg)
                        Column {
                            Text(
                                summary.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("review.fullName"),
                            )
                            Text(
                                summary.nationality,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    FieldRow("Document number", summary.documentNumber, tag = "review.documentNumber")
                    FieldRow("Date of birth", formatMrzDate(summary.dateOfBirth))
                    FieldRow("Expiry date", formatMrzDate(summary.dateOfExpiry))
                }
            }

            if (bundle != null) {
                SectionCard("What will be sent") {
                    FilesList(bundle)
                    Text(
                        "These chip files go to the issuer endpoint below — and nowhere else — so it can verify your passport's digital signature and issue your credential.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (session != null) {
                    SectionCard("Destination") {
                        FieldRow("Issuer endpoint", session.endpoint, tag = "review.endpoint")
                        FieldRow("Session", session.sessionId)
                    }
                }
            }
        }

        // Pinned primary action — the "send" decision is always reachable.
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { model.confirmedForUpload() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Send to issuer. Sends the chip files shown above to the issuer endpoint."
                    }
                    .testTag("review.sendButton"),
            ) {
                Text("Send to issuer")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, tag: String? = null) {
    Column(
        modifier = Modifier.semantics { contentDescription = "$label: $value" },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}

@Composable
private fun FilesList(bundle: ChipBundle) {
    data class FileRow(val code: String, val desc: String, val bytes: ByteArray?)
    val files = listOf(
        FileRow("DG1", "Machine-readable zone", bundle.lds.dg1),
        FileRow("DG2", "Facial photo", bundle.lds.dg2),
        FileRow("DG11", "Additional personal details", bundle.lds.dg11),
        FileRow("DG14", "Chip security info", bundle.lds.dg14),
        FileRow("SOD", "Document security object (signature)", bundle.lds.sod),
    ).filter { it.bytes != null }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        files.forEachIndexed { index, f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${f.code}, ${f.desc}, ${f.bytes?.size ?: 0} bytes" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(
                    "  ${f.desc}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${f.bytes?.size ?: 0} B",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < files.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun Photo(jpeg: ByteArray?) {
    val bitmap = jpeg?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 84.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = if (jpeg == null) "No passport photo on chip" else "Passport photo from the chip"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** `YYMMDD` → e.g. `12 Aug 1974`. */
private fun formatMrzDate(yymmdd: String): String {
    if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return yymmdd
    val yy = yymmdd.substring(0, 2).toInt()
    val mm = yymmdd.substring(2, 4).toInt()
    val dd = yymmdd.substring(4, 6).toInt()
    val currentYy = Calendar.getInstance().get(Calendar.YEAR) % 100
    val year = if (yy <= currentYy + 10) 2000 + yy else 1900 + yy
    return runCatching {
        val cal = Calendar.getInstance().apply { set(year, mm - 1, dd, 0, 0, 0) }
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
    }.getOrDefault(yymmdd)
}
