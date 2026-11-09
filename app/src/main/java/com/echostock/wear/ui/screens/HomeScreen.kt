package com.echostock.wear.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.echostock.wear.ui.HomeMessage
import com.echostock.wear.ui.SyncState
import com.echostock.wear.ui.components.CircleIconButton
import com.echostock.wear.ui.components.StatusPill
import com.echostock.wear.ui.theme.EchoColors

/**
 * Inicio: un solo toque para dictar (RF-001, RNF-001). Sin teclado en ningún caso;
 * el catálogo es el respaldo si el reconocedor de voz no está disponible.
 */
@Composable
fun HomeScreen(
    message: HomeMessage?,
    sync: SyncState,
    operator: String,
    onDictate: () -> Unit,
    onBrowse: () -> Unit,
) {
    val searching = message is HomeMessage.Searching

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(78.dp),
                    indicatorColor = EchoColors.Signal,
                    trackColor = EchoColors.Surface,
                    strokeWidth = 4.dp,
                )
            } else {
                IdleHalo()
            }
            CircleIconButton(
                onClick = onDictate,
                background = EchoColors.Signal,
                size = 64.dp,
                description = "Dictar producto, SKU o ubicación",
                enabled = !searching,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        val (text, color) = when (message) {
            is HomeMessage.Searching -> "Buscando “${message.query}”" to EchoColors.OnSurface
            is HomeMessage.NotFound -> "No encontré “${message.query}”" to EchoColors.Discrepancy
            HomeMessage.NotUnderstood -> "No entendí, toca y repite" to EchoColors.Discrepancy
            HomeMessage.VoiceUnavailable -> "Voz no disponible: usa el catálogo" to EchoColors.Offline
            null -> "Dicta producto, SKU o ubicación" to EchoColors.Muted
        }
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        StatusPill(sync, operator)
        Spacer(Modifier.height(4.dp))
        CompactChip(
            onClick = onBrowse,
            label = { Text("Catálogo", fontSize = 12.sp) },
            icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = ChipDefaults.chipColors(backgroundColor = EchoColors.SurfaceHigh, contentColor = EchoColors.OnSurface),
        )
    }
}

/** Halo que "respira" alrededor del micrófono: invita a tocar sin ruido visual. */
@Composable
private fun IdleHalo() {
    val transition = rememberInfiniteTransition(label = "halo")
    val s by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "haloScale",
    )
    Box(
        Modifier
            .size(78.dp)
            .scale(s)
            .then(Modifier.padding(0.dp)),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(EchoColors.Signal.copy(alpha = 0.16f))
        }
    }
}
