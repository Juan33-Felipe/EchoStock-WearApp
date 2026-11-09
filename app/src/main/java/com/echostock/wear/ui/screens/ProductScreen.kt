package com.echostock.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.Product
import com.echostock.wear.ui.components.KeepScreenOn
import com.echostock.wear.ui.components.PressButton
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EyebrowStyle
import com.echostock.wear.ui.theme.QuantityStyle

/**
 * Lo que dice el sistema y dos salidas de un toque: verde = coincide (RF-003),
 * rojo = registrar otra cantidad (RF-004). Tocar la cifra la vuelve a leer en voz alta.
 */
@Composable
fun ProductScreen(
    product: Product,
    submitting: Boolean,
    onRepeat: () -> Unit,
    onMatch: () -> Unit,
    onMismatch: () -> Unit,
) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(10.dp))
        Text(product.locationCode, style = EyebrowStyle, color = EchoColors.Signal)
        Spacer(Modifier.height(2.dp))
        Text(
            product.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Column(
            Modifier.clickable(onClickLabel = "Repetir en voz alta", onClick = onRepeat),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(product.systemQuantity.toString(), style = QuantityStyle, color = EchoColors.OnSurface)
                Spacer(Modifier.width(4.dp))
                Text(product.unit, fontSize = 13.sp, color = EchoColors.Muted, modifier = Modifier.padding(bottom = 9.dp))
            }
            Text("SEGÚN SISTEMA", style = EyebrowStyle, color = EchoColors.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PressButton(
                onClick = onMismatch,
                background = EchoColors.Discrepancy,
                modifier = Modifier.size(52.dp),
                description = "No coincide, registrar cantidad real",
                enabled = !submitting,
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(24.dp))
            }
            PressButton(
                onClick = onMatch,
                background = EchoColors.Match,
                modifier = Modifier.size(width = 104.dp, height = 52.dp),
                description = "Coincide, confirmar conteo",
                enabled = !submitting,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Coincide", color = EchoColors.OnColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
