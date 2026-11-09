package com.xilonalpha.xilonai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Android on-device speech recognition only. Never falls back to cloud recognition.
 * Availability depends on the OS and installed offline language packs.
 */
class XilonVoice(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private var listening = false

    fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun listen(onText: (String) -> Unit, onStatus: (String) -> Unit) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (!available()) {
            onStatus("Recunoașterea vocală offline nu este disponibilă pe acest dispozitiv.")
            return
        }
        stop()
        val currentSession = session
        val service = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = service
        listening = true
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (currentSession == session) onStatus("Ascult offline…") }
            override fun onBeginningOfSpeech() { if (currentSession == session) onStatus("Te ascult…") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (currentSession == session) onStatus("Procesez vocea local…") }
            override fun onError(error: Int) {
                if (currentSession != session) return
                onStatus(errorMessage(error))
                stop()
            }
            override fun onResults(results: Bundle?) {
                if (currentSession != session) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onText(text)
                onStatus(if (text.isBlank()) "Nu am identificat cuvinte." else "Dictare completă.")
                stop()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (currentSession != session) return
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) onStatus("Recunoscut parțial: " + partial.take(120))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            service.startListening(request)
        } catch (e: RuntimeException) {
            stop()
            onStatus("Nu pot porni microfonul offline: ${e.message ?: "eroare Android"}")
        }
    }

    fun isListening(): Boolean = listening

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Eroare la captarea sunetului. Verifică microfonul."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisiunea microfonului lipsește."
        SpeechRecognizer.ERROR_NO_MATCH -> "Nu am recunoscut cuvinte. Încearcă din nou."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nu s-a detectat voce. Încearcă din nou."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recunoașterea vocală este ocupată. Încearcă din nou."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Limba selectată nu este acceptată de recunoașterea offline."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Pachetul vocal offline pentru această limbă nu este instalat."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Serviciul de recunoaștere offline nu a răspuns. Nu se folosește recunoaștere cloud."
        else -> "Recunoașterea vocală offline a eșuat (cod $code)."
    }

    fun stop() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        session++
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
