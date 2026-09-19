package dev.jellyschedule.tv.ui.details

import androidx.compose.runtime.Composable
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen
import dev.jellyschedule.tv.ui.nav.PlaybackMode

@Composable
fun ProgrammeDetailsScreen(initial: Airing, onPlay: (Airing, PlaybackMode, Boolean) -> Unit, onBack: () -> Unit) {
    ErrorScreen(initial.title, "Programme details arrive in the next milestone.", ButtonSpec("Back", onBack))
}
