package org.jeswr.podpassport.model

import android.util.Base64
import org.json.JSONObject

/**
 * The exact payload PUT to the issuer's upload endpoint, and the structure of
 * the issuer-side canonical fixture `emrtd-bundle.json`.
 *
 * ```json
 * {
 *   "format": "icao-9303-lds1",
 *   "lds": {
 *     "dg1": "<b64>", "dg2": "<b64, optional>",
 *     "dg11": "<b64, optional>", "dg14": "<b64, optional>",
 *     "sod": "<b64>"
 *   }
 * }
 * ```
 *
 * Data-group bytes are serialised as **standard, padded** base64 (matching the
 * issuer's `base64` Zod schema `^[A-Za-z0-9+/]+={0,2}$`). Optional data groups
 * are **omitted, never null**.
 */
data class ChipBundle(val lds: Lds) {
    val format: String = LDS_FORMAT

    data class Lds(
        val dg1: ByteArray,
        val sod: ByteArray,
        val dg2: ByteArray? = null,
        val dg11: ByteArray? = null,
        val dg14: ByteArray? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Lds) return false
            return dg1.contentEquals(other.dg1) &&
                sod.contentEquals(other.sod) &&
                contentEqualsNullable(dg2, other.dg2) &&
                contentEqualsNullable(dg11, other.dg11) &&
                contentEqualsNullable(dg14, other.dg14)
        }

        override fun hashCode(): Int {
            var result = dg1.contentHashCode()
            result = 31 * result + sod.contentHashCode()
            result = 31 * result + (dg2?.contentHashCode() ?: 0)
            result = 31 * result + (dg11?.contentHashCode() ?: 0)
            result = 31 * result + (dg14?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Serialise to the fixed contract JSON. Standard padded base64; optional
     * data groups are omitted (the key is absent), never written as `null`.
     */
    fun toJson(): JSONObject {
        val ldsJson = JSONObject()
            .put("dg1", b64(lds.dg1))
        lds.dg2?.let { ldsJson.put("dg2", b64(it)) }
        lds.dg11?.let { ldsJson.put("dg11", b64(it)) }
        lds.dg14?.let { ldsJson.put("dg14", b64(it)) }
        ldsJson.put("sod", b64(lds.sod))
        return JSONObject()
            .put("format", format)
            .put("lds", ldsJson)
    }

    fun toJsonString(): String = toJson().toString()

    /**
     * Extract the 88-character TD3 MRZ string from raw DG1 bytes
     * (`61 L 5F1F L <mrz>`). Used to populate the review screen.
     */
    fun mrzFromDg1(): String? {
        if (lds.dg1.size < 88) return null
        val tail = lds.dg1.copyOfRange(lds.dg1.size - 88, lds.dg1.size)
        return String(tail, Charsets.US_ASCII)
    }

    companion object {
        const val LDS_FORMAT = "icao-9303-lds1"

        /** Standard, padded base64 — no line wrapping. */
        private fun b64(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)

        private fun decode(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

        private fun contentEqualsNullable(a: ByteArray?, b: ByteArray?): Boolean =
            if (a == null || b == null) a === b || (a == null && b == null) else a.contentEquals(b)

        /** Parse a bundle from the contract JSON (used to load the sample fixture). */
        fun fromJson(json: JSONObject): ChipBundle {
            require(json.optString("format") == LDS_FORMAT) {
                "Unexpected bundle format: ${json.optString("format")}"
            }
            val lds = json.getJSONObject("lds")
            return ChipBundle(
                Lds(
                    dg1 = decode(lds.getString("dg1")),
                    sod = decode(lds.getString("sod")),
                    dg2 = lds.optString("dg2").takeIf { lds.has("dg2") && it.isNotEmpty() }?.let(::decode),
                    dg11 = lds.optString("dg11").takeIf { lds.has("dg11") && it.isNotEmpty() }?.let(::decode),
                    dg14 = lds.optString("dg14").takeIf { lds.has("dg14") && it.isNotEmpty() }?.let(::decode),
                ),
            )
        }

        fun fromJsonString(s: String): ChipBundle = fromJson(JSONObject(s))
    }
}

/** Human-readable summary of what was read, shown on the review screen before upload. */
data class PassportSummary(
    val fullName: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String, // YYMMDD
    val dateOfExpiry: String, // YYMMDD
    val photoJpeg: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PassportSummary) return false
        return fullName == other.fullName &&
            documentNumber == other.documentNumber &&
            nationality == other.nationality &&
            dateOfBirth == other.dateOfBirth &&
            dateOfExpiry == other.dateOfExpiry &&
            (photoJpeg?.contentEquals(other.photoJpeg ?: ByteArray(0)) ?: (other.photoJpeg == null))
    }

    override fun hashCode(): Int {
        var result = fullName.hashCode()
        result = 31 * result + documentNumber.hashCode()
        result = 31 * result + nationality.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + dateOfExpiry.hashCode()
        result = 31 * result + (photoJpeg?.contentHashCode() ?: 0)
        return result
    }
}

/** Everything a [org.jeswr.podpassport.service.ChipReader] hands back after a successful read. */
data class ChipReadResult(
    val bundle: ChipBundle,
    val summary: PassportSummary,
)
