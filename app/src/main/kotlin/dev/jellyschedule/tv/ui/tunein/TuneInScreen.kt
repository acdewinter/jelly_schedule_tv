package dev.jellyschedule.tv.ui.tunein

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.domain.AutoAdvance
import dev.jellyschedule.tv.domain.TimeText
import dev.jellyschedule.tv.domain.TuneInRules
import dev.jellyschedule.tv.domain.backdropItemId
import dev.jellyschedule.tv.domain.badges
import dev.jellyschedule.tv.domain.displaySubtitle
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.domain.posterItemId
import dev.jellyschedule.tv.ui.common.BackdropImage
import dev.jellyschedule.tv.ui.common.BadgeRow
import dev.jellyschedule.tv.ui.common.ButtonSpec
import dev.jellyschedule.tv.ui.common.ErrorScreen
import dev.jellyschedule.tv.ui.common.InfoBanner
import dev.jellyschedule.tv.ui.common.LivePill
import dev.jellyschedule.tv.ui.common.LoadingScreen
import dev.jellyschedule.tv.ui.common.PosterImage
import dev.jellyschedule.tv.ui.common.ProgressBar
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.nav.PlaybackMode
import dev.jellyschedule.tv.ui.theme.JellyColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun TuneInScreen(
    onPlay: (Airing, PlaybackMode) -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val vm = graphViewModel { TuneInViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is TuneInEvent.Autoplay -> onPlay(event.airing, PlaybackMode.Live)
                TuneInEvent.SignedOut -> Unit // the navigation host reacts to the session flow
            }
        }
    }

    // Poll `now` every 30 s while the screen is in the foreground, and once right away when coming back.
    LaunchedEffect(lifecycleOwner) {
        var first = true
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!first) vm.refreshNow()
            first = false
            while (true) {
                delay(30_000)
                vm.refreshNow()
            }
        }
    }

    when (val s = state) {
        TuneInUiState.Loading -> LoadingScreen("Tuning in…")
        is TuneInUiState.Error -> ErrorScreen(
            title = "Can't reach the channel",
            message = "${s.message}\n${s.serverUrl}",
            primary = ButtonSpec("Try again", vm::load),
            secondary = ButtonSpec("Change server", onChangeServer),
            tertiary = ButtonSpec("Settings", onSettings),
        )
        is TuneInUiState.Ready -> ReadyContent(
            s = s,
            vm = vm,
            onTuneIn = { airing -> vm.markStarted(airing); onPlay(airing, PlaybackMode.Live) },
            onWatchEarly = { airing -> onPlay(airing, PlaybackMode.Watch) },
            onGuide = onGuide,
            onRecordings = onRecordings,
            onSettings = onSettings,
        )
    }
}

@Composable
private fun ReadyContent(
    s: TuneInUiState.Ready,
    vm: TuneInViewModel,
    onTuneIn: (Airing) -> Unit,
    onWatchEarly: (Airing) -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
) {
    val onNow = s.now.onNow
    val upNext = s.now.upNext
    var tick by remember { mutableStateOf(vm.serverNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = vm.serverNow()
            delay(1_000)
        }
    }
    // When the countdown to the next programme runs out, ask the server what is on right away.
    LaunchedEffect(upNext?.id, onNow?.id) {
        if (onNow == null && upNext != null) {
            val wait = Duration.between(vm.serverNow(), upNext.start.toInstant()).toMillis()
            if (wait > 0) delay(wait + 500) else delay(15_000)
            vm.refreshNow()
        }
    }

    val backdropOf = onNow ?: upNext
    Box(Modifier.fillMaxSize()) {
        BackdropImage(url = vm.imageUrl(backdropOf?.backdropItemId, "Backdrop", 1080), modifier = Modifier.fillMaxSize(), blurred = true)
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            if (s.householdMismatch) {
                InfoBanner(
                    "You are signed in as ${s.state?.me?.name}, but this channel follows ${s.state?.householdUser?.name}'s watch history. " +
                        "Anything you watch here is also marked as watched for ${s.state?.householdUser?.name}.",
                )
                Spacer(Modifier.height(12.dp))
            }
            if (s.emptySchedule) {
                InfoBanner(
                    "Your channel is empty. Choose viewing evenings and build a lineup in the Jelly Schedule web app at ${s.serverUrl}/JellySchedule/app.",
                    color = JellyColors.Primary,
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.weight(1f))
            if (onNow != null) {
                OnNowCard(onNow, upNext, s, tick, vm, onTuneIn, onGuide, onRecordings, onSettings)
            } else {
                OffAirCard(upNext, s, tick, vm, onWatchEarly, onGuide, onRecordings, onSettings)
            }
        }
    }
}

