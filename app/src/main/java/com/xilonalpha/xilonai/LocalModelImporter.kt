package com.xilonalpha.xilonai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** Imports a user-selected GGUF through Android's document picker, entirely offline. */
object LocalModelImporter {
    private const val MAX_BYTES = 16L * 1024 * 1024 * 1024

    fun import(context: Context, uri: Uri): File {
        val resolver = context.contentResolver
        val suppliedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: throw IllegalArgumentException("Nu se poate citi numele fișierului.")
        val filename = suppliedName.substringAfterLast('/').substringAfterLast('\\')
        require(filename.endsWith(".gguf", ignoreCase = true)) { "Selectează un fișier .gguf complet." }
        require(filename.length in 6..180 && filename != "." && filename != "..") { "Nume de fișier invalid." }
        val dir = File(context.filesDir, "models")
        check(dir.isDirectory || dir.mkdirs()) { "Nu se poate crea directorul modelelor." }
        val target = File(dir, filename)
        require(!target.exists()) { "Modelul există deja: $filename" }
        val temp = File.createTempFile("import-", ".part", dir)
        try {
            var total = 0L
            resolver.openInputStream(uri)?.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var first = true
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        if (first) {
                            require(n >= 4 && buffer[0] == 'G'.code.toByte() &&
                                buffer[1] == 'G'.code.toByte() && buffer[2] == 'U'.code.toByte() &&
                                buffer[3] == 'F'.code.toByte()) { "Fișierul nu are antet GGUF valid." }
                            first = false
                        }
                        total += n
                        require(total <= MAX_BYTES) { "Fișierul depășește limita de 16 GiB." }
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            } ?: throw IllegalArgumentException("Nu se poate deschide fișierul selectat.")
            require(total >= 32) { "Fișier GGUF incomplet." }
            check(temp.renameTo(target)) { "Nu se poate finaliza importul." }
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
