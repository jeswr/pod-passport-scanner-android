package org.jeswr.podpassport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.IssuerSession
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.service.ChipReadStage
import org.jeswr.podpassport.service.MockBundleUploader
import org.jeswr.podpassport.service.MockChipReader
import org.jeswr.podpassport.service.SampleBundle
import org.jeswr.podpassport.service.UploadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockServiceTest {

    @Test fun mockChipReaderEmitsAllStagesAndParsesSample() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = MockChipReader(SampleBundle.load(context), stageDelayMs = 0)
        val stages = mutableListOf<ChipReadStage>()

        val result = reader.readChip(
            MrzKey("L898902C3", "740812", "120415"),
        ) { stages.add(it) }

        assertEquals("JANE DOE", result.summary.fullName)
        assertEquals("L898902C3", result.summary.documentNumber)
        assertEquals(ChipBundle.LDS_FORMAT, result.bundle.format)
        assertNotNull(result.bundle.lds.dg2)

        assertTrue(stages.contains(ChipReadStage.WaitingForTag))
        assertTrue(stages.contains(ChipReadStage.Authenticating))
        assertTrue(stages.contains(ChipReadStage.ReadingData))
        assertEquals(ChipReadStage.Done, stages.last())
    }

    @Test fun mockUploaderSuccess() = runTest {
        MockBundleUploader(delayMs = 0).upload(sampleBundle(), sampleSession())
    }

    @Test fun mockUploaderFailurePropagates() = runTest {
        val uploader = MockBundleUploader(error = UploadException.InvalidSecret, delayMs = 0)
        try {
            uploader.upload(sampleBundle(), sampleSession())
            fail("expected throw")
        } catch (e: UploadException) {
            assertTrue(e is UploadException.InvalidSecret)
        }
    }

    private fun sampleBundle() =
        ChipBundle(ChipBundle.Lds(dg1 = "dg1".toByteArray(), sod = "sod".toByteArray()))

    private fun sampleSession() = IssuerSession(
        endpoint = "https://issuer.example/api/emrtd/sessions/x",
        sessionId = "x",
        secret = "y",
    )
}
