package dev.jellyschedule.tv.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.LiveMode
import dev.jellyschedule.tv.data.model.NowResponse
import dev.jellyschedule.tv.data.repo.PlaybackSession
import dev.jellyschedule.tv.data.repo.PlaybackUnavailableException
import dev.jellyschedule.tv.di.AppGraph
import dev.jellyschedule.tv.domain.AdvanceDecision
import dev.jellyschedule.tv.domain.AutoAdvance
import dev.jellyschedule.tv.domain.TimeText
import dev.jellyschedule.tv.domain.TuneInRules
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.ui.nav.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PlayMethod
import java.time.OffsetDateTime

data class TrackOption(val id: String, val label: String, val selected: Boolean)

enum class PlayerMenu { None, Subtitles, Audio }

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Playing : PlayerPhase
    data class Error(val message: String, val canTryTranscode: Boolean) : PlayerPhase
    data class Countdown(val upNext: Airing, val secondsLeft: Long, val canStartNow: Boolean) : PlayerPhase
    data class OffAir(val nextWindowStart: OffsetDateTime?) : PlayerPhase
}

data class PlayerUiState(
    val airing: Airing? = null,
    val mode: PlaybackMode = PlaybackMode.Watch,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val nextLabel: String? = null,
    val overlayVisible: Boolean = true,
    val controlsVisible: Boolean = false,
    val menu: PlayerMenu = PlayerMenu.None,
    val subtitleOptions: List<TrackOption> = emptyList(),
    val audioOptions: List<TrackOption> = emptyList(),
    val playMethod: String? = null,
    val seekFeedback: String? = null,
)

sealed interface PlayerEvent {
    data object Close : PlayerEvent
    data object OpenGuide : PlayerEvent
}

/**
 * Drives one ExoPlayer for the lifetime of the player screen: prepares Jellyfin playback, reports
 * play state, mirrors the household user's position, and advances the channel when a programme ends.
 */
class PlayerViewModel(private val graph: AppGraph) : ViewModel() {
    private val playback = graph.playback
    private val schedule = graph.schedule
    private val clock = graph.clock

