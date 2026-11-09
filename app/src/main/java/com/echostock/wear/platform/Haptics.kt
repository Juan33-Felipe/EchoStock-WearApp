package com.echostock.wear.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Patrones de vibración distinguibles sin mirar el reloj (RNF-003).
 * Tiempos en ms: pares (espera, vibración).
 */
enum class Haptic(val timings: LongArray, val amplitudes: IntArray) {
    /** Botón pulsado: un toque seco. */
    TAP(longArrayOf(0, 18), intArrayOf(0, 180)),
    /** Voz entendida: dos toques cortos. */
    HEARD(longArrayOf(0, 30, 70, 30), intArrayOf(0, 200, 0, 200)),
    /** Conteo correcto registrado: un pulso largo y firme. */
    CONFIRMED(longArrayOf(0, 140), intArrayOf(0, 255)),
    /** Discrepancia registrada: tres pulsos. */
    DISCREPANCY(longArrayOf(0, 60, 80, 60, 80, 60), intArrayOf(0, 255, 0, 255, 0, 255)),
    /** Guardado sin conexión: largo-corto. */
    QUEUED(longArrayOf(0, 200, 110, 40), intArrayOf(0, 200, 0, 200)),
    /** Error o no entendido: zumbido largo. */
    ERROR(longArrayOf(0, 420), intArrayOf(0, 255)),
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val _events = MutableSharedFlow<Haptic>(extraBufferCapacity = 8)

    /**
     * Cada vibración también se publica aquí para pintar un pulso en pantalla:
     * en el emulador la vibración no se siente y así se aprecia en la demo.
     */
    val events: SharedFlow<Haptic> = _events.asSharedFlow()

    fun play(haptic: Haptic) {
        _events.tryEmit(haptic)
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amplitudes = if (v.hasAmplitudeControl()) haptic.amplitudes
        else haptic.amplitudes.map { if (it == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }.toIntArray()
        v.vibrate(VibrationEffect.createWaveform(haptic.timings, amplitudes, -1))
    }
}
