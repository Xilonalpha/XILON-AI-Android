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

    @Test fun rejectsUnknownStepsRatherThanExecutingPartialPlan() {
        assertNull(XilonActionPlanner.plan("deschide setările; șterge toate fișierele"))
    }

    @Test fun rejectsUnsafeVolumeAndTooManySteps() {
        assertNull(XilonActionPlanner.plan("volum la 150%"))
        assertNull(XilonActionPlanner.plan(List(6) { "deschide setările" }.joinToString("; ")))
    }

    @Test fun whatsappRemainsUserConfirmed() {
        val plan = XilonActionPlanner.plan("trimite pe whatsapp: Salut!")
        assertTrue(plan!!.requiresConfirmation)
        assertEquals(XilonActions.Kind.SHARE_MESSAGE, plan.steps.single().proposal.kind)
    }
}
