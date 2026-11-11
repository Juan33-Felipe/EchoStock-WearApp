package com.echostock.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.echostock.wear.platform.rememberVoiceInput
import com.echostock.wear.ui.AuditViewModel
import com.echostock.wear.ui.Route
import com.echostock.wear.ui.components.CaptionBanner
import com.echostock.wear.ui.components.HapticPulseOverlay
import com.echostock.wear.ui.components.ListeningOverlay
import com.echostock.wear.ui.screens.AddProductScreen
import com.echostock.wear.ui.screens.CountScreen
import com.echostock.wear.ui.screens.DoneScreen
import com.echostock.wear.ui.screens.HomeScreen
import com.echostock.wear.ui.screens.ProductScreen
import com.echostock.wear.ui.screens.ResultsScreen
import com.echostock.wear.ui.theme.EchoColors
import com.echostock.wear.ui.theme.EchoStockTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AuditViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
            EchoStockTheme(darkTheme = isDark) {
                HapticPulseOverlay(viewModel.haptics.events) {
                    EchoStockNavigation(viewModel, isDark)
                }
            }
        }
    }
}

@Composable
private fun EchoStockNavigation(vm: AuditViewModel, isDark: Boolean) {
    val nav = rememberSwipeDismissableNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    // Respaldo: reconocedor del sistema si el offline no está disponible.
    val systemDictateProduct = rememberVoiceInput(vm::onSearchVoice)
    val systemDictateCount = rememberVoiceInput(vm::onCountVoice)
    val systemDictateNewProduct = rememberVoiceInput(vm::onNewProductVoice)
    val listening by vm.listening.collectAsStateWithLifecycle()
    var listenPrompt by remember { mutableStateOf("") }

    val context = LocalContext.current
    // Qué hacer cuando el usuario responda al diálogo de permiso: (si concede, si deniega).
    var pendingMic by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val (onGranted, onDenied) = pendingMic ?: return@rememberLauncherForActivityResult
        pendingMic = null
        if (granted) onGranted() else onDenied()
    }

    /** El reconocedor offline necesita el micrófono; si se deniega, queda el del sistema. */
    fun withMic(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            pendingMic = onGranted to onDenied
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val systemProductFallback = {
        if (vm.voiceAvailable) systemDictateProduct("¿Qué producto auditas?") else vm.onVoiceUnavailable()
    }
    val dictateProduct = {
        listenPrompt = "Producto, “agregar…” o “eliminar…”"
        withMic(onGranted = { vm.dictateProduct(fallback = systemProductFallback) }, onDenied = systemProductFallback)
    }
    val dictateNewProduct = {
        listenPrompt = "Nombre y cantidad (ej. Guantes 20)"
        val fallback = {
            if (vm.voiceAvailable) systemDictateNewProduct("Nombre y cantidad del producto") else vm.onVoiceUnavailable()
        }
        withMic(onGranted = { vm.dictateNewProduct(fallback) }, onDenied = fallback)
    }

    LaunchedEffect(nav) {
        vm.navigation.collect { route ->
            when (route) {
                Route.HOME -> nav.popBackStack(Route.HOME.name, inclusive = false)
                Route.DONE -> nav.navigate(Route.DONE.name) { popUpTo(Route.HOME.name) }
                else -> nav.navigate(route.name) { launchSingleTop = true }
            }
        }
    }

    val caption by vm.caption.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        timeText = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                TimeText(
                    timeTextStyle = TimeTextDefaults.timeTextStyle(color = EchoColors.OnSurface),
                )
                CaptionBanner(caption)
            }
        },
        vignette = {
            if (isDark) {
                Vignette(vignettePosition = VignettePosition.TopAndBottom)
            }
        },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        SwipeDismissableNavHost(navController = nav, startDestination = Route.HOME.name) {
            composable(Route.HOME.name) {
                HomeScreen(
                    message = state.message,
                    sync = sync,
                    operator = vm.operatorCode,
                    isDark = isDark,
                    onToggleTheme = vm::toggleTheme,
                    onDictate = dictateProduct,
                    onBrowse = vm::browseCatalog,
                    onAddProduct = { vm.openAddProduct() },
                    onQuickSearch = vm::searchDirect,
                )
            }
            composable(Route.RESULTS.name) {
                ResultsScreen(
                    title = state.resultsTitle,
                    hits = state.hits,
                    offline = state.offlineResults,
                    listState = listState,
                    onSelect = { vm.openProduct(it.product) },
                    onAddProduct = { vm.openAddProduct() },
                )
            }
            composable(Route.ADD_PRODUCT.name) {
                AddProductScreen(
                    draft = state.newProductDraft,
                    saving = state.submitting,
                    onDictateDetails = dictateNewProduct,
                    onAdjustQuantity = vm::adjustNewProductQuantity,
                    onSave = vm::saveNewProduct,
                    onNameChange = vm::updateNewProductName,
                )
            }
            composable(Route.PRODUCT.name) {
                state.product?.let { p ->
                    ProductScreen(
                        product = p,
                        submitting = state.submitting,
                        confirmingDelete = state.confirmingDelete,
                        onRepeat = vm::repeatStatus,
                        onRepeatLocation = vm::speakLocation,
                        onMatch = vm::confirmMatch,
                        onMismatch = vm::openCount,
                        onRequestDelete = vm::requestDeleteProduct,
                        onCancelDelete = vm::cancelDeleteProduct,
                        onConfirmDelete = vm::confirmDeleteProduct,
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
                        onDictate = {
                            listenPrompt = "¿Cuántas ${p.unit} contaste?"
                            val fallback = { systemDictateCount("¿Cuántas ${p.unit} contaste?") }
                            withMic(onGranted = { vm.dictateCount(fallback) }, onDenied = fallback)
                        },
                        onSubmit = vm::submitCount,
                    )
                }
            }
            composable(Route.DONE.name) {
                state.done?.let { DoneScreen(it, onFinish = vm::reset) }
            }
        }
        ListeningOverlay(listening, listenPrompt, onStop = vm::stopListening)
    }
}
