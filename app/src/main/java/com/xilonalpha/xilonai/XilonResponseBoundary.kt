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
        // Remove every completed reasoning block. Never expose an unfinished block
        // while the local model is still streaming its response.
        var visible = output
        while (true) {
            val start = visible.indexOf("<think>")
            if (start < 0) break
            val end = visible.indexOf("</think>", start + "<think>".length)
            if (end < 0) {
                visible = visible.substring(0, start)
                break
            }
            visible = visible.removeRange(start, end + "</think>".length)
        }
        // A non-thinking assistant prefill can close <think> in the prompt, so
        // the generated suffix may contain an orphan closing marker.
        visible = visible.replace("</think>", "")
        val end = markers.map { visible.indexOf(it) }.filter { it >= 0 }.minOrNull()
        return (if (end == null) visible else visible.substring(0, end)).trim()
    }
}
