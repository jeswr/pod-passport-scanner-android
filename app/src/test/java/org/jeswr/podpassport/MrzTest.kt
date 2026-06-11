package org.jeswr.podpassport

import org.jeswr.podpassport.model.Mrz
import org.jeswr.podpassport.model.MrzKey
import org.jeswr.podpassport.model.MrzParseException
import org.jeswr.podpassport.model.MrzParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MrzTest {
    // ICAO 9303 specimen (Doc 9303 Part 4, Appendix B).
    private val line1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"

    @Test fun checkDigitKnownValues() {
        assertEquals(6, Mrz.checkDigit("L898902C3"))
        assertEquals(2, Mrz.checkDigit("740812"))
        assertEquals(9, Mrz.checkDigit("120415"))
        assertEquals(0, Mrz.checkDigit("<<<<<<<<<<<<<<"))
    }

    @Test fun checkDigitRejectsInvalidCharacters() {
        assertNull(Mrz.checkDigit("ABC 123"))
        assertNull(Mrz.checkDigit("abc"))
    }

    @Test fun parsesSpecimenTd3() {
        val parsed = MrzParser.parseTd3(line1, line2)
        assertEquals("P", parsed.documentCode)
        assertEquals("UTO", parsed.issuingState)
        assertEquals("ERIKSSON", parsed.surname)
        assertEquals("ANNA MARIA", parsed.givenNames)
        assertEquals("ANNA MARIA ERIKSSON", parsed.fullName)
        assertEquals("L898902C3", parsed.documentNumber)
        assertEquals("UTO", parsed.nationality)
        assertEquals("740812", parsed.dateOfBirth)
        assertEquals("F", parsed.sex)
        assertEquals("120415", parsed.dateOfExpiry)
    }

    @Test fun parsesContiguous88() {
        val parsed = MrzParser.parseTd3Contiguous(line1 + line2)
        assertEquals("L898902C3", parsed.documentNumber)
    }

    @Test fun rejectsWrongLength() {
        try {
            MrzParser.parseTd3("P<UTO", line2)
            fail("expected throw")
        } catch (e: MrzParseException) {
            assertTrue(e is MrzParseException.WrongLineLength)
        }
    }

    @Test fun rejectsBadDocumentNumberChecksum() {
        val bad = line2.toCharArray().also { it[9] = '5' }.concatToString()
        try {
            MrzParser.parseTd3(line1, bad)
            fail("expected throw")
        } catch (e: MrzParseException.ChecksumFailed) {
            assertEquals("documentNumber", e.field)
        }
    }

    @Test fun rejectsBadCompositeChecksum() {
        val bad = line2.toCharArray().also { it[43] = '1' }.concatToString()
        try {
            MrzParser.parseTd3(line1, bad)
            fail("expected throw")
        } catch (e: MrzParseException.ChecksumFailed) {
            assertEquals("composite", e.field)
        }
    }

    @Test fun bacKeyStringForSpecimen() {
        val key = MrzKey("L898902C3", "740812", "120415")
        assertEquals("L898902C3" + "6" + "7408122" + "1204159", key.bacKeyString)
    }

    @Test fun bacKeyPadsShortDocumentNumbers() {
        val key = MrzKey("AB12345", "740812", "120415")
        val bac = key.bacKeyString!!
        assertTrue(bac.startsWith("AB12345<<"))
        assertEquals(10 + 7 + 7, bac.length)
    }

    @Test fun bacKeyNullForInvalidInput() {
        assertNull(MrzKey("L898902C3", "74081", "120415").bacKeyString)
        assertNull(MrzKey("L8989?2C3", "740812", "120415").bacKeyString)
    }

    @Test fun normalizeOcrLine() {
        assertEquals("P<UTOERIKSSON<ANNA", Mrz.normalizeOcrLine("p<uto eriksson«anna"))
    }
}
