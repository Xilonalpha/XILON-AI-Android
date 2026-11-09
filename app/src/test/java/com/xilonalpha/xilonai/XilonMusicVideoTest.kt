package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonMusicVideoTest {
    @Test fun rendersBoundedAnimatedPreview() {
        val html = XilonMusicVideo.html("""{"type":"music_video","characters":2,"theme":"neon","bpm":120,"duration":30}""")
        assertNotNull(html)
        assertTrue(html!!.contains("three.min.js"))
        assertTrue(html.contains("const bpm=120,duration=30"))
        assertTrue(html.contains("dancers.push"))
    }

    @Test fun rejectsUnknownTypeAndBoundsParameters() {
        assertNull(XilonMusicVideo.parse("""{"type":"arbitrary"}"""))
        val spec = XilonMusicVideo.parse("""{"type":"music_video","characters":99,"bpm":999,"duration":999}""")!!
        assertEquals(3, spec.characters)
        assertEquals(180, spec.bpm)
        assertEquals(60, spec.duration)
    }

    @Test fun ignoresUntrustedScriptText() {
        val html = XilonMusicVideo.html("""{"type":"music_video"}<script>alert(1)</script>""")!!
        assertFalse(html.contains("alert(1)"))
    }
}
