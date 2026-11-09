package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonBeatAnalyzerTest {
    @Test fun findsSynthetic120BpmPulseTrain() {
        val envelope = List(1000) { i -> if (i % 25 == 0) 1.0 else 0.01 }
        val bpm = XilonBeatAnalyzer.estimateFromEnvelope(envelope)
        assertNotNull(bpm)
        assertTrue("Expected a harmonic of 120 BPM, got $bpm", bpm == 60 || bpm == 120)
    }

    @Test fun rejectsSilenceAndShortClips() {
        assertNull(XilonBeatAnalyzer.estimateFromEnvelope(List(1000) { 0.0 }))
        assertNull(XilonBeatAnalyzer.estimateFromEnvelope(List(30) { 1.0 }))
    }
}
