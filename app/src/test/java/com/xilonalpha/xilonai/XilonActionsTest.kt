package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonActionsTest {
    @Test fun settingsProposalRequiresExplicitCommand() {
        val proposal = XilonActions.propose("Te rog deschide setările")
        assertEquals(XilonActions.Kind.SETTINGS, proposal?.kind)
    }
    @Test fun questionsContainingDeviceCommandsDoNotExecute() {
        assertNull(XilonActions.propose("Cum deschide setările Android?"))
        assertNull(XilonActions.propose("Explică cum mărește volumul telefonul"))
        assertNull(XilonActions.propose("Poți explica pauză muzica?"))
        assertNull(XilonActions.propose("deschide whatsapp?"))
        assertEquals(XilonActions.Kind.OPEN_APP,
            XilonActions.propose("deschide whatsapp")?.kind)
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
        assertEquals(XilonActions.Kind.PAUSE_MEDIA,
            XilonActions.propose("pauză muzica")?.kind)
        assertEquals(XilonActions.Kind.PLAY_MEDIA,
            XilonActions.propose("reia muzica")?.kind)
        assertEquals(XilonActions.Kind.PLAY_PAUSE,
            XilonActions.propose("play pause")?.kind)
        assertEquals(XilonActions.Kind.NEXT_TRACK,
            XilonActions.propose("melodia următoare")?.kind)
        assertEquals(XilonActions.Kind.PREVIOUS_TRACK,
            XilonActions.propose("melodia anterioară")?.kind)
    }
    @Test fun appLaunchRequiresExplicitAllowlistedCommand() {
        assertEquals(XilonActions.Kind.OPEN_APP,
            XilonActions.propose("deschide whatsapp")?.kind)
        assertEquals(XilonActions.Kind.OPEN_APP,
            XilonActions.propose("open youtube")?.kind)
        assertEquals(XilonActions.Kind.OPEN_APP,
            XilonActions.propose("deschide aplicația WhatsApp")?.kind)
        assertEquals(XilonActions.Kind.OPEN_APP,
            XilonActions.propose("lansează aplicația Spotify")?.kind)
        assertNull(XilonActions.propose("deschide aplicația bancară"))
    }
    @Test fun volumeCommandsAreExplicit() {
        assertEquals(XilonActions.Kind.VOLUME_UP,
            XilonActions.propose("mărește volumul")?.kind)
        assertEquals(XilonActions.Kind.VOLUME_DOWN,
            XilonActions.propose("micșorează volumul")?.kind)
    }
    @Test fun percentageVolumeCommandsAreRecognizedWithoutExecution() {
        assertEquals(XilonActions.Kind.SET_VOLUME,
            XilonActions.propose("Setează volumul la 40%")?.kind)
        assertEquals(XilonActions.Kind.SET_VOLUME,
            XilonActions.propose("pune volum 15%")?.kind)
        assertNull(XilonActions.propose("Cum setez volumul la 40%?"))
        assertNull(XilonActions.propose("setează volumul la maxim"))
    }
    @Test fun whatsappDraftRequiresExplicitPrefix() {
        assertEquals(XilonActions.Kind.SHARE_MESSAGE,
            XilonActions.propose("trimite pe whatsapp: Salut!")?.kind)
        assertNull(XilonActions.propose("Cum pot trimite pe WhatsApp un mesaj?"))
    }
    @Test fun youtubeSearchNeedsExplicitCommand() {
        assertEquals(XilonActions.Kind.YOUTUBE_SEARCH,
            XilonActions.propose("caută pe youtube: jazz relaxant")?.kind)
        assertNull(XilonActions.propose("Cum funcționează căutarea YouTube?"))
    }
}
