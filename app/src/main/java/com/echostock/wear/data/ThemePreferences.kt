package com.echostock.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistencia de preferencia de tema visual (RNF-002: modo oscuro / claro para almacén).
 * Por defecto modo oscuro (ahorro de batería en pantallas OLED), conmutable a modo claro
 * para áreas con luz directa o deslumbramiento.
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("echostock_theme", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
        _isDarkMode.value = dark
    }

    fun toggle(): Boolean {
        val newMode = !_isDarkMode.value
        setDarkMode(newMode)
        return newMode
    }

    companion object {
        private const val KEY_DARK_MODE = "is_dark_mode"
    }
}
