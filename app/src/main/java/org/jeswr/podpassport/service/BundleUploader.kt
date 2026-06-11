package org.jeswr.podpassport.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jeswr.podpassport.model.ChipBundle
import org.jeswr.podpassport.model.IssuerSession
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class UploadException(message: String) : Exception(message) {
    val displayMessage: String get() = message ?: "Upload failed"

    /** 401 — the bearer secret was rejected. Not retried. */
    object InvalidSecret : UploadException(
        "The issuer rejected this upload (invalid secret). Rescan the QR code in your browser and try again.",
    )

    /** 410 — the issuer session expired. Not retried; rescan the QR code. */
    object SessionExpired : UploadException(
        "This issuer session has expired. Refresh the page in your browser and scan the new QR code.",
    )

    /** Unexpected HTTP status after exhausting retries. */
    data class ServerError(val status: Int) : UploadException(
        "The issuer returned an unexpected response (HTTP $status). Please try again.",
    )

    /** Network-level failure after exhausting retries. */
    data class Network(val detail: String) : UploadException(
        "Could not reach the issuer: $detail",
    )
}

/**
 * Abstraction over the upload so the flow is testable and runs without a server
 * against [MockBundleUploader].
 */
interface BundleUploader {
    /**
     * Uploads the chip bundle to the issuer session's endpoint.
     * Returns normally on HTTP 204/200; throws [UploadException] otherwise.
     */
    suspend fun upload(bundle: ChipBundle, session: IssuerSession)
}

/**
 * Implements the fixed hand-off contract:
 * `PUT <endpoint>` with `Authorization: Bearer <secret>`, `Content-Type:
 * application/json`, and the JSON chip bundle as body. 204 = success;
 * 401/410 are terminal; network errors and other statuses (5xx etc.) are
 * retried with exponential backoff. PUT is idempotent, so retry is safe.
 *
 * Uses plain [HttpURLConnection] — no SDK dependency — so this is not blocked
 * on `jeswr/solid-kotlin`.
 */
class HttpBundleUploader(
    private val maxAttempts: Int = 3,
    /** Base backoff; attempt n waits baseDelay * 2^(n-1). Tests shrink this. */
    private val baseDelayMs: Long = 500,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : BundleUploader {

    override suspend fun upload(bundle: ChipBundle, session: IssuerSession) {
        val body = bundle.toJsonString().toByteArray(Charsets.UTF_8)
        var lastError: UploadException = UploadException.ServerError(-1)

        for (attempt in 1..maxOf(1, maxAttempts)) {
            if (attempt > 1) delay(baseDelayMs * (1L shl (attempt - 2)))
            try {
                when (val status = put(session, body)) {
                    200, 204 -> return
                    401 -> throw UploadException.InvalidSecret
                    410 -> throw UploadException.SessionExpired
                    else -> {
                        lastError = UploadException.ServerError(status)
                        continue // retry 5xx and anything else unexpected
                    }
                }
            } catch (e: UploadException) {
                throw e
            } catch (e: IOException) {
                lastError = UploadException.Network(e.message ?: e.javaClass.simpleName)
                continue
            }
        }
        throw lastError
    }

    private suspend fun put(session: IssuerSession, body: ByteArray): Int =
        withContext(Dispatchers.IO) {
            val connection = URL(session.endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.doOutput = true
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("Authorization", "Bearer ${session.secret}")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                // Drain so the connection can be pooled/closed cleanly.
                (if (code in 200..399) connection.inputStream else connection.errorStream)
                    ?.use { it.readBytes() }
                code
            } finally {
                connection.disconnect()
            }
        }
}

/** Test / preview uploader. */
class MockBundleUploader(
    private val error: UploadException? = null,
    private val delayMs: Long = 300,
) : BundleUploader {
    override suspend fun upload(bundle: ChipBundle, session: IssuerSession) {
        if (delayMs > 0) delay(delayMs)
        error?.let { throw it }
    }
}
