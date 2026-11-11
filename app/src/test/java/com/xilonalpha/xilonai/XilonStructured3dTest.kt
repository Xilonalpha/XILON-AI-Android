package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonStructured3dTest {
    @Test fun createsLocalFaceSceneFromCompactJson() {
        val result = XilonStructured3d.html("""{"type":"face","color":"#abcdef"}""")
        assertNotNull(result)
        assertTrue(result!!.contains("new THREE.SphereGeometry"))
        assertTrue(result.contains("three.min.js"))
        assertTrue(result.contains("#abcdef"))
    }

    @Test fun rejectsUnsupportedTypesAndBoundsDimensions() {
        assertNull(XilonStructured3d.html("""{"type":"script"}"""))
        val result = XilonStructured3d.extract("""{"type":"cube","width":999,"height":0.1}""")!!
        assertTrue(result.contains("2.0"))
        assertTrue(result.contains("0.5"))
    }

    @Test fun doesNotEmbedArbitraryJavascriptFromModel() {
        val html = XilonStructured3d.html("""{"type":"sphere","color":"#ffffff"} <script>alert(1)</script>""")!!
        assertFalse(html.contains("alert(1)"))
    }
    @Test fun streamingWaitsForAllFaceFields() {
        val partial = """{"type":"face","color":"#d8a47f","eyeColor":"#46352c"}"""
        assertNull(XilonStructured3d.extractComplete(partial))
        val complete = """{"type":"face","color":"#d8a47f","eyeColor":"#46352c","hairColor":"#35251e","width":1.0,"height":1.0}"""
        assertNotNull(XilonStructured3d.extractComplete(complete))
    }

    @Test fun streamingRejectsIncompleteObject() {
        assertNull(XilonStructured3d.extractComplete("""{"type":"face","color":"#d8a47f","eyeColor":"#46352c","hairColor":"#35251e","width":1.0,"height":1.0"""))
    }

}
