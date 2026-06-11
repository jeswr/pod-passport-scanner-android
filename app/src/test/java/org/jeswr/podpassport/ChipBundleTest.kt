package org.jeswr.podpassport

import androidx.test.core.app.ApplicationProvider
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.MrzParser
import org.jeswr.podpassport.service.SampleBundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChipBundleTest {

    /**
     * The exact base64 of DG1 in the canonical issuer fixture
     * `apps/issuer/test/fixtures/emrtd-bundle.json` — asserted as a known
     * value so an accidental re-encoding (e.g. URL-safe / unpadded base64)
     * breaks the contract loudly.
     */
    private val knownDg1Base64 =
        "YVtfH1hQPFVUT0RPRTw8SkFORTw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PEw4OTg5MDJDMzZVVE84NTAzMTUwRjMyMDEwMTU8PDw8PDw8PDw8PDw8PDA4"

    @Test fun encodesToContractJsonWithStandardPaddedBase64() {
        val bundle = ChipBundle(
            ChipBundle.Lds(
                dg1 = byteArrayOf(0x61, 0x02, 0x5F, 0x1F),
                sod = byteArrayOf(0x77, 0x01, 0x00),
            ),
        )
        val json = JSONObject(bundle.toJsonString())
        assertEquals("icao-9303-lds1", json.getString("format"))
        val lds = json.getJSONObject("lds")
        assertEquals("YQJfHw==", lds.getString("dg1"))
        assertEquals("dwEA", lds.getString("sod"))
        assertFalse("optional dg2 must be omitted, not null", lds.has("dg2"))
        assertFalse(lds.has("dg11"))
        assertFalse(lds.has("dg14"))
    }

    @Test fun roundTripsThroughJson() {
        val bundle = ChipBundle(
            ChipBundle.Lds(
                dg1 = "dg1".toByteArray(),
                sod = "sod".toByteArray(),
                dg2 = "dg2".toByteArray(),
                dg11 = "dg11".toByteArray(),
                dg14 = "dg14".toByteArray(),
            ),
        )
        val decoded = ChipBundle.fromJsonString(bundle.toJsonString())
        assertEquals(bundle, decoded)
    }

    @Test fun sampleFixtureMatchesIssuerContractByteForByte() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bundle = SampleBundle.load(context)

        assertEquals("icao-9303-lds1", bundle.format)
        assertNotNull(bundle.lds.dg2)
        assertNotNull(bundle.lds.dg11)
        assertNotNull(bundle.lds.dg14)
        assertFalse(bundle.lds.sod.isEmpty())

        // The re-serialised DG1 base64 must equal the issuer-side fixture value.
        val reserialised = JSONObject(bundle.toJsonString()).getJSONObject("lds").getString("dg1")
        assertEquals(knownDg1Base64, reserialised)
    }

    @Test fun sampleFixtureDg1ParsesToJaneDoe() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bundle = SampleBundle.load(context)
        val mrz = bundle.mrzFromDg1()!!
        val parsed = MrzParser.parseTd3Contiguous(mrz)
        assertEquals("DOE", parsed.surname)
        assertEquals("JANE", parsed.givenNames)
        assertEquals("L898902C3", parsed.documentNumber)
    }

    @Test fun sampleFixtureDg1IsValidTlv() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dg1 = SampleBundle.load(context).lds.dg1.map { it.toInt() and 0xFF }
        assertEquals(0x61, dg1[0]) // DG1 outer tag
        assertEquals(0x5F, dg1[2])
        assertEquals(0x1F, dg1[3])
        assertEquals(88, dg1[4]) // TD3 MRZ length
        assertEquals(5 + 88, dg1.size)
    }

    @Test fun mrzFromDg1NullForShortBytes() {
        val bundle = ChipBundle(ChipBundle.Lds(dg1 = ByteArray(10), sod = ByteArray(3)))
        assertNull(bundle.mrzFromDg1())
    }

    @Test fun fromJsonRejectsWrongFormat() {
        val bad = """{"format":"other","lds":{"dg1":"QQ==","sod":"QQ=="}}"""
        try {
            ChipBundle.fromJsonString(bad)
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
