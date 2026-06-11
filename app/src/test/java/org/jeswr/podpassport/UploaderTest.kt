package org.jeswr.podpassport

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.IssuerSession
import org.jeswr.podpassport.service.HttpBundleUploader
import org.jeswr.podpassport.service.UploadException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UploaderTest {
    private lateinit var server: MockWebServer

    private val bundle = ChipBundle(
        ChipBundle.Lds(
            dg1 = "dg1-bytes".toByteArray(),
            sod = "sod-bytes".toByteArray(),
            dg2 = "dg2-bytes".toByteArray(),
        ),
    )

    private fun session() = IssuerSession(
        endpoint = server.url("/api/emrtd/sessions/abc123").toString(),
        sessionId = "abc123",
        secret = "topsecret",
    )

    private fun uploader() = HttpBundleUploader(maxAttempts = 3, baseDelayMs = 1)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun successfulUploadSendsContractRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        uploader().upload(bundle, session())

        assertEquals(1, server.requestCount)
        val request: RecordedRequest = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/emrtd/sessions/abc123", request.path)
        assertEquals("Bearer topsecret", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))

        val json = JSONObject(request.body.readUtf8())
        assertEquals("icao-9303-lds1", json.getString("format"))
        val lds = json.getJSONObject("lds")
        // Standard padded base64 of the byte values.
        assertEquals(android.util.Base64.encodeToString("dg1-bytes".toByteArray(), android.util.Base64.NO_WRAP), lds.getString("dg1"))
        assertEquals(android.util.Base64.encodeToString("dg2-bytes".toByteArray(), android.util.Base64.NO_WRAP), lds.getString("dg2"))
        assertEquals(android.util.Base64.encodeToString("sod-bytes".toByteArray(), android.util.Base64.NO_WRAP), lds.getString("sod"))
        assertFalse("absent data groups must be omitted, not null", lds.has("dg11"))
    }

    @Test fun status401IsTerminalInvalidSecret() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            uploader().upload(bundle, session())
            fail("expected throw")
        } catch (e: UploadException) {
            assertTrue(e is UploadException.InvalidSecret)
        }
        assertEquals("401 must not be retried", 1, server.requestCount)
    }

    @Test fun status410IsTerminalSessionExpired() = runTest {
        server.enqueue(MockResponse().setResponseCode(410))
        try {
            uploader().upload(bundle, session())
            fail("expected throw")
        } catch (e: UploadException) {
            assertTrue(e is UploadException.SessionExpired)
        }
        assertEquals("410 must not be retried", 1, server.requestCount)
    }

    @Test fun retriesServerErrorsThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(204))
        uploader().upload(bundle, session())
        assertEquals(3, server.requestCount)
    }

    @Test fun exhaustedRetriesThrowsLastServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            uploader().upload(bundle, session())
            fail("expected throw")
        } catch (e: UploadException.ServerError) {
            assertEquals(503, e.status)
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun retriesNetworkErrorThenSucceeds() = runTest {
        // First response disconnects mid-body (network-level failure), then 204.
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(204))
        uploader().upload(bundle, session())
        assertEquals(2, server.requestCount)
    }

    @Test fun exhaustedNetworkRetriesThrowsNetworkError() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        }
        try {
            uploader().upload(bundle, session())
            fail("expected throw")
        } catch (e: UploadException) {
            assertTrue("expected Network, got $e", e is UploadException.Network)
        }
        assertEquals(3, server.requestCount)
    }
}
