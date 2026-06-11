package org.jeswr.podpassport.model

import org.json.JSONObject

/**
 * An upload session handed to the app by the Credential Issuer web app, either
 * via QR code or manual entry.
 *
 * QR payload contract (fixed, version 1):
 * `{"v":1,"endpoint":"<absolute uploadUrl>","sessionId":"...","secret":"..."}`
 */
data class IssuerSession(
    val endpoint: String,
    val sessionId: String,
    val secret: String,
)

sealed class QrPayloadException(message: String) : Exception(message) {
    val displayMessage: String get() = message ?: "Invalid issuer code"

    object NotJson : QrPayloadException(
        "That QR code is not a Pod Passport issuer code.",
    )

    data class UnsupportedVersion(val version: Int) : QrPayloadException(
        "Unsupported issuer QR version $version. This app understands version 1 — please update the app.",
    )

    data class MissingField(val field: String) : QrPayloadException(
        "The issuer QR code is missing the \"$field\" field.",
    )

    data class InvalidEndpoint(val value: String) : QrPayloadException(
        "The issuer QR code contains an invalid upload URL: $value",
    )
}

object QrPayloadDecoder {
    /** Decodes the JSON string carried in the issuer QR code. */
    fun decode(string: String): IssuerSession {
        val json = try {
            JSONObject(string)
        } catch (_: Exception) {
            throw QrPayloadException.NotJson
        }

        // `v` must be present and numeric; anything else is "not our QR".
        if (!json.has("v")) throw QrPayloadException.NotJson
        val version = json.optInt("v", Int.MIN_VALUE)
        if (version == Int.MIN_VALUE) throw QrPayloadException.NotJson
        if (version != 1) throw QrPayloadException.UnsupportedVersion(version)

        val endpoint = json.optString("endpoint").takeIf { it.isNotEmpty() }
            ?: throw QrPayloadException.MissingField("endpoint")
        val sessionId = json.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: throw QrPayloadException.MissingField("sessionId")
        val secret = json.optString("secret").takeIf { it.isNotEmpty() }
            ?: throw QrPayloadException.MissingField("secret")

        if (!isValidEndpoint(endpoint)) throw QrPayloadException.InvalidEndpoint(endpoint)

        return IssuerSession(endpoint = endpoint, sessionId = sessionId, secret = secret)
    }

    /** Build the same payload manual entry produces, then validate via [decode]. */
    fun fromManualEntry(endpoint: String, sessionId: String, secret: String): IssuerSession {
        val json = JSONObject()
            .put("v", 1)
            .put("endpoint", endpoint.trim())
            .put("sessionId", sessionId.trim())
            .put("secret", secret.trim())
        return decode(json.toString())
    }

    /**
     * `http` is permitted for local-development issuers; everything must be an
     * absolute URL with a host. Avoids `java.net.URL` (no protocol handler
     * issues) by validating the scheme/authority shape directly.
     */
    private fun isValidEndpoint(endpoint: String): Boolean {
        val uri = try {
            java.net.URI(endpoint)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        return !uri.host.isNullOrEmpty()
    }
}
