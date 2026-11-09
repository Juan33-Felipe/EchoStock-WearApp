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
import com.echostock.wear.platform.Haptic
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

enum class Route { HOME, RESULTS, PRODUCT, COUNT, DONE }

sealed interface HomeMessage {
    data class Searching(val query: String) : HomeMessage
    data class NotFound(val query: String) : HomeMessage
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
    val done: Done? = null,
)

data class SyncState(val connection: Connection, val pending: Int, val failed: Int)

class AuditViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as EchoStockApp).container
    val haptics = container.haptics
    private val speaker = Speaker(app)

    val operatorCode = container.operatorCode
    val voiceAvailable = RecognizeSpeech.isAvailable(app)

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
        search(alternatives)
    }

    private fun search(alternatives: List<String>) {
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
                isDecisive(hits) -> {
                    _state.update { it.copy(message = null, offlineResults = outcome!!.offline) }
                    openProduct(hits.first().product)
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
                    _navigation.tryEmit(Route.RESULTS)
                }
            }
        }
    }

    /** Si hay un claro ganador vamos directo al producto: un toque menos (RNF-001). */
    private fun isDecisive(hits: List<SearchHit>): Boolean {
        val top = hits.first()
        if (top.score < 0.9) return false
        return hits.size == 1 || top.score - hits[1].score >= 0.15
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

    fun openProduct(product: Product) {
        _state.update { it.copy(product = product, counted = product.systemQuantity, done = null) }
        speaker.speak(Phrases.systemStatus(product))
        _navigation.tryEmit(Route.PRODUCT)
    }

    fun repeatStatus() {
        state.value.product?.let { speaker.speak(Phrases.systemStatus(it)) }
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
