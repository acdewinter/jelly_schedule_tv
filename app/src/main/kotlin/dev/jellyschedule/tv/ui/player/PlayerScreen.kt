package dev.jellyschedule.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jellyschedule.tv.appGraph
import dev.jellyschedule.tv.domain.TimeText
import dev.jellyschedule.tv.domain.displaySubtitle
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.domain.formatSeconds
import dev.jellyschedule.tv.domain.posterItemId
import dev.jellyschedule.tv.ui.common.LivePill
import dev.jellyschedule.tv.ui.common.PosterImage
import dev.jellyschedule.tv.ui.common.ProgressBar
import dev.jellyschedule.tv.ui.common.Spinner
import dev.jellyschedule.tv.ui.common.TvButton
import dev.jellyschedule.tv.ui.common.graphViewModel
import dev.jellyschedule.tv.ui.common.rememberInitialFocus
import dev.jellyschedule.tv.ui.nav.PlaybackMode
import dev.jellyschedule.tv.ui.nav.PlayerRoute
import dev.jellyschedule.tv.ui.theme.JellyColors
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(route: PlayerRoute, onClose: () -> Unit, onOpenGuide: () -> Unit) {
    val vm = graphViewModel { PlayerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(route) { vm.start(route.airing, route.playbackMode, route.resume) }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                PlayerEvent.Close -> onClose()
                PlayerEvent.OpenGuide -> onOpenGuide()
            }
        }
    }
    LaunchedEffect(state.phase) {
        while (true) {
            vm.tick()
            delay(500)
        }
    }
    // Keep the screen on for as long as the player is showing.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { vm.onBack() }

    val rootMode = state.phase is PlayerPhase.Playing && !state.controlsVisible && state.menu == PlayerMenu.None
    LaunchedEffect(rootMode) { if (rootMode) runCatching { rootFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event -> handleKey(event, state, rootMode, vm) },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    player = vm.player
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null },
        )

        when (val phase = state.phase) {
            PlayerPhase.Loading -> LoadingOverlay(state)
            PlayerPhase.Playing -> PlayingOverlay(state, vm)
            is PlayerPhase.Error -> ErrorCard(phase, vm)
            is PlayerPhase.Countdown -> CountdownCard(phase, vm)
            is PlayerPhase.OffAir -> OffAirCard(phase, vm)
        }
    }
}

/** Global remote handling while the video has focus; everything else falls through to Compose focus navigation. */
private fun handleKey(event: KeyEvent, state: PlayerUiState, rootMode: Boolean, vm: PlayerViewModel): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val repeat = event.nativeKeyEvent.repeatCount
    // Media keys work in every phase.
    when (event.key) {
        Key.MediaPlayPause -> { if (state.phase is PlayerPhase.Playing) vm.togglePlayPause(); return true }
        Key.MediaPlay -> { if (state.phase is PlayerPhase.Playing && !state.isPlaying) vm.togglePlayPause(); return true }
        Key.MediaPause -> { if (state.phase is PlayerPhase.Playing && state.isPlaying) vm.togglePlayPause(); return true }
        Key.MediaStop -> { vm.close(); return true }
        Key.MediaFastForward -> { if (state.phase is PlayerPhase.Playing) seekRepeat(vm, +1, repeat, long = true); return true }
        Key.MediaRewind -> { if (state.phase is PlayerPhase.Playing) seekRepeat(vm, -1, repeat, long = true); return true }
        Key.Captions -> { if (state.phase is PlayerPhase.Playing) vm.openMenu(PlayerMenu.Subtitles); return true }
        else -> Unit
    }
    if (!rootMode) return false
    return when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { vm.togglePlayPause(); true }
        Key.DirectionLeft -> { seekRepeat(vm, -1, repeat, long = false); true }
        Key.DirectionRight -> { seekRepeat(vm, +1, repeat, long = false); true }
        Key.DirectionDown, Key.DirectionUp, Key.Menu -> { vm.showControls(); true }
        else -> false
    }
}

