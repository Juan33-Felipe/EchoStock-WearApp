package com.echostock.wear.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Frase dicha por el reloj; [id] distingue repeticiones de la misma frase. */
data class Caption(val id: Long, val text: String)

/**
 * Texto a voz (RF-002). Se inicializa una vez y se libera con la Activity para no
 * mantener el motor TTS vivo en segundo plano (RNF-005).
 */
class Speaker(context: Context) {
    private var ready = false
    private var counter = 0L

    private val _caption = MutableStateFlow<Caption?>(null)

    /**
     * Subtítulo de lo último dicho. Se muestra en pantalla para entornos muy ruidosos y
     * para dispositivos sin motor TTS instalado (como el emulador de Wear OS).
     */
    val caption: StateFlow<Caption?> = _caption.asStateFlow()
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = configureLanguage()
            pending?.let { speakNow(it) }
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
        _caption.value = Caption(++counter, text)
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        pending = null
        _caption.value = null
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
