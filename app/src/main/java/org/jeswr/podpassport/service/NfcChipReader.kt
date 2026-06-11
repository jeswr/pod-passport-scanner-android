package org.jeswr.podpassport.service

import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.ChipReadResult
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.model.PassportSummary
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG11File
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.DG14File
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Real chip reader backed by jMRTD + SCUBA (the standard Android passport-NFC
 * stack, analog of iOS NFCPassportReader). Tries PACE first (from EF.CardAccess
 * when present) and falls back to BAC, then reads DG1, SOD and — when present —
 * DG2/DG11/DG14.
 *
 * Constructed with an already-dispatched [IsoDep]-backed [Tag] (the Activity's
 * `onTagDiscovered` hands the tag over). Only meaningful on a physical device.
 */
class NfcChipReader(private val tag: Tag) : ChipReader {

    override suspend fun readChip(
        mrzKey: MrzKey,
        onProgress: (ChipReadStage) -> Unit,
    ): ChipReadResult = withContext(Dispatchers.IO) {
        val bacKeyString = mrzKey.bacKeyString ?: throw ChipReaderException.InvalidMrzKey
        val isoDep = IsoDep.get(tag) ?: throw ChipReaderException.NfcUnavailable
        isoDep.timeout = 10_000

        onProgress(ChipReadStage.WaitingForTag)
        val service = PassportService(
            CardService.getInstance(isoDep),
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.DEFAULT_MAX_BLOCKSIZE,
            false,
            false,
        )
        try {
            service.open()

            onProgress(ChipReadStage.Authenticating)
            val bacKey = BACKey(mrzKey.documentNumber, mrzKey.dateOfBirth, mrzKey.dateOfExpiry)
            val paceSucceeded = tryPace(service, bacKey)
            service.sendSelectApplet(paceSucceeded)
            if (!paceSucceeded) {
                // PACE not available / failed → fall back to BAC.
                service.doBAC(bacKey)
            }

            onProgress(ChipReadStage.ReadingData)
            val dg1 = readFile(service, PassportService.EF_DG1)
                ?: throw ChipReaderException.MissingMandatoryFile("DG1")
            val sod = readFile(service, PassportService.EF_SOD)
                ?: throw ChipReaderException.MissingMandatoryFile("SOD (Document Security Object)")
            val dg2 = readFile(service, PassportService.EF_DG2)
            val dg11 = readFile(service, PassportService.EF_DG11)
            val dg14 = readFile(service, PassportService.EF_DG14)

            val bundle = ChipBundle(
                ChipBundle.Lds(dg1 = dg1, sod = sod, dg2 = dg2, dg11 = dg11, dg14 = dg14),
            )
            val summary = summarise(dg1, dg2)

            onProgress(ChipReadStage.Done)
            ChipReadResult(bundle = bundle, summary = summary)
        } catch (e: ChipReaderException) {
            throw e
        } catch (e: Exception) {
            throw ChipReaderException.ReadFailed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { service.close() }
        }
    }

    /** Attempt PACE from EF.CardAccess; returns true on success. */
    private fun tryPace(service: PassportService, bacKey: BACKey): Boolean = try {
        val cardAccess = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
        val paceInfo = cardAccess.securityInfos.filterIsInstance<PACEInfo>().firstOrNull()
        if (paceInfo != null) {
            service.doPACE(
                bacKey,
                paceInfo.objectIdentifier,
                PACEInfo.toParameterSpec(paceInfo.parameterId),
                paceInfo.parameterId,
            )
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun readFile(service: PassportService, fid: Short): ByteArray? = try {
        readFully(service.getInputStream(fid))
    } catch (_: Exception) {
        null
    }

    private fun readFully(input: InputStream): ByteArray = input.use { stream ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        out.toByteArray()
    }

    private fun summarise(dg1: ByteArray, dg2: ByteArray?): PassportSummary {
        val info = DG1File(dg1.inputStream()).mrzInfo
        val fullName = listOf(info.secondaryIdentifier, info.primaryIdentifier)
            .map { it.replace("<", " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val photo = dg2?.let { extractPhoto(it) }
        return PassportSummary(
            fullName = fullName,
            documentNumber = info.documentNumber.replace("<", ""),
            nationality = info.nationality.replace("<", ""),
            dateOfBirth = info.dateOfBirth,
            dateOfExpiry = info.dateOfExpiry,
            photoJpeg = photo,
        )
    }

    private fun extractPhoto(dg2: ByteArray): ByteArray? = try {
        val faceInfos = DG2File(dg2.inputStream()).faceInfos
        val faceImageInfo = faceInfos.firstOrNull()?.faceImageInfos?.firstOrNull() ?: return null
        readFully(faceImageInfo.imageInputStream)
    } catch (_: Exception) {
        null
    }

    @Suppress("unused")
    private fun unusedTypeAnchors() {
        // Keep imports for the file types we may parse on-device.
        val ignore: List<Class<*>> = listOf(
            SODFile::class.java, DG11File::class.java, DG14File::class.java,
        )
        ignore.size
    }
}
