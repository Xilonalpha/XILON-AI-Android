package com.xilonalpha.xilonai

import java.io.File

/** Experimental local CPU inference. All calls must be made off the main thread. */
object LocalLlama {
    init { System.loadLibrary("xilon_native") }

    private external fun load(path: String, contextSize: Int): Boolean
    external fun unload()
    external fun generate(prompt: String, maxTokens: Int): String
    /** Tokenizes output using the active model vocabulary. Must run off the UI thread. */
    external fun countTokens(text: String): Int

    fun loadModel(file: File, contextSize: Int = 2048): Boolean {
        require(file.isFile && file.extension.equals("gguf", true))
        return load(file.absolutePath, contextSize)
    }
}
