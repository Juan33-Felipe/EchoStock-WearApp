package com.echostock.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * Esquema de colores para EchoStock (RNF-002: Alto contraste en naves y almacenes).
 */
data class EchoColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val muted: Color,
    val signal: Color,
    val match: Color,
    val discrepancy: Color,
    val offline: Color,
    val onColor: Color,
    val isDark: Boolean,
)

/**
 * Paleta de alto contraste para naves con poca luz (RNF-002 / RNF-005):
 * fondo negro puro (OLED), texto blanco y colores semánticos universales.
 */
val DarkEchoColors = EchoColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF161B21),
    surfaceHigh = Color(0xFF222932),
    onSurface = Color(0xFFFFFFFF),
    muted = Color(0xFFA9B3BD),
    signal = Color(0xFF5AD8FF),
    match = Color(0xFF2EE07E),
    discrepancy = Color(0xFFFF4F4F),
    offline = Color(0xFFFFB627),
    onColor = Color(0xFF000000),
    isDark = true,
)

/**
 * Paleta de modo claro para áreas con alta iluminación o deslumbramiento en almacén (muelles, exteriores).
 * Fondo blanco limpio, texto oscuro con alto contraste y colores semánticos de máxima visibilidad.
 */
val LightEchoColors = EchoColorScheme(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE5E9EE),
    onSurface = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    signal = Color(0xFF0284C7),
    match = Color(0xFF16A34A),
    discrepancy = Color(0xFFDC2626),
    offline = Color(0xFFD97706),
    onColor = Color(0xFFFFFFFF),
    isDark = false,
)

val LocalEchoColors = staticCompositionLocalOf { DarkEchoColors }

/**
 * Acceso directo a los colores del tema actual según el modo activo.
 */
object EchoColors {
    val current: EchoColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current

    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.background

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.surface

    val SurfaceHigh: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.surfaceHigh

    val OnSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.onSurface

    val Muted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.muted

    val Signal: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.signal

    val Match: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.match

    val Discrepancy: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.discrepancy

    val Offline: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.offline

    val OnColor: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalEchoColors.current.onColor
}

private fun toWearColors(scheme: EchoColorScheme): Colors = Colors(
    primary = scheme.signal,
    primaryVariant = scheme.signal,
    secondary = scheme.match,
    secondaryVariant = scheme.match,
    error = scheme.discrepancy,
    background = scheme.background,
    surface = scheme.surface,
    onPrimary = scheme.onColor,
    onSecondary = scheme.onColor,
    onError = scheme.onColor,
    onBackground = scheme.onSurface,
    onSurface = scheme.onSurface,
    onSurfaceVariant = scheme.muted,
)

/** Cifras grandes con dígitos de ancho fijo para que no "bailen" al cambiar. */
val QuantityStyle = TextStyle(
    fontSize = 46.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1).sp,
)

val EyebrowStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp,
)

@Composable
fun EchoStockTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val echoColors = if (darkTheme) DarkEchoColors else LightEchoColors
    val wearColors = toWearColors(echoColors)

    CompositionLocalProvider(LocalEchoColors provides echoColors) {
        MaterialTheme(colors = wearColors, content = content)
    }
}
