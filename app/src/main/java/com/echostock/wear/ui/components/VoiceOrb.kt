package com.echostock.wear.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Paleta de pigmentos inspirada en `@beui/voice-orb`:
 * azul eléctrico, cian turquesa, índigo suave y destello cálido.
 */
object VoiceOrbDefaults {
    val Colors = listOf(
        Color(0xFF4F8CFF), // Azul eléctrico (core)
        Color(0xFF2DD4BF), // Turquesa / cian líquido
        Color(0xFF5AD8FF), // EchoColors.Signal
        Color(0xFF818CF8), // Índigo / violeta suave
        Color(0xFFFFD166), // Destello cálido (pigmento secundario)
    )
}

/**
 * Recreación nativa en Jetpack Compose de `@beui/voice-orb`:
 * Esfera líquida con respiración orgánica, pigmentos fluidos, reflejos especulares
 * y deformación reactiva al nivel de actividad de voz ([activity] entre 0f y 1f).
 */
@Composable
fun VoiceOrb(
    modifier: Modifier = Modifier,
    activity: Float = 0f,
    enabled: Boolean = true,
    colors: List<Color> = VoiceOrbDefaults.Colors,
) {
    val smoothActivity by animateFloatAsState(
        targetValue = if (enabled) activity.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "orbActivity",
    )

    val transition = rememberInfiniteTransition(label = "voiceOrb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbPhase",
    )
    val fastPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbFastPhase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbBreathe",
    )

    val c0 = colors.getOrElse(0) { Color(0xFF4F8CFF) }
    val c1 = colors.getOrElse(1) { Color(0xFF2DD4BF) }
    val c2 = colors.getOrElse(2) { Color(0xFF5AD8FF) }
    val c3 = colors.getOrElse(3) { Color(0xFF818CF8) }
    val c4 = colors.getOrElse(4) { Color(0xFFFFD166) }

    val path = remember { Path() }
    val innerPath = remember { Path() }

    Canvas(modifier = modifier) {
        val alphaMultiplier = if (enabled) 1f else 0.4f
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = min(size.width, size.height)

        // Radio base con respiración y expansión por actividad de voz
        val baseRadius = (minDim * 0.36f) * breathe * (1f + smoothActivity * 0.14f)
        val time = phase + fastPhase * smoothActivity * 0.65f

        // 1. Halo ambiental exterior (resplandor suave tipo shader)
        val glowRadius = baseRadius * (1.35f + smoothActivity * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    c2.copy(alpha = (0.38f + smoothActivity * 0.25f) * alphaMultiplier),
                    c0.copy(alpha = (0.18f + smoothActivity * 0.12f) * alphaMultiplier),
                    Color.Transparent,
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )

        // 2. Silueta líquida principal con distorsión multi-armónica
        buildLiquidOrbPath(
            path = path,
            center = center,
            baseRadius = baseRadius,
            phase = time,
            fastPhase = fastPhase,
            activity = smoothActivity,
            amplitudeScale = 1f,
        )

        // Gradiente principal con centro desplazado dinámicamente (pigmento vivo)
        val focalOffset = Offset(
            x = center.x + cos(phase) * baseRadius * 0.28f,
            y = center.y + sin(phase * 0.8f) * baseRadius * 0.28f,
        )
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(
                    c2.copy(alpha = alphaMultiplier),
                    c1.copy(alpha = alphaMultiplier),
                    c0.copy(alpha = alphaMultiplier),
                    c3.copy(alpha = 0.92f * alphaMultiplier),
                ),
                center = focalOffset,
                radius = baseRadius * 1.25f,
            ),
        )

        // 3. Lóbulo interno de pigmento secundario en movimiento (efecto fluido)
        buildLiquidOrbPath(
            path = innerPath,
            center = Offset(
                x = center.x + sin(phase * 1.2f) * baseRadius * 0.12f,
                y = center.y + cos(phase * 0.9f) * baseRadius * 0.12f,
            ),
            baseRadius = baseRadius * 0.76f,
            phase = -time * 1.15f,
            fastPhase = fastPhase * 1.3f,
            activity = smoothActivity,
            amplitudeScale = 1.25f,
        )
        drawPath(
            path = innerPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    c4.copy(alpha = (0.42f + smoothActivity * 0.28f) * alphaMultiplier),
                    c1.copy(alpha = (0.55f + smoothActivity * 0.20f) * alphaMultiplier),
                    Color.Transparent,
                ),
                center = Offset(
                    x = center.x - cos(fastPhase) * baseRadius * 0.22f,
                    y = center.y - sin(fastPhase) * baseRadius * 0.22f,
                ),
                radius = baseRadius * 0.9f,
            ),
            blendMode = BlendMode.Screen,
        )

        // 4. Brillo especular superior (sensación de esfera de cristal líquido 3D)
        val highlightCenter = Offset(
            x = center.x - baseRadius * 0.26f + cos(phase) * baseRadius * 0.06f,
            y = center.y - baseRadius * 0.28f + sin(phase) * baseRadius * 0.06f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f * alphaMultiplier),
                    Color.White.copy(alpha = 0.18f * alphaMultiplier),
                    Color.Transparent,
                ),
                center = highlightCenter,
                radius = baseRadius * 0.52f,
            ),
            radius = baseRadius * 0.52f,
            center = highlightCenter,
        )

        // 5. Borde iridiscente sutil (rim light)
        drawPath(
            path = path,
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f * alphaMultiplier),
                    c2.copy(alpha = 0.35f * alphaMultiplier),
                    c4.copy(alpha = (0.35f + smoothActivity * 0.3f) * alphaMultiplier),
                    c3.copy(alpha = 0.35f * alphaMultiplier),
                    Color.White.copy(alpha = 0.55f * alphaMultiplier),
                ),
                center = center,
            ),
            style = Stroke(width = (1.5f + smoothActivity * 1f).dp.toPx()),
        )
    }
}

private fun DrawScope.buildLiquidOrbPath(
    path: Path,
    center: Offset,
    baseRadius: Float,
    phase: Float,
    fastPhase: Float,
    activity: Float,
    amplitudeScale: Float,
) {
    path.reset()
    val points = 72
    val idleAmp = baseRadius * 0.035f * amplitudeScale
    val activeAmp = baseRadius * (0.14f * activity) * amplitudeScale
    val totalAmp = idleAmp + activeAmp

    for (i in 0 until points) {
        val theta = (i.toFloat() / points) * (2.0 * PI).toFloat()
        val wave1 = sin(theta * 3f + phase) * 0.50f
        val wave2 = cos(theta * 4f - fastPhase * 1.4f) * (0.30f + activity * 0.25f)
        val wave3 = sin(theta * 6f + fastPhase * 2.1f) * (0.15f + activity * 0.35f)
        val r = baseRadius + totalAmp * (wave1 + wave2 + wave3)
        val x = center.x + cos(theta) * r
        val y = center.y + sin(theta) * r
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
}

/**
 * Botón interactivo con el componente [VoiceOrb] como icono/superficie principal.
 * Conserva la respuesta táctil elástica y la semántica de accesibilidad de EchoStock.
 */
@Composable
fun VoiceOrbButton(
    onClick: () -> Unit,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
    activity: Float = 0.12f,
    enabled: Boolean = true,
    colors: List<Color> = VoiceOrbDefaults.Colors,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "orbPress",
    )
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnClick(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) currentOnClick()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        VoiceOrb(
            modifier = Modifier.fillMaxSize(),
            activity = if (pressed) (activity + 0.35f).coerceAtMost(1f) else activity,
            enabled = enabled,
            colors = colors,
        )
    }
}
