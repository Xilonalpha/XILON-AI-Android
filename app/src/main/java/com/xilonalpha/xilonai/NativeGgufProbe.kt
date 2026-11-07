package com.xilonalpha.xilonai

import java.io.File

data class GgufHeader(val version: Int, val tensorCount: Long, val metadataCount: Long)

/** Native GGUF header preflight only. Does not execute a model or validate all tensors. */
object NativeGgufProbe {
    init { System.loadLibrary("xilon_native") }

    private external fun probeHeader(path: String): LongArray?

    fun inspect(file: File): GgufHeader? {
        require(file.isFile) { "Model file does not exist" }
        val values = probeHeader(file.absolutePath) ?: return null
        if (values.size != 3) return null
        return GgufHeader(values[0].toInt(), values[1], values[2])
    }
}
