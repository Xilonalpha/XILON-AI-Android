package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class LocalMemoryRecallTest {
    @Test fun recallsRelevantOlderTurnOnly() {
        val history = listOf(
            "My telescope tracks exoplanets" to "TESS observations",
            "Dinner" to "Pasta",
            "Recent one" to "Ok",
            "Recent two" to "Ok",
            "Recent three" to "Ok",
            "Recent four" to "Ok"
        )
        assertEquals(listOf(history[0]), LocalMemoryRecall.recall(history, "exoplanets telescope"))
    }

    @Test fun doesNotReturnRecentTurnsTwice() {
        val history = listOf("exoplanets" to "TESS", "other" to "yes")
        assertTrue(LocalMemoryRecall.recall(history, "exoplanets", excludeNewest = 4).isEmpty())
    }

    @Test fun ignoresEmptyQuery() {
        assertTrue(LocalMemoryRecall.recall(listOf("hello" to "world"), "?", excludeNewest = 0).isEmpty())
    }
}
