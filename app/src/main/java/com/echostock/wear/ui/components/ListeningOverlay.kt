package com.echostock.wear.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.echostock.wear.platform.ListenState
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EyebrowStyle

/**
 * Escucha en curso: ondas que salen del micrófono y la transcripción en vivo.
 * Tocar en cualquier parte termina el dictado con lo oído hasta ese momento.
 */
@Composable
fun ListeningOverlay(state: ListenState, prompt: String, onStop: () -> Unit) {
    AnimatedVisibility(visible = state != ListenState.Idle, enter = fadeIn(), exit = fadeOut()) {
        val partial = (state as? ListenState.Listening)?.partial.orEmpty()
        val preparing = state == ListenState.Preparing

        Box(
            Modifier
                .fillMaxSize()
                .background(EchoColors.Background.copy(alpha = 0.94f))
                .clickable(onClickLabel = "Terminar dictado", onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(if (preparing) "PREPARANDO…" else "ESCUCHANDO", style = EyebrowStyle, color = EchoColors.Signal)
                Spacer(Modifier.height(10.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (!preparing) Ripples()
                    VoiceOrb(
                        modifier = Modifier.size(72.dp),
                        activity = when {
                            preparing -> 0.35f
                            partial.isNotEmpty() -> 1.0f
                            else -> 0.78f
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    partial.ifEmpty { prompt },
                    color = if (partial.isEmpty()) EchoColors.Muted else EchoColors.OnSurface,
                    fontSize = if (partial.isEmpty()) 13.sp else 15.sp,
                    fontWeight = if (partial.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text("Toca para terminar", fontSize = 11.sp, color = EchoColors.Muted)
            }
        }
    }
}

/** Tres ondas concéntricas desfasadas que se expanden y se desvanecen. */
@Composable
private fun Ripples() {
    val t = rememberInfiniteTransition(label = "ripples")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.size(120.dp)) {
        val base = 28.dp.toPx()
        val max = size.minDimension / 2
        repeat(3) { i ->
            val p = (phase + i / 3f) % 1f
            drawCircle(
                color = EchoColors.Signal.copy(alpha = (1f - p) * 0.55f),
                radius = base + (max - base) * p,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
