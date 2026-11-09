package com.xilonalpha.xilonai

import java.util.Locale

/** Deterministic, private keyword recall; no network, embeddings or model calls. */
object LocalMemoryRecall {
    private val words = Regex("[\\p{L}\\p{N}]{3,}")
    private const val MAX_SCANNED_TURNS = 100
    private const val MAX_SCANNED_CHARS_PER_TURN = 4000
    private val stop = setOf("care", "este", "sunt", "pentru", "despre", "acest", "aceasta", "that", "this", "with", "from", "what", "have", "your", "the", "and")

    fun terms(text: String): Set<String> = words.findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filterNot { it in stop }.toSet()

    /** Returns at most [limit] older turns in chronological order. */
    fun recall(
        history: List<Pair<String, String>>,
        query: String,
        excludeNewest: Int = 4,
        limit: Int = 2
    ): List<Pair<String, String>> {
        val queryTerms = terms(query)
        if (queryTerms.isEmpty() || limit <= 0) return emptyList()
        val candidates = history.dropLast(excludeNewest.coerceAtLeast(0)).takeLast(MAX_SCANNED_TURNS)
        return candidates.withIndex().map { indexed ->
            val turnTerms = terms(indexed.value.first.take(MAX_SCANNED_CHARS_PER_TURN) + " " +
                indexed.value.second.take(MAX_SCANNED_CHARS_PER_TURN))
            val score = queryTerms.count { it in turnTerms }
            Triple(indexed.index, indexed.value, score)
        }.filter { it.third > 0 }
            .sortedWith(compareByDescending<Triple<Int, Pair<String, String>, Int>> { it.third }
                .thenByDescending { it.first })
            .take(limit)
            .sortedBy { it.first }
            .map { it.second }
    }
}
