package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonActionsTest {
    @Test fun settingsProposalRequiresExplicitCommand() {
        val proposal = XilonActions.propose("Te rog deschide setările")
        assertEquals(XilonActions.Kind.SETTINGS, proposal?.kind)
    }
    @Test fun ordinaryChatDoesNotTriggerActions() {
        assertNull(XilonActions.propose("Explică-mi cum funcționează setările Android"))
    }
    @Test fun musicProposalIsNotAutoplayPromise() {
        val proposal = XilonActions.propose("deschide muzica")
        assertEquals(XilonActions.Kind.MUSIC, proposal?.kind)
        assertTrue(proposal!!.explanation.contains("nu garantează redarea"))
    }
    @Test fun searchProposalIsExplicit() {
        assertEquals(XilonActions.Kind.WEB_SEARCH,
            XilonActions.propose("caută pe web vremea")?.kind)
    }
}
