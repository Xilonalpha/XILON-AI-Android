package com.xilonalpha.xilonai

/**
 * Prevents leaked chat-template role markers from appearing in the UI or being
 * saved to conversation history. This is a display safeguard, not a substitute
 * for correct model-specific prompt formatting and native stop tokens.
 */
object XilonResponseBoundary {
    private val markers = listOf(
        "<|im_end|>", "<|im_start|>", "<|endoftext|>",
        "<|eot_id|>", "<|start_header_id|>", "<|end_header_id|>",
        "[eot_id]", "[start_header_id]", "[end_header_id]",
        "[INST]", "[/INST]", "</s>", "<s>"
    )
    fun clean(output: String): String {
        // Hide incomplete reasoning during streaming; expose only the final answer.
        val reasoningStart = output.indexOf("<think>")
        val visible = if (reasoningStart >= 0) {
            val reasoningEnd = output.indexOf("</think>", reasoningStart + 7)
            if (reasoningEnd < 0) return ""
            output.removeRange(reasoningStart, reasoningEnd + 8)
        } else output
        val end = markers.map { visible.indexOf(it) }.filter { it >= 0 }.minOrNull()
        return (if (end == null) visible else visible.substring(0, end)).trim()
    }
}
