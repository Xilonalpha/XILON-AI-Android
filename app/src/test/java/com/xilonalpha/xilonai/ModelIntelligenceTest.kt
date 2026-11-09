package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class ModelIntelligenceTest {
    @Test fun excludesProjectorsAndImportanceMatrices() {
        assertFalse(ModelIntelligence.analyze("mmproj-F16.gguf").runnableCandidate)
        assertFalse(ModelIntelligence.analyze("imatrix.gguf").runnableCandidate)
        assertFalse(ModelIntelligence.analyze("OpenVisionHead.gguf").runnableCandidate)
    }

    @Test fun rejectsStandaloneShards() {
        val result = ModelIntelligence.analyze("Qwen-Q4_K_M-00001-of-00003.gguf")
        assertFalse(result.runnableCandidate)
        assertEquals(3, result.shardTotal)
        assertEquals(1, result.shardIndex)
    }

    @Test fun recognizesOrdinaryQuantizedModelCandidate() {
        val result = ModelIntelligence.analyze("Qwen2.5-0.5B-Instruct-Q4_K_M.gguf")
        assertTrue(result.runnableCandidate)
        assertEquals("Q4_K_M", result.quantization)
    }

    @Test fun ranksLowerMemoryCandidateAheadOfOversizedCandidate() {
        val small = GgufCandidate("small-Q4_K_M.gguf", 300_000_000, 700_000_000,
            CompatibilityStatus.ESTIMATED, "", null, "0123456789abcdef0123456789abcdef01234567")
        val huge = GgufCandidate("huge-F16.gguf", 8_000_000_000, 11_000_000_000,
            CompatibilityStatus.TOO_LARGE, "", null, "0123456789abcdef0123456789abcdef01234567")
        val result = ModelRecommender.rank(listOf(huge, small), 3_000_000_000)
        assertEquals("small-Q4_K_M.gguf", result.first().candidate.filename)
        assertEquals(ModelRecommender.Tier.RECOMMENDED, result.first().tier)
    }
}