/** ±10 s on a press, ±60 s once the key is held (and every half second while it stays held). */
private fun seekRepeat(vm: PlayerViewModel, direction: Int, repeatCount: Int, long: Boolean) {
    val short = PlayerViewModel.SEEK_SHORT_MS * direction
    val extra = (PlayerViewModel.SEEK_LONG_MS - PlayerViewModel.SEEK_SHORT_MS) * direction
    val longStep = PlayerViewModel.SEEK_LONG_MS * direction
    when {
        long && repeatCount == 0 -> vm.seekBy(longStep)
        long -> if (repeatCount % 10 == 0) vm.seekBy(longStep)
        repeatCount == 0 -> vm.seekBy(short)
        repeatCount == 1 -> vm.seekBy(extra)
        repeatCount % 10 == 0 -> vm.seekBy(longStep)
    }
}

@Composable
private fun LoadingOverlay(state: PlayerUiState) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spinner(size = 56)
            Text(state.airing?.displayTitle ?: "", style = MaterialTheme.typography.headlineSmall)
            Text(state.airing?.displaySubtitle ?: "", style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted)
        }
    }
}

@Composable
private fun PlayingOverlay(state: PlayerUiState, vm: PlayerViewModel) {
    val airing = state.airing ?: return
    if (state.isBuffering) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 56) }
    }
    if (state.seekFeedback != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                state.seekFeedback,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
    if (!state.overlayVisible) return

    // Top: channel bug, title, subtitle.
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.mode) {
                PlaybackMode.Live -> LivePill("LIVE")
                PlaybackMode.Recording -> LivePill("RECORDING", color = JellyColors.Rec)
                PlaybackMode.Watch -> Unit
            }
            if (state.playMethod != null) Text(state.playMethod, style = MaterialTheme.typography.labelSmall, color = JellyColors.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(airing.displayTitle, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(airing.displaySubtitle, style = MaterialTheme.typography.titleLarge, color = JellyColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    // Bottom: progress, next, controls.
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 300.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            if (state.nextLabel != null) {
                Text(state.nextLabel, style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(formatSeconds(state.positionMs / 1000), style = MaterialTheme.typography.labelLarge)
                val duration = state.durationMs.coerceAtLeast(1)
                ProgressBar(state.positionMs.toFloat() / duration, Modifier.weight(1f), buffered = state.bufferedMs.toFloat() / duration)
                Text(formatSeconds(state.durationMs / 1000), style = MaterialTheme.typography.labelLarge, color = JellyColors.Muted)
            }
            if (state.controlsVisible) {
                Spacer(Modifier.height(16.dp))
                val first = rememberInitialFocus(state.controlsVisible)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(if (state.isPlaying) "Pause" else "Play", onClick = vm::togglePlayPause, icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, primary = true, focusRequester = first)
                    TvButton("10 s", onClick = { vm.seekBy(-PlayerViewModel.SEEK_SHORT_MS) }, icon = Icons.Default.Replay10)
                    TvButton("10 s", onClick = { vm.seekBy(PlayerViewModel.SEEK_SHORT_MS) }, icon = Icons.Default.Forward10)
                    TvButton("Subtitles", onClick = { vm.openMenu(PlayerMenu.Subtitles) }, icon = Icons.Default.Subtitles)
                    TvButton("Audio", onClick = { vm.openMenu(PlayerMenu.Audio) }, icon = Icons.Default.Audiotrack)
                    Spacer(Modifier.weight(1f))
                    TvButton("Close", onClick = vm::close, icon = Icons.Default.Close)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Press down for controls · left / right to seek · back to close", style = MaterialTheme.typography.labelSmall, color = JellyColors.Muted)
            }
        }
    }

    if (state.menu != PlayerMenu.None) TrackMenu(state, vm)
}

@Composable
private fun TrackMenu(state: PlayerUiState, vm: PlayerViewModel) {
    val isSubs = state.menu == PlayerMenu.Subtitles
    val options = if (isSubs) state.subtitleOptions else state.audioOptions
    val focus = rememberInitialFocus(state.menu)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (isSubs) "Subtitles" else "Audio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            if (options.isEmpty()) {
                Text(if (isSubs) "No text subtitles in this file." else "Only one audio track.", style = MaterialTheme.typography.bodyMedium, color = JellyColors.Muted)
            }
            options.forEachIndexed { i, option ->
                TvButton(
                    text = (if (option.selected) "✓  " else "") + option.label,
                    onClick = { if (isSubs) vm.selectSubtitle(option) else vm.selectAudio(option) },
                    modifier = Modifier.fillMaxWidth(),
                    primary = option.selected,
                    focusRequester = if (i == 0) focus else null,
                )
            }
            if (!isSubs && state.playMethod != "Transcoding") {
                Spacer(Modifier.height(16.dp))
                Text("Problems with stutter or a codec?", style = MaterialTheme.typography.bodySmall, color = JellyColors.Muted)
                TvButton("Force transcoding", onClick = vm::forceTranscodeNow, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            TvButton("Back", onClick = vm::closeMenu, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorCard(phase: PlayerPhase.Error, vm: PlayerViewModel) {
    val focus = rememberInitialFocus(phase)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 720.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Couldn't play this", style = MaterialTheme.typography.headlineMedium)
            Text(phase.message, style = MaterialTheme.typography.bodyLarge, color = JellyColors.Muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (phase.canTryTranscode) TvButton("Try transcoding", onClick = vm::forceTranscodeNow, primary = true, focusRequester = focus)
                TvButton("Close", onClick = vm::close, primary = !phase.canTryTranscode, focusRequester = if (phase.canTryTranscode) null else focus)
            }
        }
    }
}

@Composable
private fun CountdownCard(phase: PlayerPhase.Countdown, vm: PlayerViewModel) {
    val graph = LocalContext.current.appGraph
    val focus = rememberInitialFocus(phase.upNext.id)
    val next = phase.upNext
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 1000.dp).padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            PosterImage(graph.sessions.imageUrl(next.posterItemId ?: "", maxHeight = 450).takeIf { next.posterItemId != null }, Modifier.size(220.dp, 330.dp), title = next.displayTitle)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("UP NEXT · ${TimeText.wallTime(next.start)}", style = MaterialTheme.typography.labelLarge, color = JellyColors.Primary)
                Text(next.displayTitle, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(next.displaySubtitle, style = MaterialTheme.typography.titleLarge, color = JellyColors.Muted)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (phase.secondsLeft > 0) TimeText.countdown(phase.secondsLeft) else "Starting…",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (phase.canStartNow) TvButton("Start now", onClick = vm::startNow, icon = Icons.Default.PlayArrow, primary = true, focusRequester = focus)
                    TvButton("Guide", onClick = vm::openGuide, primary = !phase.canStartNow, focusRequester = if (phase.canStartNow) null else focus)
                    TvButton("Close", onClick = vm::close)
                }
            }
        }
    }
}

@Composable
private fun OffAirCard(phase: PlayerPhase.OffAir, vm: PlayerViewModel) {
    val graph = LocalContext.current.appGraph
    val focus = rememberInitialFocus(phase)
    val now = remember { graph.clock.nowInHouseholdZone() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Off air", style = MaterialTheme.typography.displayLarge, color = JellyColors.Muted, fontWeight = FontWeight.Black)
            Text("That's all for tonight", style = MaterialTheme.typography.headlineMedium)
            Text(
                phase.nextWindowStart?.let { "Back on ${TimeText.relative(it, now)}" } ?: "Nothing else is scheduled.",
                style = MaterialTheme.typography.bodyLarge,
                color = JellyColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Guide", onClick = vm::openGuide, primary = true, focusRequester = focus)
                TvButton("Close", onClick = vm::close)
            }
        }
    }
}
