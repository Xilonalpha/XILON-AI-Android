package com.xilonalpha.xilonai

import java.io.File

/**
 * Native GGUF format preflight only. This is NOT a llama.cpp inference engine.
 */
object NativeGgufProbe {
    init { System.loadLibrary("xilon_native") }

    private external fun probeVersion(path: String): Int

    fun version(file: File): Int {
        require(file.isFile) { "Model file does not exist" }
        return probeVersion(file.absolutePath)
    }
}
