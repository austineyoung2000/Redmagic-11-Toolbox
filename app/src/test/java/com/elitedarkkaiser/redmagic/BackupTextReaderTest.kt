package com.elitedarkkaiser.redmagic

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTextReaderTest {
    @Test fun acceptsExactLimit() {
        assertEquals("12345", BackupTextReader.read(StringReader("12345"),5))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedInput() {
        BackupTextReader.read(StringReader("123456"),5)
    }
    @Test fun handlesEmptyAndMultiChunkInput() {
        assertEquals("", BackupTextReader.read(StringReader(""),5))
        val raw="a".repeat(20000)
        assertEquals(raw,BackupTextReader.read(StringReader(raw),20000))
    }
}
