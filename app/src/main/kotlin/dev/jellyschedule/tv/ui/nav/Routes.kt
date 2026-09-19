package dev.jellyschedule.tv.ui.nav

import androidx.navigation.NavController
import dev.jellyschedule.tv.data.json.PluginJson
import dev.jellyschedule.tv.data.model.Airing
import kotlinx.serialization.Serializable

@Serializable object ConnectRoute
@Serializable object SignInRoute
@Serializable object TuneInRoute
@Serializable object GuideRoute
@Serializable object RecordingsRoute
@Serializable object SettingsRoute

/** How the player treats what it plays: the channel (auto-advance), a programme from the guide, or a recording. */
enum class PlaybackMode { Live, Watch, Recording }

@Serializable
data class PlayerRoute(val airingJson: String, val mode: String, val resume: Boolean = false) {
    val airing: Airing get() = PluginJson.decodeFromString(Airing.serializer(), airingJson)
    val playbackMode: PlaybackMode get() = PlaybackMode.entries.firstOrNull { it.name == mode } ?: PlaybackMode.Watch
}

@Serializable
data class DetailsRoute(val airingJson: String) {
    val airing: Airing get() = PluginJson.decodeFromString(Airing.serializer(), airingJson)
}

fun NavController.openPlayer(airing: Airing, mode: PlaybackMode, resume: Boolean = false) {
    navigate(PlayerRoute(PluginJson.encodeToString(Airing.serializer(), airing), mode.name, resume))
}

fun NavController.openDetails(airing: Airing) {
    navigate(DetailsRoute(PluginJson.encodeToString(Airing.serializer(), airing)))
}
