package com.xilonalpha.xilonai

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XilonObjExportTest {
    @Test fun extractsGeneratedTriangleMesh() {
        val answer = """
Here is your mesh:
```obj
o Triangle
v 0 0 0
v 1 0 0
v 0 1 0
f 1 2 3
```
""".trimIndent()
        val obj = XilonObjExport.extract(answer)
        assertTrue(obj!!.contains("f 1 2 3"))
        assertTrue(obj.contains("v 0 1 0"))
    }

    @Test fun doesNotExportIncompleteMesh() {
        assertNull(XilonObjExport.extract("v 0 0 0\nv 1 0 0\nv 0 1 0"))
    }
}
