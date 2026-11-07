package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class ConversationBudgetTest {
    @Test fun retainsNewestCompleteTurns() {
        val result = ConversationBudget.fit(
            listOf("old", "recent"), 12,
            render = { turns -> turns.joinToString("|") + "|new" },
            countTokens = { it.length }
        )
        assertEquals("recent|new", result)
    }

    @Test fun rejectsOversizedLatestMessage() {
        val result = ConversationBudget.fit(
            listOf("old"), 3,
            render = { turns -> turns.joinToString("|") + "oversized" },
            countTokens = { it.length }
        )
        assertNull(result)
    }

    @Test fun retainsHistoryWhenItFits() {
        val result = ConversationBudget.fit(
            listOf("first", "second"), 100,
            render = { it.joinToString(",") },
            countTokens = { it.length }
        )
        assertEquals("first,second", result)
    }
}
