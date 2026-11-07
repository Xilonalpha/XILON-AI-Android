package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XilonOutputTest {
    @Test fun reasoningIsHiddenUntilClosingTag() {
        assertEquals("", XilonResponseBoundary.clean("<think>Calculating"))
        assertEquals("Salut!", XilonResponseBoundary.clean("<think>Calculating</think>Salut!"))
    }

    @Test fun extractsOnlyCompleteHtml() {
        assertEquals("<!DOCTYPE html><html><body>3D</body></html>",
            XilonHtmlExport.extract("```html\n<!DOCTYPE html><html><body>3D</body></html>\n```"))
        assertNull(XilonHtmlExport.extract("<!DOCTYPE html><html><body>incomplet"))
    }
}
