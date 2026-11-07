package com.xilonalpha.xilonai

/**
 * Conservative offline multi-action planner. Each step is an explicit allowlisted
 * Android action; never invents app capabilities or silently sends messages.
 */
object XilonActionPlanner {
    data class Step(val proposal: XilonActions.Proposal, val argument: String)
    data class Plan(val steps: List<Step>, val requiresConfirmation: Boolean = true)

    private val split = Regex("(?i)\\s*(?:[;,]|\\b(?:și apoi|iar apoi|apoi|then)\\b)\\s*")
    private val volume = Regex("(?i)(?:volum(?:ul)?(?: media)?(?: la)?|volume(?: to)?)\\s*(\\d{1,3})\\s*%")

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
            } else XilonActions.propose(part) ?: return null
            Step(proposal, part)
        }
        return Plan(steps)
    }

    /** Stops on first failure; later actions are never dispatched after an error. */
    fun execute(context: android.content.Context, plan: Plan): Int {
        var completed = 0
        for (step in plan.steps) {
            if (!XilonActions.execute(context, step.proposal, step.argument)) break
            completed++
        }
        return completed
    }
}
