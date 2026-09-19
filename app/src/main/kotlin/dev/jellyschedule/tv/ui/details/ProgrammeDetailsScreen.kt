package dev.jellyschedule.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.appGraph
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.domain.TimeText
import dev.jellyschedule.tv.domain.backdropItemId
import dev.jellyschedule.tv.domain.badges
import dev.jellyschedule.tv.domain.displaySubtitle
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.domain.formatMinutes
import dev.jellyschedule.tv.domain.formatTicks
import dev.jellyschedule.tv.domain.posterItemId
import dev.jellyschedule.tv.ui.common.BackdropImage
import dev.jellyschedule.tv.ui.common.BadgeRow
import dev.jellyschedule.tv.ui.common.InfoBanner
import dev.jellyschedule.tv.ui.common.PosterImage
import dev.jellyschedule.tv.ui.common.Spinner
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.nav.PlaybackMode
import dev.jellyschedule.tv.ui.theme.JellyColors
import java.util.Locale

@Composable
fun ProgrammeDetailsScreen(initial: Airing, onPlay: (Airing, PlaybackMode, Boolean) -> Unit, onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val vm: ProgrammeDetailsViewModel = viewModel(key = initial.id) { ProgrammeDetailsViewModel(graph, initial) }
    val state by vm.state.collectAsStateWithLifecycle()
    val airing = state.airing
    val focus = rememberInitialFocus(initial.id)
    val now = remember(airing.id) { graph.clock.nowInHouseholdZone() }
    val isLive = airing.isOnNow
    val past = airing.end.toInstant().isBefore(vm.serverNow()) && !isLive
    val playLabel = when {
        isLive -> "Tune in"
        past -> "Watch"
        else -> "Watch now"
    }

    Box(Modifier.fillMaxSize()) {
        BackdropImage(vm.imageUrl(airing.backdropItemId, "Backdrop", 1080), Modifier.fillMaxSize(), blurred = false, dim = 0.8f)
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            PosterImage(vm.imageUrl(airing.posterItemId, maxHeight = 700), Modifier.size(300.dp, 450.dp), title = airing.displayTitle)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BadgeRow(airing.badges())
                Spacer(Modifier.height(8.dp))
                Text(airing.displayTitle, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(airing.displaySubtitle, style = MaterialTheme.typography.titleLarge, color = JellyColors.Muted)
                Spacer(Modifier.height(14.dp))
                KeyValue("When", "${TimeText.relative(airing.start, now).replaceFirstChar { it.uppercase() }} – ${TimeText.wallTime(airing.end)}" + if (airing.runsOver) " (runs past the viewing window)" else "")
                KeyValue("Runtime", "${formatMinutes(airing.runtimeMinutes)} in a ${formatMinutes(airing.durationMinutes)} slot")
                if (airing.year != null && airing.kind != AiringKind.Movie) KeyValue("Year", airing.year.toString())
                if (airing.communityRating != null) KeyValue("Rating", "★ " + String.format(Locale.ROOT, "%.1f", airing.communityRating))
                if (airing.officialRating != null && airing.kind != AiringKind.Movie) KeyValue("Rated", airing.officialRating)
                if (airing.positionTicks > 0) KeyValue("Progress", "${formatTicks(airing.positionTicks)} watched")
                Spacer(Modifier.height(12.dp))
                Text(
                    airing.overview?.takeIf { it.isNotBlank() } ?: "No description.",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 980.dp),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(playLabel, onClick = { onPlay(airing, if (isLive) PlaybackMode.Live else PlaybackMode.Watch, false) }, icon = Icons.Default.PlayArrow, primary = true, focusRequester = focus)
                    if (airing.positionTicks > 0) {
                        TvButton("Resume ${formatTicks(airing.positionTicks)}", onClick = { onPlay(airing, PlaybackMode.Watch, true) })
                    }
                    if (state.canRecord) {
                        if (airing.isRecorded) TvButton("Cancel recording", onClick = vm::cancelRecording, icon = Icons.Default.Undo, enabled = !state.busy)
                        else TvButton("Record for later", onClick = vm::record, icon = Icons.Default.FiberManualRecord, enabled = !state.busy)
                    }
                    if (state.canMarkWatched) {
                        if (airing.isWatched) TvButton("Mark unwatched", onClick = { vm.setWatched(false) }, enabled = !state.busy)
                        else TvButton("Mark watched", onClick = { vm.setWatched(true) }, icon = Icons.Default.Check, enabled = !state.busy)
                    }
                    if (state.busy) Spinner(size = 28)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        airing.kind == AiringKind.ReRun -> "Re-runs ignore watched status."
                        airing.isRecorded -> "Recorded: this programme is waiting in Recordings and the show carries on from the next one."
                        airing.isMissed -> "You missed this one. It will air again in the next slot for this show, or record it to watch whenever you like."
                        else -> "If you miss it, it simply airs again next time. Record it to keep the show moving."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = JellyColors.Muted,
                )
                if (state.message != null) {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(state.message!!, color = JellyColors.Ok)
                }
                if (!state.canEdit) {
                    Spacer(Modifier.height(12.dp))
                    Text("Only the household user (or an administrator) can record or mark programmes.", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted, modifier = Modifier.widthIn(min = 96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
