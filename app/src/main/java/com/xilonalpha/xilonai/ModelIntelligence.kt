package com.xilonalpha.xilonai

import java.util.Locale

/**
 * Conservative GGUF preflight. A filename heuristic is never proof of llama.cpp support.
 * Multipart shards must be downloaded and loaded as a set, not as standalone files.
 */
object ModelIntelligence {
    private val shard = Regex("(?i)-([0-9]{5})-of-([0-9]{5})\\.gguf$")
    private val quant = Regex("(?i)(?:^|[-_.])(IQ[1-4]_[A-Z0-9_]+|Q[2-8]_[A-Z0-9_]+|F16|F32|BF16)(?:\\.gguf|[-_.])")

    data class Analysis(
        val runnableCandidate: Boolean,
        val reason: String,
        val quantization: String?,
        val shardIndex: Int?,
        val shardTotal: Int?
    )

    fun analyze(filename: String): Analysis {
        val name = filename.substringAfterLast('/')
        if (!name.endsWith(".gguf", true))
            return Analysis(false, "Nu este fișier GGUF", null, null, null)
        if (AuxiliaryGguf.isAuxiliary(name))
            return Analysis(false, "Componentă auxiliară, nu model de chat", null, null, null)
        val shardMatch = shard.find(name)
        val index = shardMatch?.groupValues?.get(1)?.toIntOrNull()
        val total = shardMatch?.groupValues?.get(2)?.toIntOrNull()
        val q = quant.find(name)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        if (total != null && total > 1)
            return Analysis(false, "Model împărțit în $total părți: necesită toate fișierele", q, index, total)
        return Analysis(true, "Candidat de model; arhitectura trebuie verificată la încărcare", q, index, total)
    }
}
