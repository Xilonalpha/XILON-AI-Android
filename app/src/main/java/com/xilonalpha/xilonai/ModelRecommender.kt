package com.xilonalpha.xilonai

/**
 * Explainable, conservative ranking of downloaded-file candidates.
 * Never claims that a model architecture is supported before a real load succeeds.
 */
object ModelRecommender {
    enum class Tier { RECOMMENDED, POSSIBLE, NOT_RECOMMENDED, UNVERIFIED }

    data class Recommendation(val candidate: GgufCandidate, val tier: Tier, val score: Int, val explanation: String)

    fun rank(candidates: List<GgufCandidate>, availableRamBytes: Long): List<Recommendation> {
        return candidates.map { c ->
            val analysis = ModelIntelligence.analyze(c.filename)
            val tier = when {
                !analysis.runnableCandidate -> Tier.NOT_RECOMMENDED
                c.status == CompatibilityStatus.TOO_LARGE -> Tier.NOT_RECOMMENDED
                c.status == CompatibilityStatus.UNKNOWN || c.estimatedRequiredRamBytes <= 0L -> Tier.UNVERIFIED
                availableRamBytes <= 0L -> Tier.UNVERIFIED
                c.estimatedRequiredRamBytes <= availableRamBytes * 55L / 100L -> Tier.RECOMMENDED
                c.estimatedRequiredRamBytes <= availableRamBytes * 85L / 100L -> Tier.POSSIBLE
                else -> Tier.NOT_RECOMMENDED
            }
            val score = when (tier) {
                Tier.RECOMMENDED -> 90
                Tier.POSSIBLE -> 60
                Tier.UNVERIFIED -> 20
                Tier.NOT_RECOMMENDED -> 0
            } + when (analysis.quantization) {
                "Q4_K_M", "Q5_K_M" -> 5
                "Q8_0" -> 3
                else -> 0
            }
            val explanation = when (tier) {
                Tier.RECOMMENDED -> "Memoria estimată se încadrează confortabil; suportul arhitecturii rămâne neverificat"
                Tier.POSSIBLE -> "Modelul poate consuma mult RAM; stabilitatea depinde de dispozitiv"
                Tier.UNVERIFIED -> "Dimensiunea sau memoria disponibilă nu permite o evaluare"
                Tier.NOT_RECOMMENDED -> "Fișier auxiliar, model incomplet sau resurse estimate insuficiente"
            }
            Recommendation(c, tier, score, explanation)
        }.sortedWith(compareByDescending<Recommendation> { it.score }
            .thenBy { it.candidate.sizeBytes.takeIf { n -> n > 0L } ?: Long.MAX_VALUE })
    }
}
