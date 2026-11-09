package com.xilonalpha.xilonai

import java.io.File

/** Experimental local CPU inference. All calls must be made off the main thread. */
object LocalLlama {
    init { System.loadLibrary("xilon_native") }

    private external fun load(path: String, contextSize: Int): Boolean
    external fun unload()
    external fun generate(prompt: String, maxTokens: Int): String
    /** Emits cumulative partial text from the native inference thread. */
    external fun requestStop()
    /** Clear cancellation before scheduling inference, never inside generation. */
    external fun prepareGeneration()
    external fun generateStreaming(prompt: String, maxTokens: Int, onUpdate: (String) -> Unit): String
    /** Tokenizes output using the active model vocabulary. Must run off the UI thread. */
    external fun countTokens(text: String): Int
    /** [prompt evaluation ms, decode ms, generated tokens] from the last native call. */
    external fun lastTimings(): LongArray
    /** Returns null if the loaded GGUF lacks a usable native chat template. */
    external fun formatChat(roles: Array<String>, contents: Array<String>): String?

    fun loadModel(file: File, contextSize: Int = 4096): Boolean {
        require(file.isFile && file.extension.equals("gguf", true))
        return load(file.absolutePath, contextSize)
    }
}
