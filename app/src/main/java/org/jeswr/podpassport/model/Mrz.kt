package org.jeswr.podpassport.model

/**
 * ICAO 9303 MRZ helpers: check digits, the BAC/PACE key, and a TD3 (passport
 * booklet, 2 x 44 chars) parser.
 */
object Mrz {
    private val WEIGHTS = intArrayOf(7, 3, 1)

    /**
     * ICAO 9303 check digit: weights 7-3-1 repeating; digits as value,
     * A=10..Z=35, `<` = 0; result is mod 10. Returns null for any other char.
     */
    fun checkDigit(field: CharSequence): Int? {
        var total = 0
        for ((i, c) in field.withIndex()) {
            val value = when (c) {
                '<' -> 0
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> return null
            }
            total += value * WEIGHTS[i % 3]
        }
        return total % 10
    }

    fun checkDigitMatches(field: CharSequence, digit: Char): Boolean {
        val expected = checkDigit(field) ?: return false
        return digit.digitToIntOrNull() == expected
    }

    /** Allowed MRZ alphabet. */
    val ALLOWED = ('A'..'Z').toSet() + ('0'..'9').toSet() + '<'

    /**
     * Cleans an OCR candidate line: uppercase, strip spaces, map common
     * misreads of the filler character.
     */
    fun normalizeOcrLine(line: String): String =
        line.uppercase()
            .replace(" ", "")
            .replace('«', '<')
            .replace('≤', '<')
}

/**
 * The three fields that derive the Basic Access Control / PACE key.
 * Dates are MRZ-format `YYMMDD`.
 */
data class MrzKey(
    val documentNumber: String,
    val dateOfBirth: String, // YYMMDD
    val dateOfExpiry: String, // YYMMDD
) {
    /**
     * The key string the chip reader expects: document number padded to 9 with
     * `<` + check digit, DOB + check digit, expiry + check digit.
     */
    val bacKeyString: String?
        get() {
            val doc = documentNumber.uppercase().padEnd(maxOf(9, documentNumber.length), '<')
            val docCd = Mrz.checkDigit(doc) ?: return null
            val dobCd = Mrz.checkDigit(dateOfBirth) ?: return null
            val expCd = Mrz.checkDigit(dateOfExpiry) ?: return null
            if (dateOfBirth.length != 6 || dateOfExpiry.length != 6) return null
            return "$doc$docCd$dateOfBirth$dobCd$dateOfExpiry$expCd"
        }
}

/** Parsed fields from a TD3 MRZ (the two printed lines in a passport booklet). */
data class ParsedMrz(
    val documentCode: String,
    val issuingState: String,
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String, // YYMMDD
    val sex: String,
    val dateOfExpiry: String, // YYMMDD
) {
    val key: MrzKey
        get() = MrzKey(documentNumber, dateOfBirth, dateOfExpiry)

    val fullName: String
        get() = listOf(givenNames, surname).filter { it.isNotEmpty() }.joinToString(" ")
}

sealed class MrzParseException(message: String) : Exception(message) {
    object WrongLineLength : MrzParseException("MRZ lines must be 44 characters")
    object InvalidCharacters : MrzParseException("MRZ contains invalid characters")
    data class ChecksumFailed(val field: String) : MrzParseException("MRZ checksum failed: $field")
}

object MrzParser {
    /**
     * Parses a TD3 MRZ from its two 44-character lines, verifying the
     * document-number, date-of-birth, expiry and composite check digits.
     */
    fun parseTd3(line1: String, line2: String): ParsedMrz {
        if (line1.length != 44 || line2.length != 44) throw MrzParseException.WrongLineLength
        if (!line1.all { it in Mrz.ALLOWED } || !line2.all { it in Mrz.ALLOWED }) {
            throw MrzParseException.InvalidCharacters
        }

        val documentCode = line1.substring(0, 2).replace("<", "")
        val issuingState = line1.substring(2, 5).replace("<", "")
        val nameField = line1.substring(5, 44)
        val nameParts = nameField.split("<<")
        val surname = (nameParts.getOrNull(0) ?: "").replace("<", " ").trim()
        val givenNames = (nameParts.getOrNull(1) ?: "").replace("<", " ").trim()

        val documentNumberRaw = line2.substring(0, 9)
        if (!Mrz.checkDigitMatches(documentNumberRaw, line2[9])) {
            throw MrzParseException.ChecksumFailed("documentNumber")
        }
        val nationality = line2.substring(10, 13).replace("<", "")
        val dateOfBirth = line2.substring(13, 19)
        if (!Mrz.checkDigitMatches(dateOfBirth, line2[19])) {
            throw MrzParseException.ChecksumFailed("dateOfBirth")
        }
        val sex = line2.substring(20, 21).replace("<", "")
        val dateOfExpiry = line2.substring(21, 27)
        if (!Mrz.checkDigitMatches(dateOfExpiry, line2[27])) {
            throw MrzParseException.ChecksumFailed("dateOfExpiry")
        }
        // Composite check digit covers positions 0-9, 13-19 and 21-42.
        val composite = line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43)
        if (!Mrz.checkDigitMatches(composite, line2[43])) {
            throw MrzParseException.ChecksumFailed("composite")
        }

        return ParsedMrz(
            documentCode = documentCode,
            issuingState = issuingState,
            surname = surname,
            givenNames = givenNames,
            documentNumber = documentNumberRaw.replace("<", ""),
            nationality = nationality,
            dateOfBirth = dateOfBirth,
            sex = sex,
            dateOfExpiry = dateOfExpiry,
        )
    }

    /**
     * Extracts the two TD3 lines from a single 88-character string
     * (e.g. the MRZ body inside DG1).
     */
    fun parseTd3Contiguous(contiguous: String): ParsedMrz {
        if (contiguous.length != 88) throw MrzParseException.WrongLineLength
        return parseTd3(contiguous.substring(0, 44), contiguous.substring(44))
    }
}
