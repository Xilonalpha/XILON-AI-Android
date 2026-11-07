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
    @Test fun mediaControlsAreDetectedWithoutExecuting() {
        assertEquals(XilonActions.Kind.PLAY_PAUSE,
            XilonActions.propose("pauză muzica")?.kind)
        assertEquals(XilonActions.Kind.NEXT_TRACK,
            XilonActions.propose("melodia următoare")?.kind)
        assertEquals(XilonActions.Kind.PREVIOUS_TRACK,
            XilonActions.propose("melodia anterioară")?.kind)
    }
    @Test fun volumeCommandsAreExplicit() {
        assertEquals(XilonActions.Kind.VOLUME_UP,
            XilonActions.propose("mărește volumul")?.kind)
        assertEquals(XilonActions.Kind.VOLUME_DOWN,
            XilonActions.propose("micșorează volumul")?.kind)
    }
    @Test fun whatsappDraftRequiresExplicitPrefix() {
        assertEquals(XilonActions.Kind.SHARE_MESSAGE,
            XilonActions.propose("trimite pe whatsapp: Salut!")?.kind)
        assertNull(XilonActions.propose("Cum pot trimite pe WhatsApp un mesaj?"))
    }
}