@Composable
private fun OnNowCard(
    onNow: Airing,
    upNext: Airing?,
    s: TuneInUiState.Ready,
    tick: Instant,
    vm: TuneInViewModel,
    onTuneIn: (Airing) -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
) {
    val focus = rememberInitialFocus(onNow.id)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        PosterImage(vm.imageUrl(onNow.posterItemId, maxHeight = 600), Modifier.width(240.dp).height(360.dp), title = onNow.displayTitle)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LivePill("ON NOW")
                Text("${TimeText.wallTime(onNow.start)}–${TimeText.wallTime(onNow.end)}", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
                BadgeRow(onNow.badges(compact = true, skipLive = true))
            }
            Spacer(Modifier.height(10.dp))
            Text(onNow.displayTitle, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(onNow.displaySubtitle, style = MaterialTheme.typography.titleLarge, color = JellyColors.Muted)
            if (!onNow.overview.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(onNow.overview, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressBar(TuneInRules.liveProgress(onNow, tick), Modifier.weight(1f))
                Text(
                    if (s.liveMode.name == "Strict") "joined in progress" else if (onNow.positionTicks > 0) "resumes where you left off" else "starts from the beginning",
                    style = MaterialTheme.typography.bodySmall,
                    color = JellyColors.Muted,
                )
            }
            if (upNext != null) {
                Spacer(Modifier.height(6.dp))
                Text("Next: ${upNext.displayTitle} at ${TimeText.wallTime(upNext.start)}", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Tune in", onClick = { onTuneIn(onNow) }, icon = Icons.Default.PlayArrow, primary = true, focusRequester = focus)
                TvButton("Guide", onClick = onGuide, icon = Icons.Default.CalendarMonth)
                TvButton("Recordings", onClick = onRecordings, icon = Icons.Default.FiberManualRecord)
                TvButton("Settings", onClick = onSettings, icon = Icons.Default.Settings)
            }
        }
    }
}

@Composable
private fun OffAirCard(
    upNext: Airing?,
    s: TuneInUiState.Ready,
    tick: Instant,
    vm: TuneInViewModel,
    onWatchEarly: (Airing) -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
) {
    val focus = rememberInitialFocus(upNext?.id)
    val nowWall = tick.atOffset(upNext?.start?.offset ?: s.now.now.offset)
    Column {
        Text("Off air", style = MaterialTheme.typography.displayLarge, color = JellyColors.Muted, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        if (upNext != null) {
            val seconds = AutoAdvance.secondsUntil(upNext, tick)
            val soon = Duration.ofSeconds(seconds) <= AutoAdvance.COUNTDOWN_WINDOW
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PosterImage(vm.imageUrl(upNext.posterItemId, maxHeight = 300), Modifier.size(120.dp, 180.dp), title = upNext.displayTitle)
                Column {
                    Text("UP NEXT · ${TimeText.relative(upNext.start, nowWall)}", style = MaterialTheme.typography.labelLarge, color = JellyColors.Primary)
                    Text(upNext.displayTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(upNext.displaySubtitle, style = MaterialTheme.typography.titleMedium, color = JellyColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (soon) "Starts in ${TimeText.countdown(seconds)}" else TimeText.relative(upNext.start, nowWall).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        } else if (s.emptySchedule) {
            Text("Nothing is scheduled yet.", style = MaterialTheme.typography.headlineSmall)
            Text("Set up viewing times and a lineup in the web app; the channel starts as soon as something is on.", style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted)
        } else {
            Text("That's all for now.", style = MaterialTheme.typography.headlineSmall)
            val back = s.now.nextWindowStart
            Text(
                if (back != null) "Back on ${TimeText.relative(back, nowWall)}" else "Nothing else is scheduled this week.",
                style = MaterialTheme.typography.bodyLarge,
                color = JellyColors.Muted,
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Guide", onClick = onGuide, icon = Icons.Default.CalendarMonth, primary = true, focusRequester = focus)
            TvButton("Recordings", onClick = onRecordings, icon = Icons.Default.FiberManualRecord)
            if (upNext != null) TvButton("Watch early", onClick = { onWatchEarly(upNext) }, icon = Icons.Default.PlayArrow)
            TvButton("Settings", onClick = onSettings, icon = Icons.Default.Settings)
        }
        Box(Modifier.fillMaxWidth().height(1.dp))
    }
}
