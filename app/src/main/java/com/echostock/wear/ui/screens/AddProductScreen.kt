package com.echostock.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.VoiceText
import com.echostock.wear.ui.components.CircleIconButton
import com.echostock.wear.ui.components.KeepScreenOn
import com.echostock.wear.ui.components.PressButton
import com.echostock.wear.ui.components.VoiceOrbButton
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EyebrowStyle

/**
 * Pantalla para agregar un nuevo producto mediante voz (sin teclado, RNF-001) o texto directo.
 * El operario dicta el nombre (o lo escribe) tocando el VoiceOrb o el campo de texto,
 * ajusta la cantidad inicial con +/− o la corona y confirma con Guardar.
 */
@Composable
fun AddProductScreen(
    draft: VoiceText.NewProductDraft,
    saving: Boolean,
    onDictateDetails: () -> Unit,
    onAdjustQuantity: (delta: Int, haptic: Boolean) -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit = {},
) {
    KeepScreenOn()
    val focus = remember { FocusRequester() }
    val rotaryAccumulator = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val hasName = draft.name.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                rotaryAccumulator.floatValue += e.verticalScrollPixels
                val steps = (rotaryAccumulator.floatValue / 24f).toInt()
                if (steps != 0) {
                    rotaryAccumulator.floatValue -= steps * 24f
                    onAdjustQuantity(steps, false)
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(10.dp))
        Text("NUEVO PRODUCTO", style = EyebrowStyle, color = EchoColors.Signal)
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = draft.name,
            onValueChange = onNameChange,
            textStyle = TextStyle(
                color = EchoColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(EchoColors.Signal),
            singleLine = true,
            modifier = Modifier.padding(horizontal = 6.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(EchoColors.SurfaceHigh, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (draft.name.isBlank()) {
                        Text(
                            "Toca o dicta nombre",
                            fontSize = 12.sp,
                            color = EchoColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Cajas", "Cintas", "Guantes", "Tornillos").forEach { item ->
                val selected = draft.name.equals(item, ignoreCase = true)
                CompactChip(
                    onClick = { onNameChange(item) },
                    label = { Text(item, fontSize = 9.sp) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (selected) EchoColors.Signal else EchoColors.SurfaceHigh,
                        contentColor = if (selected) EchoColors.OnColor else EchoColors.OnSurface,
                    ),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${draft.locationCode} · ${draft.unit}",
            fontSize = 11.sp,
            color = EchoColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(
                onClick = { onAdjustQuantity(-1, true) },
                background = EchoColors.SurfaceHigh,
                size = 38.dp,
                description = "Restar uno",
                enabled = draft.quantity > 0 && !saving,
                repeatOnHold = true,
            ) { Icon(Icons.Rounded.Remove, null, tint = EchoColors.OnSurface, modifier = Modifier.size(22.dp)) }

            Text(
                draft.quantity.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = EchoColors.OnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 52.dp),
            )

            CircleIconButton(
                onClick = { onAdjustQuantity(1, true) },
                background = EchoColors.SurfaceHigh,
                size = 38.dp,
                description = "Sumar uno",
                enabled = !saving,
                repeatOnHold = true,
            ) { Icon(Icons.Rounded.Add, null, tint = EchoColors.OnSurface, modifier = Modifier.size(22.dp)) }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceOrbButton(
                onClick = onDictateDetails,
                size = 50.dp,
                description = "Dictar nombre y cantidad del producto",
                enabled = !saving,
            )

            PressButton(
                onClick = onSave,
                background = EchoColors.Match,
                modifier = Modifier.size(width = 100.dp, height = 46.dp),
                description = "Guardar nuevo producto",
                enabled = hasName && !saving,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = EchoColors.OnColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Guardar", color = EchoColors.OnColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
