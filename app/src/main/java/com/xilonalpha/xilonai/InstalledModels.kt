package com.xilonalpha.xilonai

import android.content.Context
import java.io.File

/**
 * App-private installed GGUF files. Never follows arbitrary user-supplied paths.
 */
object InstalledModels {
    fun list(context: Context): List<File> {
        val directory = File(context.filesDir, "models")
        return directory.listFiles()?.filter {
            it.isFile && it.name.endsWith(".gguf", ignoreCase = true) && !it.name.endsWith(".part")
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun delete(context: Context, filename: String): Boolean {
        require(filename.isNotBlank() && filename == File(filename).name)
        require(filename.endsWith(".gguf", ignoreCase = true))
        val directory = File(context.filesDir, "models").canonicalFile
        val file = File(directory, filename).canonicalFile
        require(file.parentFile == directory) { "Invalid model path" }
        return file.isFile && file.delete()
    }
}
