package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XilonThreeOfflineTest {
    @Test fun replacesClassicThreeCdnWithLocalAsset() {
        val html = "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/three.js/r150/three.min.js\"></script>"
        assertEquals("<script src=\"https://xilon-preview.invalid/three.min.js\"></script>",
            XilonThreeOffline.prepare(html))
    }

    @Test fun doesNotRewriteArbitraryScripts() {
        val html = "<script src=\"https://example.com/tracker.js\"></script>"
        assertEquals(html, XilonThreeOffline.prepare(html))
    }

    @Test fun demoIncludesLocalThree() {
        assertTrue(XilonThreeDemo.html.contains("xilon-preview.invalid/three.min.js"))
    }
}
