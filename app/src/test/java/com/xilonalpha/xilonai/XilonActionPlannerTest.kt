package com.xilonalpha.xilonai

import org.junit.Assert.*
import org.junit.Test

class XilonActionPlannerTest {
    @Test fun twoStepPlanPreservesOrder() {
        val plan = XilonActionPlanner.plan("deschide setările, apoi volum la 30%")
        assertNotNull(plan)
        assertEquals(2, plan!!.steps.size)
        assertEquals(XilonActions.Kind.SETTINGS, plan.steps[0].proposal.kind)
        assertEquals(XilonActions.Kind.SET_VOLUME, plan.steps[1].proposal.kind)
    }

    @Test fun explicitVolumeCommandsWorkWithoutSecondConfirmation() {
        val plan = XilonActionPlanner.plan("Setează volumul la 40%")
        assertEquals(XilonActions.Kind.SET_VOLUME, plan?.steps?.single()?.proposal?.kind)
        assertEquals(XilonActions.Kind.SET_VOLUME,
            XilonActionPlanner.plan("volum la 30%")?.steps?.single()?.proposal?.kind)
    }

    @Test fun questionsCannotBecomeAutonomousVolumeActions() {
        assertNull(XilonActionPlanner.plan("Cum setez volumul la 40%?"))
        assertNull(XilonActionPlanner.plan("Poți explica volumul la 40%?"))
        assertNull(XilonActionPlanner.plan("setează volumul la 150%"))
    }

    @Test fun rejectsUnknownStepsRatherThanExecutingPartialPlan() {
        assertNull(XilonActionPlanner.plan("deschide setările; șterge toate fișierele"))
    }

    @Test fun rejectsUnsafeVolumeAndTooManySteps() {
        assertNull(XilonActionPlanner.plan("volum la 150%"))
        assertNull(XilonActionPlanner.plan(List(6) { "deschide setările" }.joinToString("; ")))
    }

    @Test fun whatsappRemainsUserConfirmed() {
        val plan = XilonActionPlanner.plan("trimite pe whatsapp: Salut!")
        assertEquals(XilonActions.Kind.SHARE_MESSAGE, plan!!.steps.single().proposal.kind)
        assertEquals(XilonActions.Kind.SHARE_MESSAGE, plan.steps.single().proposal.kind)
    }
    @Test fun supportsRomanianSequentialCommand() {
        val plan = XilonActionPlanner.plan("deschide setările și apoi volum la 30%")
        assertEquals(2, plan?.steps?.size)
    }

    @Test fun youtubeAndVolumeCanBePlannedTogether() {
        val plan = XilonActionPlanner.plan("caută pe youtube: jazz relaxant; volum la 30%")
        assertEquals(XilonActions.Kind.YOUTUBE_SEARCH, plan?.steps?.first()?.proposal?.kind)
        assertEquals(XilonActions.Kind.SET_VOLUME, plan?.steps?.last()?.proposal?.kind)
    }
    @Test fun reportSeparatesDispatchFromCompletion() {
        val report = XilonActionPlanner.ExecutionReport(
            attempted = 2, dispatched = 1, total = 3, failedStep = 2)
        assertFalse(report.allDispatched)
        assertEquals(2, report.failedStep)
        assertEquals(1, report.dispatched)
    }

    @Test fun voiceTranscriptCanBecomeActionPlan() {
        val plan = XilonActionPlanner.plan("caută pe youtube: muzică relaxantă; volum la 30%")
        assertNotNull(plan)
        assertTrue(plan!!.requiresConfirmation)
        assertEquals(2, plan.steps.size)
    }
    @Test fun commaInsideWhatsappMessageIsNotAnActionSeparator() {
        val plan = XilonActionPlanner.plan("trimite pe whatsapp: Salut, ce faci?")
        assertNotNull(plan)
        assertEquals(1, plan!!.steps.size)
        assertEquals("trimite pe whatsapp: Salut, ce faci?", plan.steps.single().argument)
    }

    @Test fun commaInsideYoutubeQueryIsPreserved() {
        val plan = XilonActionPlanner.plan("caută pe youtube: jazz, blues și rock")
        assertEquals(1, plan?.steps?.size)
        assertEquals("caută pe youtube: jazz, blues și rock", plan?.steps?.single()?.argument)
    }

    @Test fun commaFollowedByExplicitSequenceStillSplits() {
        val plan = XilonActionPlanner.plan("deschide setările, apoi volum la 30%")
        assertEquals(2, plan?.steps?.size)
    }
}
