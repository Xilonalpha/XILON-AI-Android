package com.xilonalpha.xilonai

/**
 * Conservative offline multi-action planner. Each step is an explicit allowlisted
 * Android action; never invents app capabilities or silently sends messages.
 */
object XilonActionPlanner {
    data class Step(val proposal: XilonActions.Proposal, val argument: String)
    data class Plan(val steps: List<Step>)

    private val split = Regex("""(?i)\s*(?:;|,\s*(?:și apoi|iar apoi|apoi|then)\b|\b(?:și apoi|iar apoi|apoi|then)\b)\s*""")
    // Match a complete imperative command, not a question that happens to mention volume.
    private val volume = Regex(
        """(?i)^(?:(?:setează|seteaza|set|pune|reglează|regleaza)\s+)?(?:volum(?:ul)?(?: media)?|volume)(?:\s+(?:la|to))?\s+(\d{1,3})\s*%$"""
    )

    fun plan(command: String): Plan? {
        if (command.length > 1000) return null
        val parts = command.split(split).map(String::trim).filter(String::isNotBlank)
        if (parts.isEmpty() || parts.size > 5) return null
        val steps = parts.map { part ->
            val match = volume.find(part)
            val proposal = if (match != null && (match.groupValues[1].toIntOrNull() ?: -1) in 0..100) {
                XilonActions.Proposal(XilonActions.Kind.SET_VOLUME,
                    "Volum media la ${match.groupValues[1]}%",
                    "Setează volumul media la procentul solicitat.")
            } else {
                if (match != null) return null // Invalid percentage must not fall through to volume-step commands.
                XilonActions.propose(part) ?: return null
            }
            Step(proposal, part)
        }
        return Plan(steps)
    }

    data class ExecutionReport(
        val attempted: Int,
        val dispatched: Int,
        val total: Int,
        val failedStep: Int?,
        val verified: Int = 0,
        val details: List<String> = emptyList()
    ) {
        val allDispatched: Boolean get() = dispatched == total
    }

    /**
     * Reports only Android dispatch success, never claims that another app
     * completed an action. Stops before any subsequent step after a failure.
     */
    fun executeWithReport(context: android.content.Context, plan: Plan): ExecutionReport {
        var dispatched = 0
        var verified = 0
        val details = mutableListOf<String>()
        for ((index, step) in plan.steps.withIndex()) {
            val outcome = XilonActionVerifier.execute(context, step)
            details += "${index + 1}. ${step.proposal.label}: ${outcome.message}"
            if (outcome.state == XilonActionVerifier.State.FAILED) {
                return ExecutionReport(index + 1, dispatched, plan.steps.size,
                    index + 1, verified, details)
            }
            dispatched++
            if (outcome.state == XilonActionVerifier.State.VERIFIED) verified++
        }
        return ExecutionReport(dispatched, dispatched, plan.steps.size,
            null, verified, details)
    }

    fun execute(context: android.content.Context, plan: Plan): Int =
        executeWithReport(context, plan).dispatched
}
