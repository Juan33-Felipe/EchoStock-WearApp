package com.echostock.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * Paleta de alto contraste para naves con poca luz o deslumbramiento (RNF-002):
 * fondo negro puro, texto blanco y colores semánticos universales.
 */
object EchoColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF161B21)
    val SurfaceHigh = Color(0xFF222932)
    val OnSurface = Color(0xFFFFFFFF)
    val Muted = Color(0xFFA9B3BD)

    /** Marca / acción neutra (dictar). */
    val Signal = Color(0xFF5AD8FF)

    /** Verde = correcto. */
    val Match = Color(0xFF2EE07E)

    /** Rojo = discrepancia. */
    val Discrepancy = Color(0xFFFF4F4F)

    /** Ámbar = guardado sin conexión / atención. */
    val Offline = Color(0xFFFFB627)

    val OnColor = Color(0xFF000000)
}

private val WearColors = Colors(
    primary = EchoColors.Signal,
    primaryVariant = EchoColors.Signal,
    secondary = EchoColors.Match,
    secondaryVariant = EchoColors.Match,
    error = EchoColors.Discrepancy,
    background = EchoColors.Background,
    surface = EchoColors.Surface,
    onPrimary = EchoColors.OnColor,
    onSecondary = EchoColors.OnColor,
    onError = EchoColors.OnColor,
    onBackground = EchoColors.OnSurface,
    onSurface = EchoColors.OnSurface,
    onSurfaceVariant = EchoColors.Muted,
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
fun EchoStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = WearColors, content = content)
}
