package com.xilonalpha.xilonai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class XilonGlbMaterialTest {
    private fun json(obj: String): JSONObject {
        val bytes = XilonGlbExport.fromObj(obj)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        val size = buffer.int
        buffer.int
        val data = ByteArray(size)
        buffer.get(data)
        return JSONObject(String(data, Charsets.UTF_8))
    }

    @Test fun singleMaterialColorIsPreserved() {
        val gltf = json("# xilon_base_color 1 0.25 0.5\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        val color = gltf.getJSONArray("materials").getJSONObject(0)
            .getJSONObject("pbrMetallicRoughness").getJSONArray("baseColorFactor")
        assertEquals(1.0, color.getDouble(0), 0.00001)
        assertEquals(0.25, color.getDouble(1), 0.00001)
        assertEquals(0.5, color.getDouble(2), 0.00001)
    }

    @Test fun multipleColorsDoNotMisrepresentAsSingleMaterial() {
        val gltf = json("# xilon_base_color 1 0 0\n# xilon_base_color 0 0 1\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        assertFalse(gltf.has("materials"))
    }
}
