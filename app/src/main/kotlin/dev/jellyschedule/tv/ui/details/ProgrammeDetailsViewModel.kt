package dev.jellyschedule.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.data.model.LiveMode
import dev.jellyschedule.tv.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

data class DetailsUiState(
    val airing: Airing,
    val busy: Boolean = false,
    val message: String? = null,
    val canEdit: Boolean = true,
    val liveMode: LiveMode = LiveMode.Relaxed,
) {
    val canRecord: Boolean get() = canEdit && airing.kind != AiringKind.ReRun
    val canMarkWatched: Boolean get() = canEdit && airing.kind != AiringKind.ReRun
}

class ProgrammeDetailsViewModel(private val graph: AppGraph, initial: Airing) : ViewModel() {
    private val _state = MutableStateFlow(
        DetailsUiState(
            airing = initial,
            canEdit = graph.schedule.cachedState?.canEdit ?: true,
            liveMode = graph.schedule.cachedState?.settings?.liveMode ?: LiveMode.Relaxed,
        ),
    )
    val state: StateFlow<DetailsUiState> = _state

    init {
        refresh()
    }

    fun serverNow(): Instant = graph.clock.now()

    fun imageUrl(itemId: String?, type: String = "Primary", maxHeight: Int = 600): String? =
        itemId?.let { graph.sessions.imageUrl(it, type, maxHeight = maxHeight) }

    /** Pulls the latest copy of this airing (watched / recorded / on-now flags may have changed). */
    fun refresh() {
        viewModelScope.launch {
            val fresh = runCatching { graph.schedule.findAiring(_state.value.airing) }.getOrNull() ?: return@launch
            val st = graph.schedule.cachedState
            _state.update { it.copy(airing = fresh, canEdit = st?.canEdit ?: it.canEdit, liveMode = st?.settings?.liveMode ?: it.liveMode) }
        }
    }

    fun record() = action("Recorded: waiting in Recordings, the show carries on from the next episode.") {
        graph.schedule.record(_state.value.airing)
    }

    fun cancelRecording() = action("Recording cancelled: the programme is back in the schedule.") {
        val id = _state.value.airing.recordingId ?: throw IOException("This programme has no recording to cancel.")
        graph.schedule.cancelRecording(id)
    }

    fun setWatched(watched: Boolean) = action(if (watched) "Marked as watched." else "Marked as unwatched.") {
        graph.schedule.setPlayed(_state.value.airing.itemId, watched)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun action(success: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                val fresh = runCatching { graph.schedule.findAiring(_state.value.airing) }.getOrNull()
                _state.update { it.copy(busy = false, message = success, airing = fresh ?: it.airing) }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, message = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(busy = false, message = "Could not reach the server. ${e.message ?: ""}".trim()) }
            }
        }
    }
}
