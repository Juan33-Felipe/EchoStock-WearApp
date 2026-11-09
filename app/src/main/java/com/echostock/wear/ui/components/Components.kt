package com.echostock.wear.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.Connection
import com.echostock.wear.platform.Haptic
import com.echostock.wear.ui.SyncState
import com.echostock.wear.ui.theme.EchoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun Haptic.color(): Color = when (this) {
    Haptic.TAP -> EchoColors.OnSurface
    Haptic.HEARD -> EchoColors.Signal
    Haptic.CONFIRMED -> EchoColors.Match
    Haptic.DISCREPANCY -> EchoColors.Discrepancy
    Haptic.QUEUED -> EchoColors.Offline
    Haptic.ERROR -> EchoColors.Discrepancy
}

/**
 * Anillo que destella en el borde de la pantalla con cada vibración. Hace visible la
 * retroalimentación háptica (RNF-003) en el emulador, donde el motor no vibra.
 */
@Composable
fun HapticPulseOverlay(events: Flow<Haptic>, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    var color by remember { mutableStateOf(Color.White) }
    var strong by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.collect { h ->
            launch {
                color = h.color()
                strong = h != Haptic.TAP
                alpha.snapTo(if (strong) 1f else 0.45f)
                alpha.animateTo(0f, tween(if (strong) 650 else 260, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (alpha.value > 0f) {
                    val stroke = (if (strong) 7.dp else 4.dp).toPx()
                    drawCircle(
                        color = color.copy(alpha = alpha.value),
                        radius = size.minDimension / 2 - stroke / 2,
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(width = stroke),
                    )
                }
            },
    ) { content() }
}

/** Mantiene la pantalla encendida solo mientras se está contando (RNF-005). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Punto de color + texto con el estado de conexión y la cola offline. */
@Composable
fun StatusPill(sync: SyncState, operator: String, modifier: Modifier = Modifier) {
    val (dot, label) = when {
        sync.connection == Connection.OFFLINE ->
            EchoColors.Offline to if (sync.pending > 0) "Sin conexión · ${sync.pending} en cola" else "Sin conexión"
        sync.pending > 0 -> EchoColors.Offline to "Enviando ${sync.pending}…"
        sync.connection == Connection.ONLINE -> EchoColors.Match to "En línea · $operator"
        else -> EchoColors.Muted to "Conectando…"
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(EchoColors.Surface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = EchoColors.OnSurface, maxLines = 1)
    }
}

/**
 * Botón con respuesta táctil evidente: se hunde al presionar y rebota al soltar.
 * Si [repeatOnHold] es true, mantenerlo pulsado repite la acción (conteos rápidos).
 */
@Composable
fun PressButton(
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    description: String,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnClick(); true }
            }
            .pointerInput(enabled, repeatOnHold) {
                if (!enabled) return@pointerInput
                kotlinx.coroutines.coroutineScope {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            var repeated = false
                            val repeater = if (repeatOnHold) launch {
                                delay(450)
                                while (true) {
                                    repeated = true
                                    currentOnClick()
                                    delay(70)
                                }
                            } else null
                            val released = tryAwaitRelease()
                            repeater?.cancel()
                            pressed = false
                            // Si ya estaba repitiendo, soltar no suma un paso extra.
                            if (released && !repeated) currentOnClick()
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    background: Color,
    size: Dp,
    description: String,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    content: @Composable () -> Unit,
) = PressButton(onClick, background, Modifier.size(size), description, enabled, repeatOnHold, content)
