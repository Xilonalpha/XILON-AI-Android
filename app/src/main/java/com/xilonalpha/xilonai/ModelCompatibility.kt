package com.xilonalpha.xilonai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GgufCandidate(
    val filename: String,
    val sizeBytes: Long,
    val estimatedRequiredRamBytes: Long,
    val status: CompatibilityStatus,
    val reason: String,
    val expectedSha256: String?
)

enum class CompatibilityStatus { ESTIMATED, TOO_LARGE, UNKNOWN }

object ModelCompatibility {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB

    fun candidates(context: Context, modelId: String): List<GgufCandidate> {
        require(modelId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Invalid model id" }
        val encodedId = modelId.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8")
        }
        val url = "https://huggingface.co/api/models/$encodedId?blobs=true"
        val response = getJson(url)
        val siblings = response.optJSONArray("siblings") ?: JSONArray()
        val manager = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath).availableBytes
        val is64 = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        // Use both total device RAM and currently available memory; Android may reclaim
        // cached pages, so this remains an estimate rather than a hard admission test.
        val conservativeRamBudget = minOf(mem.totalMem * 75L / 100L,
            mem.availMem * 85L / 100L)
        val storageBudget = (storage - 256L * MIB).coerceAtLeast(0L)
        return (0 until siblings.length()).mapNotNull { index ->
            val item = siblings.optJSONObject(index) ?: return@mapNotNull null
            val filename = item.optString("rfilename")
            if (!filename.endsWith(".gguf", ignoreCase = true)) return@mapNotNull null
            // Importance matrices and auxiliary quantization data are not runnable models.
            val analysis = ModelIntelligence.analyze(filename)
            if (!analysis.runnableCandidate) return@mapNotNull null
            val lfs = item.optJSONObject("lfs")
            val size = when {
                item.has("size") && !item.isNull("size") -> item.optLong("size", -1L)
                lfs != null && lfs.has("size") -> lfs.optLong("size", -1L)
                else -> -1L
            }
            // Conservative heuristic, not a runtime memory guarantee:
            // weights + 35% for working buffers + 512 MiB fixed overhead.
            val estimated = if (size > 0 && size <= (Long.MAX_VALUE - 512L * MIB) / 2L)
                size + size * 35L / 100L + 512L * MIB else -1L
            val status = when {
                size <= 0L || estimated <= 0L -> CompatibilityStatus.UNKNOWN
                !is64 || size > storageBudget || estimated > conservativeRamBudget ->
                    CompatibilityStatus.TOO_LARGE
                else -> CompatibilityStatus.ESTIMATED
            }
            val reason = when (status) {
                CompatibilityStatus.UNKNOWN -> "File size unavailable; cannot estimate"
                CompatibilityStatus.TOO_LARGE -> "Resurse insuficiente acum: RAM disponibil estimat ${conservativeRamBudget / MIB} MiB; spațiu utilizabil ${storageBudget / MIB} MiB"
                CompatibilityStatus.ESTIMATED -> "Cuantizare: ${analysis.quantization ?: "necunoscută"}; RAM estimat, arhitectură neverificată"
            }
            GgufCandidate(filename, size, estimated, status, reason,
                lfs?.optString("oid")?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) })
        }.sortedWith(compareBy({ it.status != CompatibilityStatus.ESTIMATED }, { it.sizeBytes.takeIf { n -> n > 0 } ?: Long.MAX_VALUE }))
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) error("Hugging Face HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
