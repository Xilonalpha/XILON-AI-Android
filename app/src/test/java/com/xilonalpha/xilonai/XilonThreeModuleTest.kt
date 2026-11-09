package com.xilonalpha.xilonai

import org.junit.Assert.assertTrue
import org.junit.Test

class XilonThreeModuleTest {
    @Test fun bareModuleImportsGetLocalImportMap() {
        val html = "<html><head></head><body><script type=\"module\">import {Scene} from 'three';</script></body></html>"
        val prepared = XilonThreeOffline.prepare(html)
        assertTrue(prepared.contains("type=\"importmap\""))
        assertTrue(prepared.contains("three-module-adapter.js"))
        assertTrue(prepared.contains("three.min.js"))
    }

    @Test fun cdnModuleImportsBecomeBareThreeImports() {
        val html = "<html><head></head><body><script type=\"module\">import * as THREE from 'https://cdn.jsdelivr.net/npm/three@0.150.0/build/three.module.js';</script></body></html>"
        val prepared = XilonThreeOffline.prepare(html)
        assertTrue(prepared.contains("from 'three'"))
        assertTrue(prepared.contains("three-module-adapter.js"))
    }
}
