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
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (currentSession == session) onStatus("Ascult offline…") }
            override fun onBeginningOfSpeech() { if (currentSession == session) onStatus("Te ascult…") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (currentSession == session) onStatus("Procesez vocea local…") }
            override fun onError(error: Int) {
                if (currentSession != session) return
                onStatus("Recunoașterea offline a eșuat (cod $error).")
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
        service.startListening(request)
    }

    fun stop() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        session++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
