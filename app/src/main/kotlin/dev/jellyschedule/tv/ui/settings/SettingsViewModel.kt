package dev.jellyschedule.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jellyschedule.tv.BuildConfig
import dev.jellyschedule.tv.data.model.StateResponse
import dev.jellyschedule.tv.data.session.ClientPreferences
import dev.jellyschedule.tv.data.session.StoredSession
import dev.jellyschedule.tv.di.AppGraph
import dev.jellyschedule.tv.player.DeviceProfileBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Subtitle languages offered in settings: ISO 639-2 code as Jellyfin reports it, and a label. */
data class SubtitleLanguage(val code: String?, val label: String)

val SUBTITLE_LANGUAGES = listOf(
    SubtitleLanguage(null, "Off"),
    SubtitleLanguage("eng", "English"),
    SubtitleLanguage("nld", "Dutch"),
    SubtitleLanguage("deu", "German"),
    SubtitleLanguage("fra", "French"),
    SubtitleLanguage("spa", "Spanish"),
    SubtitleLanguage("ita", "Italian"),
    SubtitleLanguage("por", "Portuguese"),
    SubtitleLanguage("swe", "Swedish"),
    SubtitleLanguage("nor", "Norwegian"),
    SubtitleLanguage("dan", "Danish"),
    SubtitleLanguage("fin", "Finnish"),
    SubtitleLanguage("pol", "Polish"),
    SubtitleLanguage("ces", "Czech"),
    SubtitleLanguage("hun", "Hungarian"),
    SubtitleLanguage("ell", "Greek"),
    SubtitleLanguage("tur", "Turkish"),
    SubtitleLanguage("rus", "Russian"),
    SubtitleLanguage("ukr", "Ukrainian"),
    SubtitleLanguage("jpn", "Japanese"),
    SubtitleLanguage("kor", "Korean"),
    SubtitleLanguage("zho", "Chinese"),
)

data class SettingsUiState(
    val session: StoredSession,
    val preferences: ClientPreferences,
    val household: StateResponse?,
    val capabilities: DeviceProfileBuilder.Capabilities?,
    val appVersion: String,
) {
    val subtitleLanguageLabel: String
        get() = SUBTITLE_LANGUAGES.firstOrNull { it.code == preferences.subtitleLanguage }?.label
            ?: preferences.subtitleLanguage?.uppercase() ?: "Off"
}

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    private val capabilities: DeviceProfileBuilder.Capabilities? = runCatching { graph.deviceProfiles.capabilities }.getOrNull()

    val state: StateFlow<SettingsUiState> = combine(graph.sessions.session, graph.sessions.preferences, graph.schedule.state) { session, prefs, household ->
        SettingsUiState(session, prefs, household, capabilities, BuildConfig.VERSION_NAME)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(graph.sessions.current, graph.sessions.preferences.value, graph.schedule.cachedState, capabilities, BuildConfig.VERSION_NAME),
    )

    init {
        viewModelScope.launch {
            if (graph.schedule.cachedState == null) runCatching { graph.schedule.refreshState() }
        }
    }

    fun toggleAutoplay() = viewModelScope.launch { graph.sessions.setAutoplayOnLaunch(!state.value.preferences.autoplayOnLaunch) }

    fun toggleAlwaysTranscode() = viewModelScope.launch { graph.sessions.setAlwaysTranscode(!state.value.preferences.alwaysTranscode) }

    /** Cycles through the language list; a D-pad friendly picker for a short list. */
    fun nextSubtitleLanguage(step: Int = 1) = viewModelScope.launch {
        val current = SUBTITLE_LANGUAGES.indexOfFirst { it.code == state.value.preferences.subtitleLanguage }.coerceAtLeast(0)
        val next = SUBTITLE_LANGUAGES[(current + step + SUBTITLE_LANGUAGES.size) % SUBTITLE_LANGUAGES.size]
        graph.sessions.setSubtitleLanguage(next.code)
    }

    fun signOut() = viewModelScope.launch { graph.sessions.signOut() }

    fun forgetServer() = viewModelScope.launch { graph.sessions.forgetServer() }
}
