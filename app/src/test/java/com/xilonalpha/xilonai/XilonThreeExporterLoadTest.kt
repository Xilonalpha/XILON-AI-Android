package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XilonThreeExporterLoadTest {
    @Test fun classicScriptLoadsExporterAfterThree() {
        val html = """<html><head></head><body><script src="https://cdn.example.com/three.min.js"></script><script>const scene=new THREE.Scene()</script></body></html>"""
        val prepared = XilonThreeOffline.prepare(html)
        assertEquals(1, Regex("GLTFExporter.js").findAll(prepared).count())
        assertTrue(prepared.indexOf("three.min.js") < prepared.indexOf("GLTFExporter.js"))
        assertTrue(prepared.indexOf("GLTFExporter.js") < prepared.indexOf("const scene"))
    }

    @Test fun moduleImportsLoadExporterAndImportMapOnce() {
        val html = """<html><head></head><body><script type="module">import {Scene} from 'three';</script></body></html>"""
        val prepared = XilonThreeOffline.prepare(html)
        assertEquals(1, Regex("GLTFExporter.js").findAll(prepared).count())
        assertEquals(1, Regex("type=\"importmap\"").findAll(prepared).count())
        assertTrue(prepared.indexOf("GLTFExporter.js") < prepared.indexOf("type=\"importmap\""))
    }
}
