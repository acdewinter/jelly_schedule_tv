package dev.jellyschedule.tv.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.domain.TuneInRules
import dev.jellyschedule.tv.domain.formatMinutes
import dev.jellyschedule.tv.domain.formatTicks
import dev.jellyschedule.tv.domain.toAiring
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen
import dev.jellyschedule.tv.ui.common.InfoBanner
import dev.jellyschedule.tv.ui.common.LoadingScreen
import dev.jellyschedule.tv.ui.common.PosterImage
import dev.jellyschedule.tv.ui.common.ProgressBar
import dev.jellyschedule.tv.ui.common.SectionTitle
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.theme.JellyColors

@Composable
fun RecordingsScreen(onPlay: (Airing, Boolean) -> Unit, onBack: () -> Unit) {
    val vm = graphViewModel { RecordingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Coming back from the player refreshes positions and watched flags.
    LaunchedEffect(lifecycleOwner) {
        var first = true
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!first) vm.load(silent = true)
            first = false
        }
    }

    when {
        state.loading && state.recordings.isEmpty() -> LoadingScreen("Loading recordings…")
        state.error != null && state.recordings.isEmpty() -> ErrorScreen(
            title = "Can't load recordings",
            message = state.error!!,
            primary = ButtonSpec("Try again", onClick = { vm.load() }),
            secondary = ButtonSpec("Back", onBack),
        )
        state.recordings.isEmpty() -> ErrorScreen(
            title = "No recordings",
            message = "Use \"Record for later\" on a programme in the guide to keep it here while the show moves on.",
            primary = ButtonSpec("Back", onBack),
        )
        else -> RecordingsList(state, vm, onPlay)
    }
}

@Composable
private fun RecordingsList(state: RecordingsUiState, vm: RecordingsViewModel, onPlay: (Airing, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(JellyColors.Background).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Recordings", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${state.toWatch.size} to watch" + if (state.watched.isNotEmpty()) " · ${state.watched.size} watched" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = JellyColors.Muted,
            )
        }
        if (state.message != null) {
            Spacer(Modifier.height(10.dp))
            InfoBanner(state.message!!, color = JellyColors.Ok)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.toWatch.isNotEmpty()) {
                item { SectionTitle("To watch", Modifier.padding(bottom = 4.dp)) }
                items(state.toWatch, key = { it.recording.id }) { rec ->
                    RecordingRow(rec, vm, state, onPlay, focusFirst = rec == state.toWatch.first())
                }
            }
            if (state.watched.isNotEmpty()) {
                item { SectionTitle("Watched", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(state.watched, key = { it.recording.id }) { rec ->
                    RecordingRow(rec, vm, state, onPlay, focusFirst = state.toWatch.isEmpty() && rec == state.watched.first())
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: RecordingStatus, vm: RecordingsViewModel, state: RecordingsUiState, onPlay: (Airing, Boolean) -> Unit, focusFirst: Boolean) {
    val airing = rec.toAiring()
    val runtime = rec.item?.runtimeMinutes ?: 0
    val progress = if (runtime > 0 && rec.positionTicks > 0) (rec.positionTicks.toFloat() / (runtime * 60L * TuneInRules.TICKS_PER_SECOND)).coerceIn(0f, 1f) else 0f
    val busy = state.busyId == rec.recording.id
    val focus = rememberInitialFocus(if (focusFirst) rec.recording.id else null)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JellyColors.Surface.copy(alpha = if (rec.isWatched) 0.4f else 0.7f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PosterImage(vm.imageUrl(rec.seriesId ?: rec.item?.id?.takeIf { rec.item.hasPrimaryImage }), Modifier.size(64.dp, 96.dp), title = rec.recording.title, corner = 6)
        Column(Modifier.weight(1f)) {
            Text(rec.recording.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(rec.recording.subtitle, runtime.takeIf { it > 0 }?.let(::formatMinutes)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = JellyColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            when {
                rec.missing -> Text("This item is no longer in the library.", style = MaterialTheme.typography.bodySmall, color = JellyColors.Warn)
                rec.isWatched -> Text("Watched", style = MaterialTheme.typography.bodySmall, color = JellyColors.Ok)
                progress > 0f -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressBar(progress, Modifier.width(220.dp))
                    Text("${formatTicks(rec.positionTicks)} watched", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
                }
                else -> Text("Not started", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
            }
        }
        if (!rec.missing) {
            TvButton(
                if (rec.positionTicks > 0 && !rec.isWatched) "Resume" else "Play",
                onClick = { onPlay(airing, rec.positionTicks > 0 && !rec.isWatched) },
                icon = Icons.Default.PlayArrow,
                primary = true,
                focusRequester = if (focusFirst) focus else null,
            )
        }
        if (state.canEdit) {
            TvButton(
                if (rec.isWatched) "Remove" else "Cancel",
                onClick = { vm.cancel(rec) },
                icon = Icons.Default.Close,
                enabled = !busy,
                focusRequester = if (focusFirst && rec.missing) focus else null,
            )
        }
    }
}
