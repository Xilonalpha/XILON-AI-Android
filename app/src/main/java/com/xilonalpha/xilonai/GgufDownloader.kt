package com.xilonalpha.xilonai

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Blocking downloader. Invoke from a background worker, never from the UI thread.
 * Downloads to app-private storage and supports HTTP Range resume when the server
 * responds with 206. Existing partial data is discarded on a 200 response.
 * The completed file is atomically renamed from .part.
 */
object GgufDownloader {
    data class Result(val file: File, val sha256: String)

    fun download(
        context: Context,
        modelId: String,
        filename: String,
        expectedBytes: Long,
        expectedSha256: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result {
        require(modelId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        require(filename.endsWith(".gguf", true))
        require(filename.split('/').all { it.isNotBlank() && it != "." && it != ".." })
        require(expectedBytes > 0) { "Known size required for safe download" }
        val root = File(context.filesDir, "models")
        check(root.isDirectory || root.mkdirs())
        val safeName = (modelId + "__" + filename).replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val target = File(root, safeName)
        val partial = File(root, "$safeName.part")
        if (target.exists() && target.length() == expectedBytes) {
            val hash = sha256(target)
            if (expectedSha256 == null || hash.equals(expectedSha256, true)) return Result(target, hash)
            check(target.delete()) { "Unable to delete corrupt model" }
        }
        var offset = partial.takeIf { it.exists() }?.length() ?: 0L
        if (offset > expectedBytes) { check(partial.delete()); offset = 0L }
        val segments = (modelId + "/" + filename).split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val connection = URL("https://huggingface.co/$segments/resolve/main").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        try {
            val status = connection.responseCode
            check(status == 200 || status == 206) { "Download HTTP $status" }
            val append = offset > 0 && status == 206 &&
                connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true
            if (status == 206 && !append) error("Invalid partial-content response")
            if (status == 206 && offset == 0L) error("Unexpected partial-content response")
            if (!append) offset = 0L
            java.io.FileOutputStream(partial, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = offset
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        check(written <= expectedBytes) { "Response larger than expected" }
                        onProgress(written, expectedBytes)
                    }
                }
            }
            check(partial.length() == expectedBytes) { "Incomplete download" }
            val hash = sha256(partial)
            check(expectedSha256 == null || hash.equals(expectedSha256, true)) { "SHA-256 mismatch" }
            check(partial.renameTo(target)) { "Cannot finalize model file" }
            return Result(target, hash)
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
