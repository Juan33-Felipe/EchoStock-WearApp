package com.echostock.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.echostock.wear.platform.rememberVoiceInput
import com.echostock.wear.ui.AuditViewModel
import com.echostock.wear.ui.Route
import com.echostock.wear.ui.components.HapticPulseOverlay
import com.echostock.wear.ui.screens.CountScreen
import com.echostock.wear.ui.screens.DoneScreen
import com.echostock.wear.ui.screens.HomeScreen
import com.echostock.wear.ui.screens.ProductScreen
import com.echostock.wear.ui.screens.ResultsScreen
import com.echostock.wear.ui.theme.EchoStockTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AuditViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            EchoStockTheme {
                HapticPulseOverlay(viewModel.haptics.events) {
                    EchoStockNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
private fun EchoStockNavigation(vm: AuditViewModel) {
    val nav = rememberSwipeDismissableNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val dictateProduct = rememberVoiceInput(vm::onSearchVoice)
    val dictateCount = rememberVoiceInput(vm::onCountVoice)

    LaunchedEffect(nav) {
        vm.navigation.collect { route ->
            when (route) {
                Route.HOME -> nav.popBackStack(Route.HOME.name, inclusive = false)
                Route.DONE -> nav.navigate(Route.DONE.name) { popUpTo(Route.HOME.name) }
                else -> nav.navigate(route.name) { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        SwipeDismissableNavHost(navController = nav, startDestination = Route.HOME.name) {
            composable(Route.HOME.name) {
                HomeScreen(
                    message = state.message,
                    sync = sync,
                    operator = vm.operatorCode,
                    onDictate = {
                        if (vm.voiceAvailable) {
                            vm.clearMessage()
                            dictateProduct("¿Qué producto auditas?")
                        } else {
                            vm.onVoiceUnavailable()
                        }
                    },
                    onBrowse = vm::browseCatalog,
                )
            }
            composable(Route.RESULTS.name) {
                ResultsScreen(
                    title = state.resultsTitle,
                    hits = state.hits,
                    offline = state.offlineResults,
                    listState = listState,
                    onSelect = { vm.openProduct(it.product) },
                )
            }
            composable(Route.PRODUCT.name) {
                state.product?.let { p ->
                    ProductScreen(
                        product = p,
                        submitting = state.submitting,
                        onRepeat = vm::repeatStatus,
                        onMatch = vm::confirmMatch,
                        onMismatch = vm::openCount,
                    )
                }
            }
            composable(Route.COUNT.name) {
                state.product?.let { p ->
                    CountScreen(
                        product = p,
                        counted = state.counted,
                        submitting = state.submitting,
                        onAdjust = vm::adjustCount,
                        onDictate = { dictateCount("¿Cuántas ${p.unit} contaste?") },
                        onSubmit = vm::submitCount,
                    )
                }
            }
            composable(Route.DONE.name) {
                state.done?.let { DoneScreen(it, onFinish = vm::reset) }
            }
        }
    }
}
