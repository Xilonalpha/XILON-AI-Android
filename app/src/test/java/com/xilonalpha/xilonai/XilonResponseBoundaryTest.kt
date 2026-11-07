package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Test

class XilonResponseBoundaryTest {
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
}
