package com.echostock.wear.data

import com.echostock.wear.domain.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException

/** Último estado conocido de la conexión con el servidor, según el resultado real de cada llamada. */
class ServerStatus {
    private val _connection = MutableStateFlow(Connection.UNKNOWN)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    fun reachable() { _connection.value = Connection.ONLINE }
    fun unreachable() { _connection.value = Connection.OFFLINE }
}

/** Errores que significan "no hay servidor válido ahora mismo" y justifican trabajar en modo offline. */
internal fun Throwable.isTransient(): Boolean =
    this is IOException || (this is HttpException && (code() >= 500 || code() == 401 || code() == 403 || code() == 408 || code() == 429))
