package com.xilonalpha.xilonai

import org.junit.Assert.assertEquals
import org.junit.Test

class XilonQwenPromptTest {
    @Test fun addsNonThinkingPrefillOnlyToEmptyAssistantHeader() {
        val prompt = "<|im_start|>user\nSalut<|im_end|>\n<|im_start|>assistant\n"
        assertEquals(prompt + "<think>\n\n</think>\n\n",
            XilonQwenPrompt.forceNonThinkingAssistantPrefix(prompt, true))
    }

    @Test fun leavesOtherTemplatesAndExistingAssistantTextUnchanged() {
        val plain = "User: Salut\nAssistant:"
        val populated = "<|im_start|>assistant\nSalut"
        assertEquals(plain, XilonQwenPrompt.forceNonThinkingAssistantPrefix(plain, true))
        assertEquals(populated, XilonQwenPrompt.forceNonThinkingAssistantPrefix(populated, true))
    }

    @Test fun disabledModeDoesNotChangePrompt() {
        val prompt = "<|im_start|>assistant\n"
        assertEquals(prompt, XilonQwenPrompt.forceNonThinkingAssistantPrefix(prompt, false))
    }
}
