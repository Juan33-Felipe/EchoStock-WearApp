package com.echostock.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.echostock.wear.ui.components.StatusPill
import com.echostock.wear.ui.components.VoiceOrbButton
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
    onAddProduct: () -> Unit = {},
) {
    val searching = message is HomeMessage.Searching

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(18.dp))

        Box(contentAlignment = Alignment.Center) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(76.dp),
                    indicatorColor = EchoColors.Signal,
                    trackColor = EchoColors.Surface,
                    strokeWidth = 4.dp,
                )
            }
            VoiceOrbButton(
                onClick = onDictate,
                size = 76.dp,
                description = "Dictar producto, SKU, ubicación o comando",
                activity = if (searching) 0.55f else 0.15f,
                enabled = !searching,
            )
        }

        Spacer(Modifier.height(6.dp))
        val (text, color) = when (message) {
            is HomeMessage.Searching -> "Buscando “${message.query}”" to EchoColors.OnSurface
            is HomeMessage.NotFound -> "No encontré “${message.query}”" to EchoColors.Discrepancy
            is HomeMessage.ProductDeleted -> "Eliminado: ${message.name}" to EchoColors.Match
            HomeMessage.NotUnderstood -> "No entendí, toca y repite" to EchoColors.Discrepancy
            HomeMessage.VoiceUnavailable -> "Voz no disponible: usa el catálogo" to EchoColors.Offline
            null -> "Dicta producto, “agregar…” o “eliminar…”" to EchoColors.Muted
        }
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        StatusPill(sync, operator)
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onBrowse,
                label = { Text("Catálogo", fontSize = 11.sp) },
                icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(15.dp)) },
                colors = ChipDefaults.chipColors(backgroundColor = EchoColors.SurfaceHigh, contentColor = EchoColors.OnSurface),
            )
            CompactChip(
                onClick = onAddProduct,
                label = { Text("Nuevo", fontSize = 11.sp) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                colors = ChipDefaults.chipColors(backgroundColor = EchoColors.SurfaceHigh, contentColor = EchoColors.Signal),
            )
        }
    }
}
