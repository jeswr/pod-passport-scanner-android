package org.jeswr.podpassport.service

import kotlinx.coroutines.delay
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.ChipReadResult
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.model.MrzParser
import org.jeswr.podpassport.model.PassportSummary

/**
 * Test / no-hardware chip reader: returns the supplied synthetic eMRTD bundle
 * (same structure as the issuer-side fixture) instead of touching NFC. Ignores
 * the MRZ key, emits the progress stages, and parses DG1 for the summary.
 *
 * The bundle is injected so unit tests can construct it directly from the
 * fixture string without an Android [android.content.Context].
 */
class MockChipReader(
    private val bundle: ChipBundle,
    /** Delay between stages so the progress UI is visible. Zero in tests. */
    private val stageDelayMs: Long = 600,
) : ChipReader {

    override suspend fun readChip(
        mrzKey: MrzKey,
        onProgress: (ChipReadStage) -> Unit,
    ): ChipReadResult {
        for (stage in listOf(ChipReadStage.WaitingForTag, ChipReadStage.Authenticating, ChipReadStage.ReadingData)) {
            onProgress(stage)
            if (stageDelayMs > 0) delay(stageDelayMs)
        }

        val mrz = bundle.mrzFromDg1()
            ?: throw ChipReaderException.MissingMandatoryFile("DG1 (sample bundle has no parseable MRZ)")
        val parsed = try {
            MrzParser.parseTd3Contiguous(mrz)
        } catch (e: Exception) {
            throw ChipReaderException.MissingMandatoryFile("DG1 (sample bundle MRZ did not parse)")
        }

        val summary = PassportSummary(
            fullName = parsed.fullName,
            documentNumber = parsed.documentNumber,
            nationality = parsed.nationality,
            dateOfBirth = parsed.dateOfBirth,
            dateOfExpiry = parsed.dateOfExpiry,
            photoJpeg = null, // the synthetic DG2 is not a real JPEG
        )

        onProgress(ChipReadStage.Done)
        return ChipReadResult(bundle = bundle, summary = summary)
    }
}
