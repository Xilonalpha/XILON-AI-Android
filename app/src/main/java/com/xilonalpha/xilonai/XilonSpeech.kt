package com.xilonalpha.xilonai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Speech output constrained to locally installed TTS voices.
 * No network voices are ever selected.
 */
class XilonSpeech(context: Context, private val onStatus: (String) -> Unit) {
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                onStatus("Motorul vocal Android nu a putut fi inițializat.")
            } else {
                ready = true
                onStatus("Sinteză vocală pregătită; se verifică vocile offline.")
            }
        }
    }

    fun speak(text: String) {
        val tts = engine ?: return
        if (!ready) { onStatus("Motorul vocal nu este încă pregătit."); return }
        val language = Locale.getDefault()
        val candidates = tts.voices.orEmpty().filter { voice ->
            !voice.isNetworkConnectionRequired &&
                voice.locale.language == language.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
        }
        val chosen = candidates.firstOrNull()
        if (chosen == null) {
            onStatus("Nu există o voce offline instalată pentru limba ${language.displayLanguage}.")
            return
        }
        if (tts.setVoice(chosen) != TextToSpeech.SUCCESS) {
            onStatus("Vocea offline nu poate fi activată.")
            return
        }
        val result = tts.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "xilon-local-tts")
        onStatus(if (result == TextToSpeech.SUCCESS) "Redare vocală solicitată local."
            else "Sinteza vocală a eșuat.")
    }

    fun stop() { engine?.stop() }
    fun close() { engine?.stop(); engine?.shutdown(); engine = null; ready = false }
}
