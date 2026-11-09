package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Test

class XilonResponseBoundaryTest {
    @Test fun hidesUnfinishedReasoningButKeepsEarlierVisibleText() {
        assertEquals("Răspuns", XilonResponseBoundary.clean("Răspuns<think>intern"))
    }
    @Test fun removesAllCompletedReasoningBlocks() {
        assertEquals("Salut! Gata.", XilonResponseBoundary.clean(
            "<think>primul</think>Salut! <think>al doilea</think>Gata."))
    }
    @Test fun hidesStreamingReasoningUntilClosingTag() {
        assertEquals("", XilonResponseBoundary.clean("<think>analiză în curs"))
        assertEquals("Rezultat", XilonResponseBoundary.clean(
            "<think>analiză</think>Rezultat"))
    }
    @Test fun stopsBeforeChatMlRoleLeak() {
        assertEquals("Salut!", XilonResponseBoundary.clean(
            "Salut!<|im_end|>\n<|im_start|>user\nAltă întrebare"))
    }
    @Test fun preservesOrdinaryText() {
        assertEquals("Plan pentru site: 1. Structură, 2. Design.",
            XilonResponseBoundary.clean("Plan pentru site: 1. Structură, 2. Design."))
    }
    @Test fun stopsAtLlamaTurnBoundary() {
        assertEquals("Bună ziua", XilonResponseBoundary.clean(
            "Bună ziua<|eot_id|><|start_header_id|>user"))
    }
    @Test fun removesOrphanClosingThinkAfterQwenPrefill() {
        assertEquals("Salut! Cu ce te pot ajuta?",
            XilonResponseBoundary.clean("</think>\nSalut! Cu ce te pot ajuta?"))
    }
}
