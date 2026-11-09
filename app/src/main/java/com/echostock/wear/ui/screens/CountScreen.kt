package com.echostock.wear.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.Product
import com.echostock.wear.ui.components.CircleIconButton
import com.echostock.wear.ui.components.KeepScreenOn
import com.echostock.wear.ui.components.PressButton
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EyebrowStyle
import com.echostock.wear.ui.theme.QuantityStyle

/**
 * Registro de la cantidad real (RF-004) sin teclado (RNF-001): botones +/− grandes
 * (mantener pulsado repite), corona giratoria o dictado del número.
 */
@Composable
fun CountScreen(
    product: Product,
    counted: Int,
    submitting: Boolean,
    onAdjust: (delta: Int, haptic: Boolean) -> Unit,
    onDictate: () -> Unit,
    onSubmit: () -> Unit,
) {
    KeepScreenOn()
    val focus = remember { FocusRequester() }
    val rotaryAccumulator = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val diff = counted - product.systemQuantity
    val matches = diff == 0
    val accent = if (matches) EchoColors.Match else EchoColors.Discrepancy

    Column(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                // Un paso por cada ~24 px de giro: preciso sin tener que dar vueltas de más.
                rotaryAccumulator.floatValue += e.verticalScrollPixels
                val steps = (rotaryAccumulator.floatValue / 24f).toInt()
                if (steps != 0) {
                    rotaryAccumulator.floatValue -= steps * 24f
                    onAdjust(steps, false)
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(8.dp))
        Text("CONTEO REAL", style = EyebrowStyle, color = EchoColors.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleIconButton(
                onClick = { onAdjust(-1, true) },
                background = EchoColors.SurfaceHigh,
                size = 44.dp,
                description = "Restar uno",
                enabled = counted > 0 && !submitting,
                repeatOnHold = true,
            ) { Icon(Icons.Rounded.Remove, null, tint = EchoColors.OnSurface, modifier = Modifier.size(26.dp)) }

            AnimatedContent(
                targetState = counted,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(140)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(140)))
                        .togetherWith(slideOutVertically(tween(140)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(100)))
                },
                label = "count",
                modifier = Modifier.widthIn(min = 72.dp),
            ) { value ->
                Text(
                    value.toString(),
                    style = QuantityStyle,
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
            }

            CircleIconButton(
                onClick = { onAdjust(1, true) },
                background = EchoColors.SurfaceHigh,
                size = 44.dp,
                description = "Sumar uno",
                enabled = !submitting,
                repeatOnHold = true,
            ) { Icon(Icons.Rounded.Add, null, tint = EchoColors.OnSurface, modifier = Modifier.size(26.dp)) }
        }
        Text(
            when {
                matches -> "Igual al sistema"
                diff > 0 -> "+$diff sobre ${product.systemQuantity} del sistema"
                else -> "−${-diff} bajo ${product.systemQuantity} del sistema"
            },
            fontSize = 12.sp,
            color = if (matches) EchoColors.Match else EchoColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(
                onClick = onDictate,
                background = EchoColors.Signal,
                size = 48.dp,
                description = "Dictar cantidad",
                enabled = !submitting,
            ) { Icon(Icons.Rounded.Mic, null, tint = EchoColors.OnColor, modifier = Modifier.size(24.dp)) }

            PressButton(
                onClick = onSubmit,
                background = accent,
                modifier = Modifier.size(width = 104.dp, height = 48.dp),
                description = if (matches) "Confirmar conteo" else "Registrar discrepancia",
                enabled = !submitting,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = EchoColors.OnColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Registrar", color = EchoColors.OnColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
