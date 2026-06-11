package org.jeswr.podpassport

import org.jeswr.podpassport.model.QrPayloadDecoder
import org.jeswr.podpassport.model.QrPayloadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrPayloadTest {
    @Test fun decodesValidPayload() {
        val json = """{"v":1,"endpoint":"https://issuer.example/api/emrtd/sessions/abc123","sessionId":"abc123","secret":"s3cr3t"}"""
        val session = QrPayloadDecoder.decode(json)
        assertEquals("https://issuer.example/api/emrtd/sessions/abc123", session.endpoint)
        assertEquals("abc123", session.sessionId)
        assertEquals("s3cr3t", session.secret)
    }

    @Test fun allowsHttpForLocalDevelopment() {
        val json = """{"v":1,"endpoint":"http://localhost:3000/api/emrtd/sessions/x","sessionId":"x","secret":"y"}"""
        QrPayloadDecoder.decode(json) // no throw
    }

    @Test fun rejectsNonJson() {
        try {
            QrPayloadDecoder.decode("https://example.com/not-our-qr")
            fail("expected throw")
        } catch (e: QrPayloadException) {
            assertTrue(e is QrPayloadException.NotJson)
        }
    }

    @Test fun rejectsUnsupportedVersion() {
        val json = """{"v":2,"endpoint":"https://e.example/u","sessionId":"a","secret":"b"}"""
        try {
            QrPayloadDecoder.decode(json)
            fail("expected throw")
        } catch (e: QrPayloadException.UnsupportedVersion) {
            assertEquals(2, e.version)
        }
    }

    @Test fun rejectsMissingFields() {
        val json = """{"v":1,"endpoint":"https://e.example/u","sessionId":"a"}"""
        try {
            QrPayloadDecoder.decode(json)
            fail("expected throw")
        } catch (e: QrPayloadException.MissingField) {
            assertEquals("secret", e.field)
        }
    }

    @Test fun rejectsInvalidEndpointScheme() {
        val json = """{"v":1,"endpoint":"ftp://e.example/u","sessionId":"a","secret":"b"}"""
        try {
            QrPayloadDecoder.decode(json)
            fail("expected throw")
        } catch (e: QrPayloadException.InvalidEndpoint) {
            assertEquals("ftp://e.example/u", e.value)
        }
    }

    @Test fun rejectsRelativeEndpoint() {
        val json = """{"v":1,"endpoint":"/api/emrtd/sessions/abc","sessionId":"a","secret":"b"}"""
        try {
            QrPayloadDecoder.decode(json)
            fail("expected throw")
        } catch (e: QrPayloadException.InvalidEndpoint) {
            assertEquals("/api/emrtd/sessions/abc", e.value)
        }
    }

    @Test fun manualEntryRoutesThroughSameValidation() {
        val session = QrPayloadDecoder.fromManualEntry(
            "  https://issuer.example/u  ", " sess ", " sec ",
        )
        assertEquals("https://issuer.example/u", session.endpoint)
        assertEquals("sess", session.sessionId)
        assertEquals("sec", session.secret)
    }
}
