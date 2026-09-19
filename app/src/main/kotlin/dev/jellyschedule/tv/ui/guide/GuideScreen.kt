package dev.jellyschedule.tv.ui.guide

import androidx.compose.runtime.Composable
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen
import dev.jellyschedule.tv.ui.nav.PlaybackMode

@Composable
fun GuideScreen(onOpenDetails: (Airing) -> Unit, onPlay: (Airing, PlaybackMode) -> Unit, onBack: () -> Unit) {
    ErrorScreen("Guide", "The guide arrives in the next milestone.", ButtonSpec("Back", onBack))
}
