package com.echostock.wear.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echostock.wear.EchoStockApp
import com.echostock.wear.domain.AuditDraft
import com.echostock.wear.domain.AuditResult
import com.echostock.wear.domain.Connection
import com.echostock.wear.domain.Phrases
import com.echostock.wear.domain.Product
import com.echostock.wear.domain.SearchHit
import com.echostock.wear.domain.SubmitOutcome
import com.echostock.wear.domain.VoiceText
import com.echostock.wear.domain.VoiceGrammar
import com.echostock.wear.platform.Haptic
import com.echostock.wear.platform.OfflineSpeech
import com.echostock.wear.platform.SpeechUnavailableException
import com.echostock.wear.platform.RecognizeSpeech
import com.echostock.wear.platform.Speaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

enum class Route { HOME, RESULTS, PRODUCT, COUNT, ADD_PRODUCT, DONE }

sealed interface HomeMessage {
    data class Searching(val query: String) : HomeMessage
    data class NotFound(val query: String) : HomeMessage
    data class ProductDeleted(val name: String) : HomeMessage
    data class ThemeChanged(val isDark: Boolean) : HomeMessage
    data object NotUnderstood : HomeMessage
    data object VoiceUnavailable : HomeMessage
}

data class Done(val result: AuditResult, val difference: Int, val outcome: SubmitOutcome, val product: Product)

data class FlowState(
    val message: HomeMessage? = null,
    val hits: List<SearchHit> = emptyList(),
    val resultsTitle: String = "",
    val offlineResults: Boolean = false,
    val product: Product? = null,
    val counted: Int = 0,
    val submitting: Boolean = false,
    val confirmingDelete: Boolean = false,
    val newProductDraft: VoiceText.NewProductDraft = VoiceText.NewProductDraft("", "A-01-01", "unidades", 10),
    val done: Done? = null,
)

data class SyncState(val connection: Connection, val pending: Int, val failed: Int)

class AuditViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as EchoStockApp).container
    val haptics = container.haptics
    private val speaker = Speaker(app)
    val caption = speaker.caption
    private val speech = OfflineSpeech(app, viewModelScope).also { it.prewarm() }
    val listening = speech.state

    val operatorCode = container.operatorCode
    val voiceAvailable = RecognizeSpeech.isAvailable(app)
    val isDarkMode: StateFlow<Boolean> = container.theme.isDarkMode

    fun toggleTheme() {
        haptics.play(Haptic.TAP)
        val newDark = container.theme.toggle()
        val modeName = if (newDark) "oscuro" else "claro"
        speaker.speak("Modo $modeName activado")
        _state.update { it.copy(message = HomeMessage.ThemeChanged(newDark)) }
    }

    fun setDarkMode(dark: Boolean) {
        haptics.play(Haptic.CONFIRMED)
        container.theme.setDarkMode(dark)
        val modeName = if (dark) "oscuro" else "claro"
        speaker.speak("Modo $modeName activado")
        _state.update { it.copy(message = HomeMessage.ThemeChanged(dark)) }
    }

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    val sync: StateFlow<SyncState> = combine(
        container.serverStatus.connection,
        container.audits.pendingCount,
        container.audits.failedCount,
    ) { c, p, f -> SyncState(c, p, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncState(Connection.UNKNOWN, 0, 0))

    private val _navigation = MutableSharedFlow<Route>(extraBufferCapacity = 4)
    val navigation = _navigation.asSharedFlow()

    private var searchJob: Job? = null

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                if (container.products.ping()) container.scheduler.requestSync()
            }
        }

        override fun onLost(network: Network) = container.serverStatus.unreachable()
    }

    init {
        connectivity?.registerDefaultNetworkCallback(networkCallback)
        viewModelScope.launch { container.products.ping() }
    }

    // ---- Búsqueda (RF-001) ----

    /**
     * Dictado con el reconocedor offline del reloj. Si no está disponible (modelo dañado,
     * permiso denegado) se usa [fallback], que abre el reconocedor del sistema.
     */
    fun dictateProduct(fallback: () -> Unit) {
        speaker.stop()
        clearMessage()
        haptics.play(Haptic.TAP)
        viewModelScope.launch {
            val grammar = VoiceGrammar.forSearch(container.products.cached())
            val heard = try {
                speech.listen(grammar)
            } catch (e: SpeechUnavailableException) {
                fallback()
                return@launch
            }
            if (heard.isNullOrEmpty() && voiceAvailable) {
                fallback()
                return@launch
            }
            onSearchVoice(heard)
        }
    }

    fun dictateCount(fallback: () -> Unit) {
        speaker.stop()
        haptics.play(Haptic.TAP)
        viewModelScope.launch {
            val heard = try {
                speech.listen(VoiceGrammar.forQuantity())
            } catch (e: SpeechUnavailableException) {
                fallback()
                return@launch
            }
            onCountVoice(heard)
        }
    }

    fun stopListening() = speech.stop()

    fun onVoiceUnavailable() {
        haptics.play(Haptic.ERROR)
        _state.update { it.copy(message = HomeMessage.VoiceUnavailable) }
    }

    fun onSearchVoice(alternatives: List<String>?) {
        if (alternatives.isNullOrEmpty()) {
            haptics.play(Haptic.ERROR)
            speaker.speak(Phrases.NOT_UNDERSTOOD)
            _state.update { it.copy(message = HomeMessage.NotUnderstood) }
            return
        }
        haptics.play(Haptic.HEARD)
        when (val cmd = VoiceText.parseCommand(alternatives.first())) {
            is VoiceText.VoiceCommand.SetTheme -> {
                setDarkMode(cmd.dark)
            }
            is VoiceText.VoiceCommand.ToggleTheme -> {
                toggleTheme()
            }
            is VoiceText.VoiceCommand.Add -> {
                openAddProduct(cmd.details)
            }
            is VoiceText.VoiceCommand.Delete -> {
                search(listOf(cmd.query), forDeletion = true)
            }
            is VoiceText.VoiceCommand.Search -> {
                search(alternatives, forDeletion = false)
            }
        }
    }

    private fun search(alternatives: List<String>, forDeletion: Boolean = false) {
        val shown = alternatives.first()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(message = HomeMessage.Searching(shown)) }
            val outcome = runCatching { container.products.search(alternatives) }.getOrNull()
            val hits = outcome?.hits.orEmpty()
            when {
                hits.isEmpty() -> {
                    haptics.play(Haptic.ERROR)
                    speaker.speak(Phrases.notFound(shown))
                    _state.update { it.copy(message = HomeMessage.NotFound(shown)) }
                }
                forDeletion || isDecisive(hits) -> {
                    _state.update { it.copy(message = null, offlineResults = outcome!!.offline) }
                    openProduct(hits.first().product, confirmDelete = forDeletion)
                }
                else -> {
                    _state.update {
                        it.copy(
                            message = null,
                            hits = hits,
                            resultsTitle = "“$shown”",
                            offlineResults = outcome!!.offline,
                        )
                    }
                    val firstLoc = hits.firstOrNull()?.product?.locationCode
                    if (firstLoc != null) {
                        speaker.speak(Phrases.multipleResults(shown, hits.size, firstLoc))
                    }
                    _navigation.tryEmit(Route.RESULTS)
                }
            }
        }
    }

    /** Si hay un claro ganador vamos directo al producto: un toque menos (RNF-001). */
    private fun isDecisive(hits: List<SearchHit>): Boolean {
        val top = hits.first()
        val gap = if (hits.size == 1) 1.0 else top.score - hits[1].score
        // SKU o ubicación exactos (≥ 0.9) con algo de ventaja, o un nombre casi exacto (≥ 0.8) muy por delante.
        return (top.score >= 0.9 && gap >= 0.15) || (top.score >= 0.8 && gap >= 0.2)
    }

    fun browseCatalog() {
        haptics.play(Haptic.TAP)
        viewModelScope.launch {
            val all = runCatching { container.products.browse() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    hits = all.map { p -> SearchHit(p, 0.0, com.echostock.wear.domain.MatchedBy.NAME) },
                    resultsTitle = "Catálogo",
                    offlineResults = sync.value.connection == Connection.OFFLINE,
                    message = null,
                )
            }
            _navigation.tryEmit(Route.RESULTS)
        }
    }

    // ---- Producto y lectura por voz (RF-002) ----

    fun openProduct(product: Product, confirmDelete: Boolean = false) {
        _state.update {
            it.copy(
                product = product,
                counted = product.systemQuantity,
                confirmingDelete = confirmDelete,
                done = null,
            )
        }
        if (confirmDelete) {
            speaker.speak(Phrases.confirmDelete(product.name))
        } else {
            speaker.speak(Phrases.systemStatus(product))
        }
        _navigation.tryEmit(Route.PRODUCT)
    }

    fun repeatStatus() {
        state.value.product?.let { speaker.speak(Phrases.systemStatus(it)) }
    }

    fun speakLocation() {
        state.value.product?.let { speaker.speak("${it.name}, ${Phrases.location(it.locationCode)}.") }
    }

    fun searchDirect(query: String) {
        onSearchVoice(listOf(query))
    }

    // ---- Agregar y eliminar productos por voz ----

    fun openAddProduct(initialRaw: String = "") {
        speaker.stop()
        haptics.play(Haptic.TAP)
        val seed = initialRaw.ifBlank {
            (state.value.message as? HomeMessage.NotFound)?.query.orEmpty()
        }
        val draft = if (seed.isNotBlank()) {
            VoiceText.parseNewProduct(seed)
        } else {
            VoiceText.NewProductDraft("", "A-01-01", "unidades", 10)
        }
        _state.update { it.copy(newProductDraft = draft, message = null) }
        speaker.speak(Phrases.PROMPT_NEW_PRODUCT)
        _navigation.tryEmit(Route.ADD_PRODUCT)
    }

    fun dictateNewProduct(fallback: () -> Unit) {
        speaker.stop()
        haptics.play(Haptic.TAP)
        viewModelScope.launch {
            val heard = try {
                speech.listen(VoiceGrammar.forNewProduct())
            } catch (e: SpeechUnavailableException) {
                fallback()
                return@launch
            }
            onNewProductVoice(heard)
        }
    }

    fun onNewProductVoice(alternatives: List<String>?) {
        val text = alternatives?.firstOrNull { it.isNotBlank() }
        if (text == null) {
            haptics.play(Haptic.ERROR)
            speaker.speak(Phrases.NOT_UNDERSTOOD)
            return
        }
        haptics.play(Haptic.HEARD)
        val current = state.value.newProductDraft
        val updated = VoiceText.parseNewProduct(
            rawText = text,
            defaultName = current.name,
            defaultLocation = current.locationCode,
            defaultUnit = current.unit,
            defaultQuantity = current.quantity,
        )
        _state.update { it.copy(newProductDraft = updated) }
        speaker.speak("${updated.name}, ${updated.quantity} ${updated.unit}.")
    }

    fun updateNewProductName(name: String) {
        _state.update { s -> s.copy(newProductDraft = s.newProductDraft.copy(name = name)) }
    }

    fun adjustNewProductQuantity(delta: Int, withHaptic: Boolean = true) {
        if (withHaptic) haptics.play(Haptic.TAP)
        _state.update { s ->
            val nextQty = (s.newProductDraft.quantity + delta).coerceIn(0, MAX_COUNT)
            s.copy(newProductDraft = s.newProductDraft.copy(quantity = nextQty))
        }
    }

    fun saveNewProduct() {
        val draft = state.value.newProductDraft
        if (draft.name.isBlank() || state.value.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val created = container.products.create(
                name = draft.name,
                locationCode = draft.locationCode,
                unit = draft.unit,
                systemQuantity = draft.quantity,
            )
            haptics.play(Haptic.CONFIRMED)
            speaker.speak(Phrases.productAdded(created))
            _state.update {
                it.copy(
                    submitting = false,
                    product = created,
                    counted = created.systemQuantity,
                    confirmingDelete = false,
                )
            }
            _navigation.tryEmit(Route.PRODUCT)
        }
    }

    fun requestDeleteProduct() {
        val p = state.value.product ?: return
        haptics.play(Haptic.TAP)
        _state.update { it.copy(confirmingDelete = true) }
        speaker.speak(Phrases.confirmDelete(p.name))
    }

    fun cancelDeleteProduct() {
        haptics.play(Haptic.TAP)
        _state.update { it.copy(confirmingDelete = false) }
    }

    fun confirmDeleteProduct() {
        val p = state.value.product ?: return
        if (state.value.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            container.products.delete(p.sku)
            haptics.play(Haptic.CONFIRMED)
            speaker.speak(Phrases.productDeleted(p.name))
            _state.update {
                FlowState(message = HomeMessage.ProductDeleted(p.name))
            }
            _navigation.tryEmit(Route.HOME)
        }
    }

    // ---- Confirmación (RF-003) y discrepancias (RF-004) ----

    fun confirmMatch() {
        val p = state.value.product ?: return
        submit(p, p.systemQuantity)
    }

    fun openCount() {
        haptics.play(Haptic.TAP)
        speaker.stop()
        _state.update { s -> s.copy(counted = s.product?.systemQuantity ?: 0) }
        _navigation.tryEmit(Route.COUNT)
    }

    fun adjustCount(delta: Int, withHaptic: Boolean = true) {
        if (withHaptic) haptics.play(Haptic.TAP)
        _state.update { it.copy(counted = (it.counted + delta).coerceIn(0, MAX_COUNT)) }
    }

    fun onCountVoice(alternatives: List<String>?) {
        val n = alternatives?.firstNotNullOfOrNull { VoiceText.parseQuantity(it) }
        if (n == null) {
            haptics.play(Haptic.ERROR)
            speaker.speak(Phrases.NOT_UNDERSTOOD)
            return
        }
        haptics.play(Haptic.HEARD)
        _state.update { it.copy(counted = n.coerceIn(0, MAX_COUNT)) }
        speaker.speak(Phrases.countSet(n))
    }

    fun submitCount() {
        val s = state.value
        val p = s.product ?: return
        submit(p, s.counted)
    }

    // ---- Envío (RF-005 / RF-006) ----

    private fun submit(product: Product, counted: Int) {
        if (state.value.submitting) return
        val draft = AuditDraft(
            clientAuditId = UUID.randomUUID().toString(),
            sku = product.sku,
            operatorCode = container.operatorCode,
            deviceId = container.deviceId,
            systemQuantity = product.systemQuantity,
            countedQuantity = counted,
            recordedAt = Instant.now(),
        )
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val outcome = try {
                runCatching { container.products.syncLocalChanges() }
                container.audits.submit(draft)
            } catch (e: Exception) {
                SubmitOutcome.Rejected(e.message ?: "Error inesperado")
            }
            haptics.play(
                when (outcome) {
                    SubmitOutcome.Synced -> if (draft.result == AuditResult.MATCH) Haptic.CONFIRMED else Haptic.DISCREPANCY
                    SubmitOutcome.QueuedOffline -> Haptic.QUEUED
                    is SubmitOutcome.Rejected -> Haptic.ERROR
                },
            )
            speaker.speak(Phrases.outcome(draft.result, draft.difference, outcome))
            _state.update {
                it.copy(submitting = false, done = Done(draft.result, draft.difference, outcome, product))
            }
            _navigation.tryEmit(Route.DONE)
        }
    }

    /** Vuelve al inicio listo para el siguiente producto. */
    fun reset() {
        speaker.stop()
        _state.value = FlowState()
        _navigation.tryEmit(Route.HOME)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        speaker.shutdown()
    }

    private companion object {
        const val MAX_COUNT = 99_999
    }
}
