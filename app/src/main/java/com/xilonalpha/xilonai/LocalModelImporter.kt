package com.xilonalpha.xilonai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream

/** Imports a user-selected GGUF through Android's document picker, entirely offline. */
object LocalModelImporter {
    private const val MAX_BYTES = 16L * 1024 * 1024 * 1024

    fun import(context: Context, uri: Uri, onProgress: (Long, Long?) -> Unit = { _, _ -> }): File {
        val resolver = context.contentResolver
        val suppliedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: throw IllegalArgumentException("Nu se poate citi numele fișierului.")
        val declaredSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }?.takeIf { it >= 0 }
        require(declaredSize == null || declaredSize <= MAX_BYTES) { "Fișierul depășește limita de 16 GiB." }
        val filename = suppliedName.substringAfterLast('/').substringAfterLast('\\')
        require(filename.endsWith(".gguf", ignoreCase = true)) { "Selectează un fișier .gguf complet." }
        require(filename.length in 6..180 && filename != "." && filename != "..") { "Nume de fișier invalid." }
        val analysis = ModelIntelligence.analyze(filename)
        require(analysis.runnableCandidate) { analysis.reason }
        val dir = File(context.filesDir, "models")
        check(dir.isDirectory || dir.mkdirs()) { "Nu se poate crea directorul modelelor." }
        if (declaredSize != null) {
            val freeBytes = StatFs(dir.absolutePath).availableBytes
            require(freeBytes > declaredSize + 32L * 1024 * 1024) { "Spațiu insuficient pentru copierea modelului." }
        }
        val target = File(dir, filename)
        require(!target.exists()) { "Modelul există deja: $filename" }
        val temp = File.createTempFile("import-", ".part", dir)
        try {
            var total = 0L
            val magic = ByteArray(4)
            var magicLength = 0
            var lastReported = 0L
            resolver.openInputStream(uri)?.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        val needed = 4 - magicLength
                        if (needed > 0) {
                            val copy = minOf(needed, n)
                            System.arraycopy(buffer, 0, magic, magicLength, copy)
                            magicLength += copy
                            if (magicLength == 4) {
                                require(magic.contentEquals(byteArrayOf(71, 71, 85, 70))) {
                                    "Fișierul nu are antet GGUF valid."
                                }
                            }
                        }
                        total += n
                        require(total <= MAX_BYTES) { "Fișierul depășește limita de 16 GiB." }
                        output.write(buffer, 0, n)
                        if (total - lastReported >= 4L * 1024 * 1024) {
                            onProgress(total, declaredSize)
                            lastReported = total
                        }
                    }
                    output.fd.sync()
                }
            } ?: throw IllegalArgumentException("Nu se poate deschide fișierul selectat.")
            onProgress(total, declaredSize)
            require(total >= 32) { "Fișier GGUF incomplet." }
            if (declaredSize != null) {
                require(total == declaredSize) { "Copiere incompletă: $total din $declaredSize octeți." }
            }
            check(temp.renameTo(target)) { "Nu se poate finaliza importul." }
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
