package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonCoreTest {
    @Test fun codeTaskIsDetectedOffline() {
        assertEquals(XilonCore.Task.CODE, XilonCore.classify("Scrie cod Kotlin pentru Android").task)
    }
    @Test fun translationTaskIsDetectedOffline() {
        assertEquals(XilonCore.Task.TRANSLATION, XilonCore.classify("Tradu textul în română").task)
    }
    @Test fun normalChatHasNoForcedSpecialization() {
        assertEquals(XilonCore.Task.CHAT, XilonCore.classify("Salut, ce faci?").task)
    }
    @Test fun coderHintPrefersCoderFilename() {
        val selected = XilonCore.suggestModel(
            XilonCore.Task.CODE,
            listOf("generic-instruct-Q4_K_M.gguf", "my-coder-Q4_K_M.gguf")
        )
        assertEquals("my-coder-Q4_K_M.gguf", selected)
    }
    @Test fun auxiliaryFilesAreNotSuggested() {
        assertNull(XilonCore.suggestModel(XilonCore.Task.CHAT, listOf("imatrix.gguf")))
    }
}
