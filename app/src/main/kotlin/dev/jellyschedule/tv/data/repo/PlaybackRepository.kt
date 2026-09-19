package dev.jellyschedule.tv.data.repo

import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.player.DeviceProfileBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import java.io.IOException
import java.util.UUID

/** Thrown when Jellyfin has no way to play the item on this device. */
class PlaybackUnavailableException(message: String) : IOException(message)

data class SubtitleTrack(
    val index: Int,
    val language: String?,
    val label: String,
    val url: String,
    val isDefault: Boolean,
    val isForced: Boolean,
)

data class AudioTrack(
    val index: Int,
    val language: String?,
    val label: String,
    val isDefault: Boolean,
)

data class PlaybackSession(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val method: PlayMethod,
    val streamUrl: String,
    val isHls: Boolean,
    val subtitles: List<SubtitleTrack>,
    val audioTracks: List<AudioTrack>,
    val audioIndex: Int?,
    val runtimeTicks: Long,
    val startTicks: Long,
) {
    val isTranscode: Boolean get() = method == PlayMethod.TRANSCODE
}

/**
 * Jellyfin's standard playback flow: `PlaybackInfo` with our device profile, then a static stream
 * (direct play / direct stream) or the HLS transcode the server hands back, plus play-state reporting.
 */
class PlaybackRepository(
    private val sessions: SessionRepository,
    private val profiles: DeviceProfileBuilder,
) {
    suspend fun prepare(
        itemId: String,
        startTicks: Long,
        audioIndex: Int? = null,
        forceTranscode: Boolean = false,
        maxBitrate: Int = DeviceProfileBuilder.MAX_BITRATE,
    ): PlaybackSession {
        val session = sessions.current
        val base = session.serverUrl?.trimEnd('/') ?: throw IOException("Not signed in")
        val token = session.accessToken ?: throw IOException("Not signed in")
        val api = sessions.api
        val info = try {
            api.mediaInfoApi.getPostedPlaybackInfo(
                itemId = itemId.toUuid(),
                data = PlaybackInfoDto(
                    userId = session.userId?.toUuid(),
                    maxStreamingBitrate = maxBitrate,
                    startTimeTicks = startTicks,
                    audioStreamIndex = audioIndex,
                    deviceProfile = profiles.profile,
                    enableDirectPlay = !forceTranscode,
                    enableDirectStream = !forceTranscode,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true,
                    autoOpenLiveStream = true,
                ),
            ).content
        } catch (e: InvalidStatusException) {
            if (e.status == 401) sessions.onUnauthorized()
            throw ApiException(e.status, if (e.status == 401) "Your session has expired. Please sign in again." else "PlaybackInfo failed (HTTP ${e.status}).")
        }
        info.errorCode?.let { throw PlaybackUnavailableException("The server refused playback: $it") }
        val source = info.mediaSources.firstOrNull() ?: throw PlaybackUnavailableException("No playable media source.")
        val sourceId = source.id ?: itemId

        val method: PlayMethod
        val url: String
        val isHls: Boolean
        if (!forceTranscode && (source.supportsDirectPlay || source.supportsDirectStream)) {
            method = if (source.supportsDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.DIRECT_STREAM
            val container = source.container?.split(',')?.firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() } ?: "mp4"
            url = "$base/Videos/$itemId/stream.$container".toHttpUrl().newBuilder()
                .addQueryParameter("static", "true")
                .addQueryParameter("mediaSourceId", sourceId)
                .addQueryParameter("deviceId", sessions.deviceId)
                .addQueryParameter("api_key", token)
                .apply { info.playSessionId?.let { addQueryParameter("PlaySessionId", it) } }
                .apply { source.eTag?.let { addQueryParameter("Tag", it) } }
                .build().toString()
            isHls = false
        } else if (source.supportsTranscoding && !source.transcodingUrl.isNullOrBlank()) {
            method = PlayMethod.TRANSCODE
            val path = source.transcodingUrl!!
            url = if (path.startsWith("http://") || path.startsWith("https://")) path else base + (if (path.startsWith("/")) path else "/$path")
            isHls = source.transcodingSubProtocol == MediaStreamProtocol.HLS || url.contains(".m3u8", ignoreCase = true)
        } else {
            throw PlaybackUnavailableException("This file cannot be played on this device.")
        }

        return PlaybackSession(
            itemId = itemId,
            mediaSourceId = sourceId,
            playSessionId = info.playSessionId,
            method = method,
            streamUrl = url,
            isHls = isHls,
            subtitles = subtitleTracks(base, token, itemId, sourceId, source),
            audioTracks = audioTracks(source),
            audioIndex = audioIndex ?: source.defaultAudioStreamIndex ?: audioTracks(source).firstOrNull { it.isDefault }?.index
                ?: audioTracks(source).firstOrNull()?.index,
            runtimeTicks = source.runTimeTicks ?: 0L,
            startTicks = startTicks,
        )
    }

    private fun subtitleTracks(base: String, token: String, itemId: String, sourceId: String, source: MediaSourceInfo): List<SubtitleTrack> =
        source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && (it.isTextSubtitleStream || it.supportsExternalStream) }
            .map { stream ->
                SubtitleTrack(
                    index = stream.index,
                    language = stream.language,
                    label = stream.displayTitle ?: stream.title ?: stream.language ?: "Subtitles ${stream.index}",
                    url = "$base/Videos/$itemId/$sourceId/Subtitles/${stream.index}/0/Stream.vtt?api_key=$token",
                    isDefault = stream.isDefault,
                    isForced = stream.isForced,
                )
            }

    private fun audioTracks(source: MediaSourceInfo): List<AudioTrack> =
        source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.AUDIO }
            .map { stream ->
                AudioTrack(
                    index = stream.index,
                    language = stream.language,
                    label = stream.displayTitle ?: listOfNotNull(stream.language, stream.codec?.uppercase(), stream.channelLayout).joinToString(" - ").ifBlank { "Audio ${stream.index}" },
                    isDefault = stream.isDefault,
                )
            }

    suspend fun reportStart(session: PlaybackSession, positionTicks: Long, paused: Boolean, subtitleIndex: Int?) {
        sessions.api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = session.itemId.toUuid(),
                mediaSourceId = session.mediaSourceId,
                audioStreamIndex = session.audioIndex,
                subtitleStreamIndex = subtitleIndex,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = session.method,
                playSessionId = session.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportProgress(session: PlaybackSession, positionTicks: Long, paused: Boolean, subtitleIndex: Int?) {
        sessions.api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = session.itemId.toUuid(),
                mediaSourceId = session.mediaSourceId,
                audioStreamIndex = session.audioIndex,
                subtitleStreamIndex = subtitleIndex,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = session.method,
                playSessionId = session.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    /** Reports the stop and, for a transcode, asks the server to stop encoding. */
    suspend fun reportStopped(session: PlaybackSession, positionTicks: Long) {
        try {
            sessions.api.playStateApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = session.itemId.toUuid(),
                    mediaSourceId = session.mediaSourceId,
                    positionTicks = positionTicks,
                    playSessionId = session.playSessionId,
                    failed = false,
                ),
            )
        } finally {
            val playSessionId = session.playSessionId
            if (session.isTranscode && playSessionId != null) {
                runCatching { sessions.api.hlsSegmentApi.stopEncodingProcess(deviceId = sessions.deviceId, playSessionId = playSessionId) }
            }
        }
    }
}

/** Jellyfin sends GUIDs as 32 hex characters; the SDK wants `java.util.UUID`. */
fun String.toUuid(): UUID {
    val s = trim()
    if (s.length == 32 && s.all { it.isLetterOrDigit() }) {
        return UUID.fromString("${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-${s.substring(16, 20)}-${s.substring(20)}")
    }
    return UUID.fromString(s)
}
