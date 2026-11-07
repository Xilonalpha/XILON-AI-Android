package com.xilonalpha.xilonai

/** Extracts a complete Wavefront OBJ mesh from a model response. */
object XilonObjExport {
    private val vertex = Regex("""^v\\s+[-+0-9.eE]+\\s+[-+0-9.eE]+\\s+[-+0-9.eE]+(?:\\s+.*)?$""")
    private val face = Regex("""^f\\s+\\S+\\s+\\S+\\s+\\S+(?:\\s+.*)?$""")
    private val allowed = Regex("""^(?:v|vt|vn|f|o|g|s|usemtl|mtllib|l|p)\\s+.*$""")

    fun extract(answer: String): String? {
        val fenced = Regex("(?s)```(?:obj|wavefront)?\\s*\\n(.*?)```", RegexOption.IGNORE_CASE)
            .findAll(answer).map { it.groupValues[1] }.toList()
        val candidates = fenced + answer
        for (candidate in candidates) {
            val lines = candidate.lines().map { it.trim() }
                .filter { it.isNotEmpty() && (it.startsWith("#") || allowed.matches(it)) }
            if (lines.count { vertex.matches(it) } >= 3 && lines.any { face.matches(it) }) {
                return lines.joinToString("\n", postfix = "\n")
            }
        }
        return null
    }
}
