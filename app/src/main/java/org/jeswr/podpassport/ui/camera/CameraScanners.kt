package org.jeswr.podpassport.ui.camera

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.jeswr.podpassport.model.Mrz
import org.jeswr.podpassport.model.MrzParser
import org.jeswr.podpassport.model.ParsedMrz
import java.util.concurrent.Executors

/**
 * Live camera preview that runs an ML Kit QR-barcode analyzer and reports the
 * first decoded payload string. Manual entry remains the fallback elsewhere.
 */
@Composable
fun QrCameraScanner(modifier: Modifier = Modifier, onPayload: (String) -> Unit) {
    CameraPreview(modifier) { imageProxy, deliver ->
        analyzeQr(imageProxy) { value -> deliver { onPayload(value) } }
    }
}

/**
 * Live camera preview that runs ML Kit Latin text recognition and reports the
 * first checksum-valid TD3 MRZ parse it finds across recognised lines.
 */
@Composable
fun MrzCameraScanner(modifier: Modifier = Modifier, onMrz: (ParsedMrz) -> Unit) {
    CameraPreview(modifier) { imageProxy, deliver ->
        analyzeMrz(imageProxy) { parsed -> deliver { onMrz(parsed) } }
    }
}

private val barcodeScanner = BarcodeScanning.getClient()
private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeQr(imageProxy: ImageProxy, deliver: (String) -> Unit) {
    val media = imageProxy.image
    if (media == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(input)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.rawValue != null }?.rawValue
            if (raw != null) deliver(raw)
        }
        .addOnCompleteListener { imageProxy.close() }
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeMrz(imageProxy: ImageProxy, deliver: (ParsedMrz) -> Unit) {
    val media = imageProxy.image
    if (media == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
    textRecognizer.process(input)
        .addOnSuccessListener { result ->
            val candidates = result.textBlocks
                .flatMap { it.lines }
                .map { Mrz.normalizeOcrLine(it.text) }
                .filter { it.length == 44 && it.all { c -> c in Mrz.ALLOWED } }
            for (i in 0 until (candidates.size - 1).coerceAtLeast(0)) {
                val parsed = runCatching { MrzParser.parseTd3(candidates[i], candidates[i + 1]) }.getOrNull()
                if (parsed != null) {
                    deliver(parsed)
                    break
                }
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}

/**
 * Shared CameraX preview + analyzer host. [analyze] receives each frame and a
 * `deliver` lambda; calling `deliver { ... }` runs the action once and stops
 * further analysis (first valid result wins).
 */
@Composable
private fun CameraPreview(
    modifier: Modifier,
    analyze: (ImageProxy, deliver: (() -> Unit) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { booleanArrayOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    if (delivered[0]) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    analyze(proxy) { action ->
                        if (!delivered[0]) {
                            delivered[0] = true
                            ContextCompat.getMainExecutor(ctx).execute(action)
                        }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
