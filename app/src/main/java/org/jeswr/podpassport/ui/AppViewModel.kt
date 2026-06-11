package org.jeswr.podpassport.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jeswr.podpassport.model.ChipReadResult
import org.jeswr.podpassport.model.IssuerSession
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.service.BundleUploader
import org.jeswr.podpassport.service.ChipReader
import org.jeswr.podpassport.service.HttpBundleUploader
import org.jeswr.podpassport.service.MockBundleUploader
import org.jeswr.podpassport.service.MockChipReader
import org.jeswr.podpassport.service.SampleBundle

/** The linear issuance-flow steps. */
enum class FlowStep { Home, ScanQr, MrzCapture, NfcRead, Review, Upload, Done }

/**
 * Central state for the flow. Mirrors the iOS `AppModel`. The chip reader and
 * uploader are injectable so the whole flow runs without NFC/camera/server
 * hardware: in `--uitest` / `mockUpload` mode the mocks are used; otherwise the
 * NFC read is wired per-tag from the Activity (see [provideNfcReader]).
 */
class AppViewModel(
    application: Application,
    private val mockChip: Boolean,
    private val mockUpload: Boolean,
    private val fast: Boolean,
) : AndroidViewModel(application) {

    var path by mutableStateOf(listOf(FlowStep.Home))
        private set

    val currentStep: FlowStep get() = path.last()

    // Collected along the flow.
    var session: IssuerSession? by mutableStateOf(null)
        private set
    var mrzKey: MrzKey? by mutableStateOf(null)
        private set
    var chipResult: ChipReadResult? by mutableStateOf(null)
        private set

    /** Debug toggle: force the sample-passport mock reader on the Home screen. */
    var useSamplePassport by mutableStateOf(mockChip)

    val uploader: BundleUploader =
        if (mockUpload) MockBundleUploader(delayMs = if (fast) 0 else 300) else HttpBundleUploader()

    /**
     * Provide the chip reader for the NFC step. When the sample-passport toggle
     * is on (always true on devices without NFC, and in tests), the mock reader
     * backed by the bundled fixture is used. A real-tag reader is supplied by
     * the Activity via [pendingNfcReader].
     */
    fun provideChipReader(): ChipReader {
        if (useSamplePassport) {
            return MockChipReader(
                bundle = SampleBundle.load(getApplication()),
                stageDelayMs = if (fast) 0 else 600,
            )
        }
        return pendingNfcReader ?: MockChipReader(
            bundle = SampleBundle.load(getApplication()),
            stageDelayMs = if (fast) 0 else 600,
        )
    }

    /** Set by the Activity when a passport tag is dispatched. */
    var pendingNfcReader: ChipReader? = null

    // MARK: Flow transitions

    fun start() {
        session = null
        mrzKey = null
        chipResult = null
        path = listOf(FlowStep.Home, FlowStep.ScanQr)
    }

    fun sessionCaptured(session: IssuerSession) {
        this.session = session
        push(FlowStep.MrzCapture)
    }

    fun mrzCaptured(key: MrzKey) {
        this.mrzKey = key
        push(FlowStep.NfcRead)
    }

    fun chipRead(result: ChipReadResult) {
        this.chipResult = result
        push(FlowStep.Review)
    }

    fun confirmedForUpload() = push(FlowStep.Upload)

    fun uploadSucceeded() = push(FlowStep.Done)

    fun back() {
        if (path.size > 1) path = path.dropLast(1)
    }

    fun reset() {
        path = listOf(FlowStep.Home)
        session = null
        mrzKey = null
        chipResult = null
    }

    private fun push(step: FlowStep) {
        path = path + step
    }
}
