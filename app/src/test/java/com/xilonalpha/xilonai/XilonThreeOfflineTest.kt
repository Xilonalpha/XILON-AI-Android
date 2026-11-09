package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XilonThreeOfflineTest {
    @Test fun replacesClassicThreeCdnWithLocalAsset() {
        val html = "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/three.js/r150/three.min.js\"></script>"
        val result = XilonThreeOffline.prepare(html)
        assertTrue(result.contains("<script src=\"https://xilon-preview.invalid/three.min.js\"></script>"))
        assertTrue(result.contains("https://xilon-preview.invalid/GLTFExporter.js"))
    }

    @Test fun doesNotRewriteArbitraryScripts() {
        val html = "<script src=\"https://example.com/tracker.js\"></script>"
        val prepared = XilonThreeOffline.prepare(html)
        assertTrue(prepared.contains(html))
        assertTrue(prepared.contains("xilon-preview-error"))
    }

    @Test fun demoIncludesLocalThree() {
        assertTrue(XilonThreeDemo.html.contains("xilon-preview.invalid/three.min.js"))
    }
    @Test fun diagnosticsAreInsertedInsideHead() {
        val prepared = XilonThreeOffline.prepare("<html><head><title>Demo</title></head><body></body></html>")
        val head = prepared.indexOf("<head>")
        val diagnostics = prepared.indexOf("xilon-preview-error")
        val closingHead = prepared.indexOf("</head>")
        assertTrue(head >= 0 && diagnostics > head && diagnostics < closingHead)
    }

    @Test fun mixedClassicAndModuleLoadsThreeBeforeExporter() {
        val html = "<html><head></head><body><script src=\"https://cdn.example.com/three.min.js\"></script><script type=\"module\">import { Scene } from 'three';</script></body></html>"
        val prepared = XilonThreeOffline.prepare(html)
        val three = prepared.indexOf("<script src=\"https://xilon-preview.invalid/three.min.js\"></script>")
        val exporter = prepared.indexOf("https://xilon-preview.invalid/GLTFExporter.js")
        assertTrue(three >= 0 && exporter > three)
    }

}
