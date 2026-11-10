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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
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
 * Lo que dice el sistema y salidas de un toque: verde = coincide (RF-003),
 * editar = registrar otra cantidad (RF-004), papelera = eliminar producto.
 * Tocar la cifra la vuelve a leer en voz alta.
 */
@Composable
fun ProductScreen(
    product: Product,
    submitting: Boolean,
    confirmingDelete: Boolean = false,
    onRepeat: () -> Unit,
    onMatch: () -> Unit,
    onMismatch: () -> Unit,
    onRequestDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(10.dp))
        Text("${product.locationCode} · ${product.sku}", style = EyebrowStyle, color = EchoColors.Signal)
        Spacer(Modifier.height(2.dp))
        Text(
            product.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        if (confirmingDelete) {
            Spacer(Modifier.height(6.dp))
            Text(
                "¿Eliminar del catálogo?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = EchoColors.Discrepancy,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PressButton(
                    onClick = onCancelDelete,
                    background = EchoColors.SurfaceHigh,
                    modifier = Modifier.size(46.dp),
                    description = "Cancelar eliminación",
                    enabled = !submitting,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = EchoColors.OnSurface, modifier = Modifier.size(22.dp))
                }
                PressButton(
                    onClick = onConfirmDelete,
                    background = EchoColors.Discrepancy,
                    modifier = Modifier.size(width = 104.dp, height = 46.dp),
                    description = "Confirmar eliminación del producto",
                    enabled = !submitting,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar", color = EchoColors.OnColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
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
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PressButton(
                    onClick = onRequestDelete,
                    background = EchoColors.SurfaceHigh,
                    modifier = Modifier.size(44.dp),
                    description = "Eliminar producto",
                    enabled = !submitting,
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = EchoColors.Discrepancy, modifier = Modifier.size(20.dp))
                }
                PressButton(
                    onClick = onMismatch,
                    background = EchoColors.Discrepancy,
                    modifier = Modifier.size(44.dp),
                    description = "No coincide, registrar cantidad real",
                    enabled = !submitting,
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(20.dp))
                }
                PressButton(
                    onClick = onMatch,
                    background = EchoColors.Match,
                    modifier = Modifier.size(width = 86.dp, height = 44.dp),
                    description = "Coincide, confirmar conteo",
                    enabled = !submitting,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("OK", color = EchoColors.OnColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
