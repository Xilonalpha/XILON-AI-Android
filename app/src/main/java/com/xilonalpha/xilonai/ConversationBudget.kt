package com.xilonalpha.xilonai

/** Pure helper: retain whole recent turns, never fragments of chat template tokens. */
object ConversationBudget {
    data class Turn(val user: String, val assistant: String)

    fun <T> fit(
        history: List<T>,
        tokenBudget: Int,
        render: (List<T>) -> String?,
        countTokens: (String) -> Int
    ): String? {
        if (tokenBudget <= 0) return null
        for (drop in 0..history.size) {
            val formatted = render(history.drop(drop)) ?: continue
            val tokens = countTokens(formatted)
            if (tokens in 1..tokenBudget) return formatted
        }
        return null
    }
}