    val player: ExoPlayer = ExoPlayer.Builder(graph.appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(graph.appContext)
                .setDataSourceFactory(OkHttpDataSource.Factory(graph.okHttp).setUserAgent("JellyScheduleTV")),
        )
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_SHORT_MS)
        .setSeekForwardIncrementMs(SEEK_SHORT_MS)
        .build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayerEvent> = _events

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var current: Airing? = null
    private var mode: PlaybackMode = PlaybackMode.Watch
    private var session: PlaybackSession? = null
    private var forceTranscode = false
    private var ended = false
    private var started = false
    private var appliedInitialSubtitle = false
    private var selectedSubtitleIndex: Int? = null
    private var lastNow: NowResponse? = null

    private var progressJob: Job? = null
    private var mirrorJob: Job? = null
    private var countdownJob: Job? = null
    private var overlayJob: Job? = null
    private var feedbackJob: Job? = null

    private val liveMode: LiveMode get() = schedule.cachedState?.settings?.liveMode ?: LiveMode.Relaxed
    private val householdMismatch: Boolean get() = schedule.cachedState?.householdMismatch == true

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> onEnded()
                Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                Player.STATE_READY -> _state.update { it.copy(isBuffering = false, durationMs = durationMs()) }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            reportProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            val s = session
            if (s != null && !forceTranscode && !s.isTranscode) {
                // Direct play failed on this device: fall back to a transcode from the same position.
                val pos = positionTicks()
                current?.let { launchPlayback(it, mode, startTicks = pos, forceTranscode = true) }
            } else {
                _state.update { it.copy(phase = PlayerPhase.Error(describe(error), canTryTranscode = false)) }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!appliedInitialSubtitle && tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                appliedInitialSubtitle = true
                applyPreferredSubtitle(tracks)
            }
            rebuildTrackOptions(tracks)
        }
    }

    init {
        player.addListener(listener)
    }

    // ---- lifecycle from the screen

    fun start(airing: Airing, mode: PlaybackMode, resume: Boolean) {
        if (started) return
        started = true
        launchPlayback(airing, mode, resume = resume)
    }

    /** Called ~twice a second while the screen is visible to refresh the progress bar. */
    fun tick() {
        if (player.playbackState == Player.STATE_IDLE) return
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = durationMs(),
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            )
        }
    }

    // ---- user actions

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        showOverlay()
    }

    fun seekBy(deltaMs: Long) {
        val duration = durationMs()
        val target = (player.currentPosition + deltaMs).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
        showFeedback(if (deltaMs >= 0) "+${deltaMs / 1000} s" else "−${-deltaMs / 1000} s")
        showOverlay()
        reportProgress()
    }

    fun showOverlay() {
        _state.update { it.copy(overlayVisible = true) }
        scheduleOverlayHide()
    }

    fun showControls() {
        _state.update { it.copy(overlayVisible = true, controlsVisible = true) }
        scheduleOverlayHide()
    }

    fun hideControls() {
        _state.update { it.copy(controlsVisible = false, menu = PlayerMenu.None) }
    }

    fun openMenu(menu: PlayerMenu) {
        overlayJob?.cancel()
        rebuildTrackOptions(player.currentTracks)
        _state.update { it.copy(menu = menu, overlayVisible = true, controlsVisible = true) }
    }

    fun closeMenu() {
        _state.update { it.copy(menu = PlayerMenu.None) }
        scheduleOverlayHide()
    }

    /** Back: close the menu, then the controls, then the player. */
    fun onBack() {
        val s = _state.value
        when {
            s.menu != PlayerMenu.None -> closeMenu()
            s.controlsVisible && s.phase is PlayerPhase.Playing -> hideControls()
            else -> close()
        }
    }

    fun selectSubtitle(option: TrackOption) {
        val tracks = player.currentTracks
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (option.id == "off") {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            selectedSubtitleIndex = null
        } else {
            val group = textGroups(tracks).firstOrNull { trackId(it) == option.id }
            if (group != null) {
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            }
            selectedSubtitleIndex = option.id.removePrefix("jf:").toIntOrNull()
        }
        player.trackSelectionParameters = params.build()
        val language = session?.subtitles?.firstOrNull { "jf:${it.index}" == option.id }?.language
        viewModelScope.launch { graph.sessions.setSubtitleLanguage(if (option.id == "off") null else language) }
        closeMenu()
        reportProgress()
    }

    fun selectAudio(option: TrackOption) {
        val s = session ?: return
        closeMenu()
        if (option.id.startsWith("grp:")) {
            val index = option.id.removePrefix("grp:").toIntOrNull() ?: return
            val group = audioGroups(player.currentTracks).getOrNull(index) ?: return
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                .build()
            reportProgress()
        } else if (option.id.startsWith("jf:")) {
            // Transcode: the server picks the audio stream, so ask for a new stream at the same position.
            val index = option.id.removePrefix("jf:").toIntOrNull() ?: return
            if (index == s.audioIndex) return
            current?.let { launchPlayback(it, mode, startTicks = positionTicks(), audioIndex = index, forceTranscode = forceTranscode) }
        }
    }

    fun forceTranscodeNow() {
        val airing = current ?: return
        launchPlayback(airing, mode, startTicks = positionTicks(), forceTranscode = true)
    }

    /** Relaxed mode only: skip the countdown and start the next programme now. */
    fun startNow() {
        val phase = _state.value.phase as? PlayerPhase.Countdown ?: return
        countdownJob?.cancel()
        launchPlayback(phase.upNext, PlaybackMode.Live)
    }

    fun openGuide() {
        viewModelScope.launch {
            finishSession()
            _events.emit(PlayerEvent.OpenGuide)
        }
    }

    fun close() {
        viewModelScope.launch {
            finishSession()
            _events.emit(PlayerEvent.Close)
        }
    }

    // ---- playback

    private fun launchPlayback(
        airing: Airing,
        mode: PlaybackMode,
        resume: Boolean = false,
        startTicks: Long? = null,
        audioIndex: Int? = null,
        forceTranscode: Boolean = graph.sessions.preferences.value.alwaysTranscode,
    ) {
        viewModelScope.launch {
            countdownJob?.cancel()
            stopReporting()
            val previous = session
            if (previous != null && !ended) {
                val pos = positionTicks()
                background.launch { runCatching { playback.reportStopped(previous, pos) } }
            }
            session = null
            ended = false
            appliedInitialSubtitle = false
            current = airing
            this@PlayerViewModel.mode = mode
            this@PlayerViewModel.forceTranscode = forceTranscode
            _state.update {
                it.copy(
                    airing = airing,
                    mode = mode,
                    phase = PlayerPhase.Loading,
                    isPlaying = false,
                    positionMs = 0,
                    durationMs = airing.runtimeMinutes * 60_000L,
                    bufferedMs = 0,
                    nextLabel = null,
                    menu = PlayerMenu.None,
                    controlsVisible = false,
                    overlayVisible = true,
                    subtitleOptions = emptyList(),
                    audioOptions = emptyList(),
                    playMethod = null,
                )
            }
            val ticks = startTicks ?: TuneInRules.startPositionTicks(airing, liveMode, clock.now(), live = mode == PlaybackMode.Live, resume = resume)
            try {
                val s = playback.prepare(airing.itemId, ticks, audioIndex = audioIndex, forceTranscode = forceTranscode)
                session = s
                selectedSubtitleIndex = null
                val item = MediaItem.Builder()
                    .setUri(s.streamUrl)
                    .apply { if (s.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .setSubtitleConfigurations(
                        s.subtitles.map { sub ->
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(sub.language)
                                .setLabel(sub.label)
                                .setId("jf:${sub.index}")
                                .build()
                        },
                    )
                    .build()
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverrides()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                player.setMediaItem(item, ticks / TuneInRules.TICKS_PER_MILLISECOND)
                player.prepare()
                player.playWhenReady = true
                _state.update {
                    it.copy(
                        phase = PlayerPhase.Playing,
                        playMethod = when (s.method) {
                            PlayMethod.DIRECT_PLAY -> "Direct play"
                            PlayMethod.DIRECT_STREAM -> "Direct stream"
                            PlayMethod.TRANSCODE -> "Transcoding"
                        },
                    )
                }
                background.launch { runCatching { playback.reportStart(s, ticks, paused = false, subtitleIndex = selectedSubtitleIndex) } }
                startReporting()
                scheduleOverlayHide()
                if (mode == PlaybackMode.Live) refreshNextLabel(airing)
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private fun onFailure(e: Exception) {
        val canTranscode = !forceTranscode && e !is PlaybackUnavailableException
        _state.update { it.copy(phase = PlayerPhase.Error(e.message ?: "Couldn't play this.", canTryTranscode = canTranscode)) }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Lost the connection to the server."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused the stream (${error.message})."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This TV cannot decode the file."
        else -> error.message ?: "Playback error (${error.errorCodeName})."
    }

    private fun refreshNextLabel(airing: Airing) {
        viewModelScope.launch {
            val now = runCatching { schedule.now() }.getOrNull() ?: return@launch
            lastNow = now
            val next = now.upNext?.takeIf { it.id != airing.id && it.itemId != airing.itemId }
            _state.update { it.copy(nextLabel = next?.let { n -> "Next: ${n.displayTitle} at ${TimeText.wallTime(n.start)}" }) }
        }
    }

    private fun onEnded() {
        if (ended) return
        ended = true
        stopReporting()
        val s = session
        val pos = positionTicks()
        viewModelScope.launch {
            if (s != null) {
                runCatching { playback.reportStopped(s, pos) }
                mirror(s, pos, final = true)
            }
            when (mode) {
                PlaybackMode.Live -> advance()
                else -> _events.emit(PlayerEvent.Close)
            }
        }
    }

    private suspend fun advance() {
        val airing = current
        val now = try {
            schedule.now()
        } catch (e: Exception) {
            _events.emit(PlayerEvent.Close)
            return
        }
        lastNow = now
        when (val decision = AutoAdvance.decide(now, airing, clock.now())) {
            is AdvanceDecision.PlayNext -> launchPlayback(decision.airing, PlaybackMode.Live)
            is AdvanceDecision.Countdown -> startCountdown(decision.upNext)
            is AdvanceDecision.OffAir -> _state.update {
                it.copy(phase = PlayerPhase.OffAir(decision.nextWindowStart), controlsVisible = false, menu = PlayerMenu.None)
            }
        }
    }

    private fun startCountdown(next: Airing) {
        countdownJob?.cancel()
        val canStartNow = liveMode == LiveMode.Relaxed
        countdownJob = viewModelScope.launch {
            while (true) {
                val left = AutoAdvance.secondsUntil(next, clock.now())
                _state.update { it.copy(phase = PlayerPhase.Countdown(next, left, canStartNow), controlsVisible = false, menu = PlayerMenu.None) }
                if (left <= 0) {
                    launchPlayback(next, PlaybackMode.Live)
                    return@launch
                }
                delay(1_000)
            }
        }
    }

    private suspend fun finishSession() {
        countdownJob?.cancel()
        stopReporting()
        val s = session
        if (s != null && !ended) {
            val pos = positionTicks()
            runCatching { playback.reportStopped(s, pos) }
            mirror(s, pos, final = false)
        }
        ended = true
        session = null
        player.stop()
    }

    // ---- reporting

    private fun startReporting() {
        stopReporting()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(PROGRESS_INTERVAL_MS)
                reportProgress()
            }
        }
        if (householdMismatch) {
            mirrorJob = viewModelScope.launch {
                while (true) {
                    delay(MIRROR_INTERVAL_MS)
                    session?.let { mirror(it, positionTicks(), final = false) }
                }
            }
        }
    }

    private fun stopReporting() {
        progressJob?.cancel()
        mirrorJob?.cancel()
        progressJob = null
        mirrorJob = null
    }

    private fun reportProgress() {
        val s = session ?: return
        if (ended) return
        val pos = positionTicks()
        val paused = !player.playWhenReady
        val sub = selectedSubtitleIndex
        background.launch { runCatching { playback.reportProgress(s, pos, paused, sub) } }
    }

    private suspend fun mirror(s: PlaybackSession, positionTicks: Long, final: Boolean) {
        if (!householdMismatch) return
        val runtime = if (s.runtimeTicks > 0) s.runtimeTicks else durationMs() * TuneInRules.TICKS_PER_MILLISECOND
        val played = final && runtime > 0 && positionTicks.toDouble() / runtime >= 0.9
        runCatching { schedule.mirrorPlayState(s.itemId, positionTicks, played) }
    }

    // ---- tracks

    private fun textGroups(tracks: Tracks) = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
    private fun audioGroups(tracks: Tracks) = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }

    private fun trackId(group: Tracks.Group): String {
        val id = group.getTrackFormat(0).id
        return if (id != null && id.startsWith("jf:")) id else "emb:${group.mediaTrackGroup.id}"
    }

    private fun rebuildTrackOptions(tracks: Tracks) {
        val s = session
        val subs = buildList {
            val anySelected = textGroups(tracks).any { it.isSelected } && !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            add(TrackOption("off", "Off", selected = !anySelected))
            textGroups(tracks).forEach { group ->
                val id = trackId(group)
                val format = group.getTrackFormat(0)
                val label = s?.subtitles?.firstOrNull { "jf:${it.index}" == id }?.label
                    ?: format.label ?: format.language?.let { "Embedded ($it)" } ?: "Embedded subtitles"
                add(TrackOption(id, label, selected = anySelected && group.isSelected))
            }
        }
        val audio = buildList {
            if (s != null && s.isTranscode) {
                s.audioTracks.forEach { add(TrackOption("jf:${it.index}", it.label, selected = it.index == s.audioIndex)) }
            } else {
                val groups = audioGroups(tracks)
                groups.forEachIndexed { i, group ->
                    val format = group.getTrackFormat(0)
                    val jf = s?.audioTracks?.getOrNull(i)?.takeIf { s.audioTracks.size == groups.size }
                    val label = jf?.label ?: format.label ?: listOfNotNull(format.language, format.sampleMimeType?.substringAfter('/')).joinToString(" · ").ifBlank { "Audio ${i + 1}" }
                    add(TrackOption("grp:$i", label, selected = group.isSelected))
                }
            }
        }
        _state.update { it.copy(subtitleOptions = subs, audioOptions = audio) }
    }

    private fun applyPreferredSubtitle(tracks: Tracks) {
        val wanted = graph.sessions.preferences.value.subtitleLanguage ?: return
        val group = textGroups(tracks).firstOrNull { g ->
            val id = trackId(g)
            val lang = session?.subtitles?.firstOrNull { "jf:${it.index}" == id }?.language ?: g.getTrackFormat(0).language
            languageMatches(lang, wanted)
        } ?: return
        selectedSubtitleIndex = trackId(group).removePrefix("jf:").toIntOrNull()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    // ---- helpers

    private fun positionTicks(): Long = player.currentPosition.coerceAtLeast(0) * TuneInRules.TICKS_PER_MILLISECOND

    private fun durationMs(): Long {
        val d = player.duration
        if (d != C.TIME_UNSET && d > 0) return d
        val s = session
        if (s != null && s.runtimeTicks > 0) return s.runtimeTicks / TuneInRules.TICKS_PER_MILLISECOND
        return (current?.runtimeMinutes ?: 0) * 60_000L
    }

    private fun scheduleOverlayHide() {
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            if (_state.value.menu == PlayerMenu.None && player.isPlaying) {
                _state.update { it.copy(overlayVisible = false, controlsVisible = false) }
            }
        }
    }

    private fun showFeedback(text: String) {
        feedbackJob?.cancel()
        _state.update { it.copy(seekFeedback = text) }
        feedbackJob = viewModelScope.launch {
            delay(900)
            _state.update { it.copy(seekFeedback = null) }
        }
    }

    override fun onCleared() {
        val s = session
        if (s != null && !ended) {
            val pos = positionTicks()
            background.launch { runCatching { playback.reportStopped(s, pos) } }
        }
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    companion object {
        const val SEEK_SHORT_MS = 10_000L
        const val SEEK_LONG_MS = 60_000L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val MIRROR_INTERVAL_MS = 30_000L
        const val OVERLAY_TIMEOUT_MS = 4_000L

        private val LANGUAGE_ALIASES = listOf(
            setOf("en", "eng"), setOf("nl", "nld", "dut"), setOf("de", "deu", "ger"), setOf("fr", "fra", "fre"), setOf("es", "spa"),
            setOf("it", "ita"), setOf("pt", "por"), setOf("sv", "swe"), setOf("no", "nor", "nob", "nno"), setOf("da", "dan"), setOf("fi", "fin"),
            setOf("pl", "pol"), setOf("ja", "jpn"), setOf("ko", "kor"), setOf("zh", "zho", "chi"), setOf("ru", "rus"), setOf("tr", "tur"),
            setOf("ar", "ara"), setOf("hi", "hin"), setOf("cs", "ces", "cze"), setOf("hu", "hun"), setOf("el", "ell", "gre"), setOf("he", "heb"),
            setOf("ro", "ron", "rum"), setOf("uk", "ukr"),
        )

        /** ISO 639-1 / 639-2 (bibliographic or terminologic) codes for the same language match. */
        fun languageMatches(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return false
            val x = a.lowercase().substringBefore('-')
            val y = b.lowercase().substringBefore('-')
            if (x == y) return true
            return LANGUAGE_ALIASES.any { x in it && y in it }
        }
    }
}
