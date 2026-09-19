package dev.jellyschedule.tv.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.data.model.GuideDay
import dev.jellyschedule.tv.data.model.GuideResult
import dev.jellyschedule.tv.di.AppGraph
import dev.jellyschedule.tv.domain.GuideWeeks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

data class GuideUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val guide: GuideResult? = null,
    val weekIndex: Int = 0,
    val today: LocalDate? = null,
    val selectedDate: LocalDate? = null,
    val serverUrl: String = "",
    val householdMismatch: Boolean = false,
) {
    val days: List<GuideDay> get() = guide?.days.orEmpty()
    val selectedDay: GuideDay? get() = days.firstOrNull { it.date == selectedDate } ?: days.firstOrNull()
}

class GuideViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(GuideUiState(serverUrl = graph.sessions.current.serverUrl.orEmpty()))
    val state: StateFlow<GuideUiState> = _state

    init {
        load()
    }

    fun serverNow(): Instant = graph.clock.now()

    fun imageUrl(itemId: String?, type: String = "Primary", maxHeight: Int = 300): String? =
        itemId?.let { graph.sessions.imageUrl(it, type, maxHeight = maxHeight) }

    fun selectWeek(index: Int) {
        if (index == _state.value.weekIndex && _state.value.guide != null) return
        _state.update { it.copy(weekIndex = index, selectedDate = null) }
        load()
    }

    fun selectDay(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    /** Reloads the current week; used on first show, after a change on the details screen and for retry. */
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = it.guide == null, error = null) }
            val today = _state.value.today ?: graph.clock.nowInHouseholdZone().toLocalDate()
            val from = GuideWeeks.mondayOf(today).plusWeeks(_state.value.weekIndex.toLong())
            try {
                var guide = graph.schedule.guide(from, 7)
                val realToday = guide.now.toLocalDate()
                val realFrom = GuideWeeks.mondayOf(realToday).plusWeeks(_state.value.weekIndex.toLong())
                if (realFrom != from) guide = graph.schedule.guide(realFrom, 7)
                val mismatch = graph.schedule.cachedState?.householdMismatch ?: runCatching { graph.schedule.refreshState().householdMismatch }.getOrDefault(false)
                _state.update { s ->
                    s.copy(
                        loading = false,
                        error = null,
                        guide = guide,
                        today = realToday,
                        selectedDate = s.selectedDate?.takeIf { d -> guide.days.any { it.date == d } } ?: GuideWeeks.defaultSelectedDay(guide.days, realToday),
                        householdMismatch = mismatch,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = "Could not reach the server. ${e.message ?: ""}".trim()) }
            }
        }
    }
}
