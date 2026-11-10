package com.echostock.wear.platform

import android.content.Context
import android.util.Log
import com.echostock.wear.BuildConfig
import com.echostock.wear.domain.VoiceGrammar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

sealed interface ListenState {
    data object Idle : ListenState
    data object Preparing : ListenState
    data class Listening(val partial: String) : ListenState
}

class SpeechUnavailableException(cause: Throwable? = null) : Exception("Reconocimiento offline no disponible", cause)

/**
 * Voz a texto dentro del reloj con Vosk (RF-001), sin red ni servicios de Google:
 * - funciona en modo offline (RF-006) y en el emulador;
 * - un toque y empieza a escuchar, sin pantalla intermedia ni teclado (RNF-001);
 * - vocabulario cerrado al catálogo para resistir el ruido (RNF-004);
 * - el micrófono solo está abierto mientras se dicta y se corta solo al detectar
 *   el final de la frase o a los 8 s (RNF-005).
 */
class OfflineSpeech(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var model: Deferred<Model?>? = null
    private var stopCurrent: (() -> Unit)? = null

    /** Desempaqueta y carga el modelo en segundo plano para que el primer dictado sea inmediato. */
    fun prewarm() {
        loadModel()
    }

    private fun loadModel(): Deferred<Model?> = model ?: scope.async {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context, ASSET_DIR, "model",
                { m -> if (cont.isActive) cont.resume(m) },
                { e -> Log.e(TAG, "No se pudo cargar el modelo de voz", e); if (cont.isActive) cont.resume(null) },
            )
        }
    }.also { model = it }

    /**
     * Escucha una frase y devuelve las hipótesis (la mejor primero), o null si no se oyó nada.
     * @param grammar JSON de [VoiceGrammar]; null para dictado libre.
     */
    suspend fun listen(grammar: String?): List<String>? {
        _state.value = ListenState.Preparing
        val m = loadModel().await()
        if (m == null) {
            _state.value = ListenState.Idle
            throw SpeechUnavailableException()
        }
        val recognizer = try {
            if (grammar != null) Recognizer(m, SAMPLE_RATE, grammar) else Recognizer(m, SAMPLE_RATE)
        } catch (e: Exception) {
            _state.value = ListenState.Idle
            throw SpeechUnavailableException(e)
        }
        recognizer.setMaxAlternatives(MAX_ALTERNATIVES)

        return suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            lateinit var stop: () -> Unit
            lateinit var release: () -> Unit

            fun finish(result: List<String>?) {
                if (!finished.compareAndSet(false, true)) return
                release()
                recognizer.close()
                stopCurrent = null
                _state.value = ListenState.Idle
                if (cont.isActive) cont.resume(result?.takeIf { it.isNotEmpty() })
            }

            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (!finished.get()) _state.value = ListenState.Listening(VoiceGrammar.parsePartial(hypothesis))
                }

                /** Fin de frase detectado (silencio): si hay texto, terminamos ya. */
                override fun onResult(hypothesis: String?) {
                    val alternatives = VoiceGrammar.parseResult(hypothesis)
                    if (alternatives.isNotEmpty()) finish(alternatives)
                }

                override fun onFinalResult(hypothesis: String?) = finish(VoiceGrammar.parseResult(hypothesis))
                override fun onError(exception: Exception?) {
                    Log.e(TAG, "Error de reconocimiento", exception)
                    finish(null)
                }

                override fun onTimeout() = finish(null)
            }

            val injected = debugInjectedAudio()
            if (injected != null) {
                // Solo en builds debug: permite probar el reconocimiento en el emulador con un WAV.
                val stream = FileInputStream(injected).apply { skip(WAV_HEADER_BYTES) }
                val service = SpeechStreamService(recognizer, stream, SAMPLE_RATE)
                stop = { service.stop() }
                release = { runCatching { stream.close() }; injected.delete() }
                service.start(listener)
            } else {
                val service = SpeechService(recognizer, SAMPLE_RATE)
                stop = { service.stop() }
                release = { service.shutdown() }
                service.startListening(listener, LISTEN_TIMEOUT_MS)
            }
            _state.value = ListenState.Listening("")
            stopCurrent = stop
            cont.invokeOnCancellation { stop(); finish(null) }
        }
    }

    /** El operario tocó para terminar: se entrega lo que se haya oído hasta ahora. */
    fun stop() {
        stopCurrent?.invoke()
    }

    private fun debugInjectedAudio(): File? =
        if (BuildConfig.DEBUG) File(context.filesDir, "voice-inject.wav").takeIf { it.exists() } else null

    private companion object {
        const val TAG = "OfflineSpeech"
        const val ASSET_DIR = "model-es"
        const val SAMPLE_RATE = 16_000f
        const val MAX_ALTERNATIVES = 3
        const val LISTEN_TIMEOUT_MS = 8_000
        const val WAV_HEADER_BYTES = 44L
    }
}
