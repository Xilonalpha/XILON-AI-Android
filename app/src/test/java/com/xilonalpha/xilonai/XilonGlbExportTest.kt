package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class XilonGlbExportTest {
    @Test fun writesValidGlbHeaderAndChunks() {
        val bytes = XilonGlbExport.fromObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buffer.int)
        assertEquals(2, buffer.int)
        assertEquals(bytes.size, buffer.int)
        val jsonSize = buffer.int
        assertEquals(0x4E4F534A, buffer.int)
        val jsonBytes = ByteArray(jsonSize)
        buffer.get(jsonBytes)
        assertTrue(String(jsonBytes, Charsets.UTF_8).contains("\"POSITION\""))
        assertEquals(0x004E4942, bytes.let {
            ByteBuffer.wrap(it, 12 + 8 + jsonSize + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFaceReferences() {
        XilonGlbExport.fromObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 99")
    }
}
