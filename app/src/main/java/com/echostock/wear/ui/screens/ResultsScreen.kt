package com.echostock.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.SearchHit
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EyebrowStyle

/** Elegir entre varias coincidencias: un toque (RNF-001). Se recorre con la corona. */
@Composable
fun ResultsScreen(
    title: String,
    hits: List<SearchHit>,
    offline: Boolean,
    listState: ScalingLazyListState,
    onSelect: (SearchHit) -> Unit,
) {
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        item {
            ListHeader {
                Text(
                    title,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
            }
        }
        if (offline) {
            item {
                Text(
                    "SIN CONEXIÓN · CATÁLOGO LOCAL",
                    style = EyebrowStyle,
                    color = EchoColors.Offline,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (hits.isEmpty()) {
            item { Text("Sin productos", color = EchoColors.Muted) }
        }
        items(hits, key = { it.product.sku }) { hit ->
            val p = hit.product
            Chip(
                onClick = { onSelect(hit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                secondaryLabel = {
                    Text(
                        "${p.locationCode} · ${p.sku}",
                        maxLines = 1,
                        color = EchoColors.Muted,
                        fontSize = 12.sp,
                    )
                },
                colors = ChipDefaults.chipColors(backgroundColor = EchoColors.Surface, contentColor = EchoColors.OnSurface),
            )
        }
    }
}
