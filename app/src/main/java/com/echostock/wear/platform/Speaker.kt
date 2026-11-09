package com.echostock.wear.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Texto a voz (RF-002). Se inicializa una vez y se libera con la Activity para no
 * mantener el motor TTS vivo en segundo plano (RNF-005).
 */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = configureLanguage()
            pending?.let { speak(it) }
            pending = null
        } else {
            Log.w(TAG, "TTS no disponible: $status")
        }
    }

    private fun configureLanguage(): Boolean {
        for (locale in listOf(Locale("es", "CO"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))) {
            val r = tts.setLanguage(locale)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return true
        }
        Log.w(TAG, "Sin voz en español; se usa el idioma por defecto")
        tts.setLanguage(Locale.getDefault())
        return true
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
