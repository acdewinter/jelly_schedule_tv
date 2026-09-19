package dev.jellyschedule.tv.ui.tunein

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.data.api.NotSignedInException
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.LiveMode
import dev.jellyschedule.tv.data.model.NowResponse
import dev.jellyschedule.tv.data.model.StateResponse
import dev.jellyschedule.tv.di.AppGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

sealed interface TuneInUiState {
    data object Loading : TuneInUiState

    data class Error(val message: String, val serverUrl: String) : TuneInUiState

    data class Ready(
        val now: NowResponse,
        val state: StateResponse?,
        val serverUrl: String,
        val liveMode: LiveMode,
        val autoplay: Boolean,
        val refreshing: Boolean = false,
    ) : TuneInUiState {
        val householdMismatch: Boolean get() = state?.householdMismatch == true
        val emptySchedule: Boolean get() = state?.isEmptySchedule == true
    }
}

sealed interface TuneInEvent {
    /** Something is on and autoplay is enabled: start the player. Emitted once per programme. */
    data class Autoplay(val airing: Airing) : TuneInEvent

    data object SignedOut : TuneInEvent
}

class TuneInViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow<TuneInUiState>(TuneInUiState.Loading)
    val state: StateFlow<TuneInUiState> = _state

    private val _events = MutableSharedFlow<TuneInEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TuneInEvent> = _events

    /** Airings that were already started automatically; backing out of the player must not restart them. */
    private val autoplayed = mutableSetOf<String>()

    private var lastState: StateResponse? = graph.schedule.cachedState

    init {
        load()
    }

    fun serverNow(): Instant = graph.clock.now()

    fun imageUrl(itemId: String?, type: String = "Primary", maxHeight: Int = 600): String? =
        itemId?.let { graph.sessions.imageUrl(it, type, maxHeight = maxHeight) }

    /** Fetches `state` and `now` in parallel; used at start and for manual retry. */
    fun load() {
        if (_state.value !is TuneInUiState.Ready) _state.value = TuneInUiState.Loading
        viewModelScope.launch {
            try {
                val (st, now) = coroutineScope {
                    val s = async { graph.schedule.refreshState() }
                    val n = async { graph.schedule.now() }
                    s.await() to n.await()
                }
                lastState = st
                publish(now, st)
                maybeAutoplay(now, st)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Polls `now` only (every 30 s while the screen is visible). */
    fun refreshNow() {
        viewModelScope.launch {
            val current = _state.value
            if (current is TuneInUiState.Ready) _state.value = current.copy(refreshing = true)
            try {
                val now = graph.schedule.now()
                val st = lastState ?: runCatching { graph.schedule.refreshState() }.getOrNull().also { lastState = it }
                publish(now, st)
                maybeAutoplay(now, st)
            } catch (e: Exception) {
                if (current is TuneInUiState.Ready) _state.value = current.copy(refreshing = false) else fail(e)
            }
        }
    }

    /** Called when the user starts playback themselves so the poll does not start it again. */
    fun markStarted(airing: Airing) {
        autoplayed += airing.id
    }

    private fun publish(now: NowResponse, st: StateResponse?) {
        val prefs = graph.sessions.preferences.value
        _state.value = TuneInUiState.Ready(
            now = now,
            state = st,
            serverUrl = graph.sessions.current.serverUrl.orEmpty(),
            liveMode = st?.settings?.liveMode ?: LiveMode.Relaxed,
            autoplay = (st?.settings?.autoplayOnOpen ?: true) && prefs.autoplayOnLaunch,
        )
    }

    private fun maybeAutoplay(now: NowResponse, st: StateResponse?) {
        val onNow = now.onNow ?: return
        val ready = _state.value as? TuneInUiState.Ready ?: return
        if (!ready.autoplay) return
        if (onNow.id in autoplayed) return
        autoplayed += onNow.id
        _events.tryEmit(TuneInEvent.Autoplay(onNow))
    }

    private fun fail(e: Exception) {
        val server = graph.sessions.current.serverUrl.orEmpty()
        when {
            e is NotSignedInException || (e is ApiException && e.isUnauthorized) -> _events.tryEmit(TuneInEvent.SignedOut)
            e is ApiException -> _state.value = TuneInUiState.Error(e.message ?: "The server returned an error.", server)
            e is IOException -> _state.value = TuneInUiState.Error("Could not reach the server. ${e.message ?: ""}".trim(), server)
            else -> _state.value = TuneInUiState.Error(e.message ?: e.javaClass.simpleName, server)
        }
    }
}
