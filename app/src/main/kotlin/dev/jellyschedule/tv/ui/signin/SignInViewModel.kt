package dev.jellyschedule.tv.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.di.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException

data class SignInUiState(
    val serverName: String,
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val quickConnectCode: String? = null,
    val quickConnectWaiting: Boolean = false,
)

class SignInViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(
        SignInUiState(
            serverName = graph.sessions.pendingServer?.name ?: graph.sessions.current.serverName ?: "Jellyfin",
            serverUrl = graph.sessions.pendingServer?.url ?: graph.sessions.current.serverUrl ?: "",
            username = graph.sessions.current.userName ?: "",
        ),
    )
    val state: StateFlow<SignInUiState> = _state

    private var quickConnectJob: Job? = null

    fun setUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun signIn() {
        val s = _state.value
        if (s.busy) return
        if (s.username.isBlank()) {
            _state.update { it.copy(error = "Enter your Jellyfin user name.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                graph.sessions.signIn(s.username.trim(), s.password)
                // The session flow flips to signed-in and the navigation host moves on.
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = describe(e)) }
            }
        }
    }

    fun startQuickConnect() {
        if (_state.value.quickConnectWaiting) return
        _state.update { it.copy(error = null, quickConnectWaiting = true, quickConnectCode = null) }
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            try {
                if (!graph.sessions.quickConnectEnabled()) {
                    _state.update { it.copy(quickConnectWaiting = false, error = "Quick Connect is disabled on this server. Enable it in the Jellyfin dashboard or sign in with a password.") }
                    return@launch
                }
                val started = graph.sessions.startQuickConnect()
                _state.update { it.copy(quickConnectCode = started.code) }
                while (true) {
                    delay(2_000)
                    val current = graph.sessions.quickConnectState(started.secret)
                    if (current.authenticated) {
                        graph.sessions.finishQuickConnect(started.secret)
                        _state.update { it.copy(quickConnectWaiting = false, quickConnectCode = null) }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(quickConnectWaiting = false, quickConnectCode = null, error = describe(e)) }
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        _state.update { it.copy(quickConnectWaiting = false, quickConnectCode = null) }
    }

    private fun describe(e: Exception): String = when (e) {
        is InvalidStatusException -> when (e.status) {
            401 -> "Wrong user name or password."
            403 -> "This user is not allowed to sign in."
            404 -> "Quick Connect code expired. Try again."
            else -> "The server answered with HTTP ${e.status}."
        }
        is TimeoutException -> "The server did not answer in time."
        else -> e.message ?: "Sign-in failed."
    }
}
