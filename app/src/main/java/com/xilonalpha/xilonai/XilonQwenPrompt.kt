package com.xilonalpha.xilonai

/**
 * Qwen3 ChatML non-thinking prefill. The plain /no_think instruction is not
 * sufficient for every GGUF chat template; the assistant header may still
 * begin a hidden reasoning section. Only modify known ChatML assistant tails.
 */
object XilonQwenPrompt {
    fun forceNonThinkingAssistantPrefix(prompt: String, enabled: Boolean): String {
        if (!enabled) return prompt
        // Do not rewrite arbitrary templates (Llama, Gemma, etc.).
        val assistantHeader = "<|im_start|>assistant"
        val headerStart = prompt.lastIndexOf(assistantHeader)
        if (headerStart < 0) return prompt
        val tail = prompt.substring(headerStart + assistantHeader.length)
        // A template may include an empty assistant header, with or without a
        // channel marker. Avoid altering already generated assistant content.
        if (tail != "\n" && tail != "\n\n") return prompt
        return prompt + "<think>\n\n</think>\n\n"
    }
}
