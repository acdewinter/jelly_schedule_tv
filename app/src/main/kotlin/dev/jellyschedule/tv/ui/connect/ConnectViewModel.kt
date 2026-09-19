package dev.jellyschedule.tv.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.di.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException

data class DiscoveredServer(val name: String, val address: String)

data class ConnectUiState(
    val address: String = "",
    val connecting: Boolean = false,
    val discovering: Boolean = false,
    val discovered: List<DiscoveredServer> = emptyList(),
    val error: String? = null,
    val rememberedServer: String? = null,
)

sealed interface ConnectEvent {
    data object Connected : ConnectEvent
}

class ConnectViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState(address = graph.sessions.current.serverUrl ?: "", rememberedServer = graph.sessions.current.serverUrl))
    val state: StateFlow<ConnectUiState> = _state

    private val _events = MutableSharedFlow<ConnectEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConnectEvent> = _events

    private var discoveryJob: Job? = null

    fun setAddress(value: String) = _state.update { it.copy(address = value, error = null) }

    fun connect(address: String = _state.value.address) {
        if (_state.value.connecting) return
        _state.update { it.copy(connecting = true, error = null, address = address) }
        viewModelScope.launch {
            try {
                graph.sessions.connect(address)
                _state.update { it.copy(connecting = false) }
                _events.tryEmit(ConnectEvent.Connected)
            } catch (e: Exception) {
                _state.update { it.copy(connecting = false, error = describe(e)) }
            }
        }
    }

    fun discover() {
        discoveryJob?.cancel()
        _state.update { it.copy(discovering = true, discovered = emptyList(), error = null) }
        discoveryJob = viewModelScope.launch {
            try {
                graph.sessions.discoverLocalServers().collect { info ->
                    _state.update { s ->
                        if (s.discovered.any { it.address == info.address }) s
                        else s.copy(discovered = s.discovered + DiscoveredServer(info.name, info.address))
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not search the network: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _state.update { it.copy(discovering = false) }
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is TimeoutException -> "The server did not answer in time. Check the address and that the TV is on the same network."
        is SecureConnectionException -> "The secure connection failed. Check the server's certificate or use http://."
        is InvalidStatusException -> "The server answered with HTTP ${e.status}. Is this the address of a Jellyfin server?"
        is ApiClientException -> "Could not reach the server: ${e.message}"
        else -> e.message ?: "Could not reach the server."
    }
}
