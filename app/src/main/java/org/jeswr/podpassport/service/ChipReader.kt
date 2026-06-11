package org.jeswr.podpassport.service

import org.jeswr.podpassport.model.ChipReadResult
import org.jeswr.podpassport.model.MrzKey

/**
 * Coarse progress stages surfaced in the in-app UI while the chip is read.
 */
enum class ChipReadStage(val message: String) {
    WaitingForTag("Hold your phone on the passport"),
    Authenticating("Establishing secure session (PACE/BAC)"),
    ReadingData("Reading chip data"),
    Done("Read complete"),
}

sealed class ChipReaderException(message: String) : Exception(message) {
    val displayMessage: String get() = message ?: "Chip read failed"

    object InvalidMrzKey : ChipReaderException(
        "The document number, date of birth or expiry date is invalid. Check the values and try again.",
    )
    object NfcUnavailable : ChipReaderException(
        "NFC is not available on this device. A phone with NFC is required to read the chip.",
    )
    data class MissingMandatoryFile(val file: String) : ChipReaderException(
        "The chip did not return the mandatory $file file.",
    )
    data class ReadFailed(val detail: String) : ChipReaderException(detail)
    object Cancelled : ChipReaderException("Reading was cancelled.")
}

/**
 * Abstraction over the NFC chip read so the full flow runs without hardware
 * against [MockChipReader].
 */
interface ChipReader {
    /**
     * Reads DG1, SOD and — when present — DG2/DG11/DG14 from the chip.
     * [onProgress] may be invoked from any thread.
     */
    suspend fun readChip(
        mrzKey: MrzKey,
        onProgress: (ChipReadStage) -> Unit,
    ): ChipReadResult
}
