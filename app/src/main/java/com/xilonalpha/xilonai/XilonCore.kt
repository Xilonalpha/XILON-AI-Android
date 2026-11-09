package com.xilonalpha.xilonai

/**
 * Deterministic, offline task router. It never sends user text to a server,
 * executes commands, or claims that a model supports an untested capability.
 */
object XilonCore {
    enum class Task { CHAT, CODE, THREE_D, REASONING, TRANSLATION, SUMMARIZATION }
    data class Decision(val task: Task, val explanation: String, val suggestedOutputTokens: Int)

    fun classify(message: String): Decision {
        val q = message.lowercase(java.util.Locale.ROOT)
        val task = when {
            (listOf("3d", "trei dimensiuni", "three.js", "mesh", "model tridimensional").any { q.contains(it) } &&
                listOf("genere", "cree", "modele", "constru", "desene", "export").any { q.contains(it) }) -> Task.THREE_D
            listOf("cod", "program", "kotlin", "python", "javascript", "c++", "debug",
                "compile", "compil", "funcție", "function", "bug", "eroare în cod")
                .any { q.contains(it) } -> Task.CODE
            listOf("tradu", "translate", "translation", "traducere", "traduci")
                .any { q.contains(it) } -> Task.TRANSLATION
            listOf("rezum", "rezumat", "summarize", "summary", "sintetiz")
                .any { q.contains(it) } -> Task.SUMMARIZATION
            listOf("demonstrează", "demonstreaza", "explică de ce", "explica de ce",
                "raționament", "rationament", "calculate", "calculează", "calculeaza")
                .any { q.contains(it) } -> Task.REASONING
            else -> Task.CHAT
        }
        return when (task) {
            Task.THREE_D -> Decision(task, "Generare 3D detectată local; verifică instrumentele 3D și export", 512)
            Task.CODE -> Decision(task, "Sarcină de programare detectată local", 128)
            Task.TRANSLATION -> Decision(task, "Traducere detectată local", 96)
            Task.SUMMARIZATION -> Decision(task, "Rezumare detectată local", 128)
            Task.REASONING -> Decision(task, "Raționament detectat local", 128)
            Task.CHAT -> Decision(task, "Conversație generală", 64)
        }
    }

    /** Filename heuristics are hints, never proof of capability. */
    fun modelHint(task: Task, filename: String): Int {
        val name = filename.lowercase(java.util.Locale.ROOT)
        val analysis = ModelIntelligence.analyze(filename)
        if (!analysis.runnableCandidate) return -1000
        return when (task) {
            Task.THREE_D -> if (listOf("coder", "code", "instruct").any { name.contains(it) }) 20 else 0
            Task.CODE -> if (listOf("coder", "code", "starcoder", "deepseek-coder")
                .any { name.contains(it) }) 30 else 0
            Task.REASONING -> if (listOf("reason", "r1", "qwq")
                .any { name.contains(it) }) 20 else 0
            Task.TRANSLATION, Task.SUMMARIZATION, Task.CHAT ->
                if (listOf("instruct", "chat", "-it-", "_it_")
                    .any { name.contains(it) }) 10 else 0
        }
    }

    fun suggestModel(task: Task, installedNames: List<String>): String? =
        installedNames.asSequence()
            .filter { ModelIntelligence.analyze(it).runnableCandidate }
            .sortedWith(compareByDescending<String> { modelHint(task, it) }
                .thenBy { it.lowercase(java.util.Locale.ROOT) })
            .firstOrNull()
}
