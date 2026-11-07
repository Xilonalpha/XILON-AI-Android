package com.xilonalpha.xilonai

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Converts a triangulated Wavefront OBJ mesh into a self-contained glTF 2.0 GLB. */
object XilonGlbExport {
    fun fromObj(obj: String): ByteArray {
        val vertices = mutableListOf<FloatArray>()
        val triangles = mutableListOf<Int>()
        obj.lineSequence().forEach { raw ->
            val parts = raw.trim().split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "v" -> {
                    require(parts.size >= 4) { "Invalid OBJ vertex" }
                    vertices.add(floatArrayOf(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                }
                "f" -> {
                    require(parts.size >= 4) { "Invalid OBJ face" }
                    val face = parts.drop(1).map { token ->
                        val n = token.substringBefore('/').toInt()
                        val index = if (n > 0) n - 1 else vertices.size + n
                        require(index in vertices.indices) { "OBJ face references invalid vertex" }
                        index
                    }
                    for (i in 1 until face.size - 1) {
                        triangles.add(face[0]); triangles.add(face[i]); triangles.add(face[i + 1])
                    }
                }
            }
        }
        require(vertices.size >= 3 && triangles.size >= 3) { "OBJ has no mesh triangles" }
        require(vertices.size <= 1_000_000 && triangles.size <= 3_000_000) { "Mesh too large" }
        val positionsBytes = vertices.size * 12
        val indicesOffset = (positionsBytes + 3) and -4
        val binLength = indicesOffset + triangles.size * 4
        val bin = ByteBuffer.allocate(binLength).order(ByteOrder.LITTLE_ENDIAN)
        vertices.forEach { v -> v.forEach { bin.putFloat(it) } }
        bin.position(indicesOffset)
        triangles.forEach { bin.putInt(it) }
        val min = (0..2).map { axis -> vertices.minOf { it[axis].toDouble() } }
        val max = (0..2).map { axis -> vertices.maxOf { it[axis].toDouble() } }
        val accessors = JSONArray()
            .put(JSONObject().put("bufferView", 0).put("componentType", 5126)
                .put("count", vertices.size).put("type", "VEC3")
                .put("min", JSONArray(min)).put("max", JSONArray(max)))
            .put(JSONObject().put("bufferView", 1).put("componentType", 5125)
                .put("count", triangles.size).put("type", "SCALAR"))
        val gltf = JSONObject()
            .put("asset", JSONObject().put("version", "2.0").put("generator", "XILON AI"))
            .put("scene", 0)
            .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            .put("nodes", JSONArray().put(JSONObject().put("mesh", 0)))
            .put("meshes", JSONArray().put(JSONObject().put("primitives", JSONArray().put(
                JSONObject().put("attributes", JSONObject().put("POSITION", 0))
                    .put("indices", 1).put("mode", 4)))))
            .put("buffers", JSONArray().put(JSONObject().put("byteLength", binLength)))
            .put("bufferViews", JSONArray()
                .put(JSONObject().put("buffer", 0).put("byteOffset", 0)
                    .put("byteLength", positionsBytes).put("target", 34962))
                .put(JSONObject().put("buffer", 0).put("byteOffset", indicesOffset)
                    .put("byteLength", triangles.size * 4).put("target", 34963)))
            .put("accessors", accessors)
        val json = gltf.toString().toByteArray(Charsets.UTF_8)
        val jsonPadded = (json.size + 3) and -4
        val total = 12 + 8 + jsonPadded + 8 + binLength
        val output = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(0x46546C67).putInt(2).putInt(total)
        output.putInt(jsonPadded).putInt(0x4E4F534A).put(json)
        repeat(jsonPadded - json.size) { output.put(0x20.toByte()) }
        output.putInt(binLength).putInt(0x004E4942).put(bin.array())
        return output.array()
    }
}
