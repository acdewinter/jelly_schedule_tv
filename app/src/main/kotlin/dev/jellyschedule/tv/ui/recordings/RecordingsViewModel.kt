package dev.jellyschedule.tv.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class RecordingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val recordings: List<RecordingStatus> = emptyList(),
    val busyId: String? = null,
    val message: String? = null,
    val canEdit: Boolean = true,
) {
    val toWatch: List<RecordingStatus> get() = recordings.filter { !it.isWatched }
    val watched: List<RecordingStatus> get() = recordings.filter { it.isWatched }
}

class RecordingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(RecordingsUiState(canEdit = graph.schedule.cachedState?.canEdit ?: true))
    val state: StateFlow<RecordingsUiState> = _state

    init {
        load()
    }

    fun imageUrl(itemId: String?, maxHeight: Int = 300): String? = itemId?.let { graph.sessions.imageUrl(it, "Primary", maxHeight = maxHeight) }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = it.recordings.isEmpty(), error = null) }
            try {
                val list = graph.schedule.recordings()
                _state.update { it.copy(loading = false, error = null, recordings = list, canEdit = graph.schedule.cachedState?.canEdit ?: it.canEdit) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = "Could not reach the server. ${e.message ?: ""}".trim()) }
            }
        }
    }

    fun cancel(recording: RecordingStatus) {
        val id = recording.recording.id
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, message = null) }
        viewModelScope.launch {
            try {
                graph.schedule.cancelRecording(id)
                val list = runCatching { graph.schedule.recordings() }.getOrNull()
                _state.update { s ->
                    s.copy(
                        busyId = null,
                        message = "Recording cancelled" + if (recording.isWatched) "." else "; the programme returns to the schedule.",
                        recordings = list ?: s.recordings.filter { it.recording.id != id },
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(busyId = null, message = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(busyId = null, message = "Could not reach the server. ${e.message ?: ""}".trim()) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
