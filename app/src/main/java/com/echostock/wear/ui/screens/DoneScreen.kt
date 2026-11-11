package com.echostock.wear.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.echostock.wear.domain.AuditResult
import com.echostock.wear.domain.SubmitOutcome
import com.echostock.wear.ui.Done
import com.echostock.wear.ui.theme.DarkEchoColors
import com.echostock.wear.ui.theme.EchoColorScheme
import com.echostock.wear.ui.theme.EchoColors
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Pantalla completa de color universal (RNF-002): se entiende con un vistazo de reojo.
 * Vuelve sola al inicio para encadenar el siguiente producto.
 */
@Composable
fun DoneScreen(done: Done, onFinish: () -> Unit) {
    val look = done.look(EchoColors.current)
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(done) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(done) {
        delay(if (done.outcome is SubmitOutcome.Rejected) 4_000 else 2_400)
        onFinish()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(look.background)
            .clickable(onClickLabel = "Siguiente producto", onClick = onFinish)
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(look.icon, contentDescription = null, tint = EchoColors.OnColor, modifier = Modifier.size(52.dp).scale(pop.value))
        Spacer(Modifier.height(6.dp))
        Text(
            look.title,
            color = EchoColors.OnColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            look.subtitle,
            color = EchoColors.OnColor.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class Look(val background: Color, val icon: ImageVector, val title: String, val subtitle: String)

private fun Done.look(scheme: EchoColorScheme = DarkEchoColors): Look = when (outcome) {
    is SubmitOutcome.Rejected -> Look(scheme.discrepancy, Icons.Rounded.ErrorOutline, "No registrado", outcome.reason)
    SubmitOutcome.QueuedOffline -> Look(
        scheme.offline, Icons.Rounded.CloudOff, "Guardado en el reloj",
        "Se enviará al recuperar señal",
    )
    SubmitOutcome.Synced -> when (result) {
        AuditResult.MATCH -> Look(scheme.match, Icons.Rounded.CheckCircle, "Confirmado", product.name)
        AuditResult.DISCREPANCY -> Look(
            scheme.discrepancy, Icons.Rounded.ReportProblem,
            if (difference > 0) "Sobran ${abs(difference)}" else "Faltan ${abs(difference)}",
            "Discrepancia enviada a revisión",
        )
    }
}
