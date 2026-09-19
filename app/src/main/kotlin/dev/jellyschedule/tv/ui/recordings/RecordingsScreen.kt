package dev.jellyschedule.tv.ui.recordings

import androidx.compose.runtime.Composable
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen

@Composable
fun RecordingsScreen(onPlay: (Airing, Boolean) -> Unit, onBack: () -> Unit) {
    ErrorScreen("Recordings", "Recordings arrive in a later milestone.", ButtonSpec("Back", onBack))
}
