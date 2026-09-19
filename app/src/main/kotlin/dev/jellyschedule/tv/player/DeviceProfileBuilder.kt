package dev.jellyschedule.tv.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.view.Display
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * Builds the Jellyfin device profile from what this TV can actually decode (`MediaCodecList`) and display,
 * so HEVC / AV1 / AC-3 direct-play when the hardware supports them and everything else is transcoded to
 * HLS (H.264 + AAC), which every device handles.
 */
class DeviceProfileBuilder(private val context: Context) {

    /** Summary of what was detected, for the settings screen. */
    data class Capabilities(
        val videoCodecs: List<String>,
        val audioCodecs: List<String>,
        val hevcMain10: Boolean,
        val hdrTypes: List<String>,
        val maxAvcLevel: Int,
        val maxHevcLevel: Int,
    )

    val capabilities: Capabilities by lazy { detect() }
    val profile: DeviceProfile by lazy { build(capabilities) }

    private fun detect(): Capabilities {
        val decoders = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder } }
            .getOrDefault(emptyList())
        val mimes = decoders.flatMap { info -> info.supportedTypes.map { it.lowercase() } }.toSet()
        fun has(vararg mime: String) = mime.any { it in mimes }

        val video = buildList {
            if (has("video/avc")) add("h264")
            if (has("video/hevc")) add("hevc")
            if (has("video/av01")) add("av1")
            if (has("video/x-vnd.on2.vp9")) add("vp9")
            if (has("video/x-vnd.on2.vp8")) add("vp8")
            if (has("video/mp4v-es")) add("mpeg4")
            if (has("video/mpeg2")) add("mpeg2video")
            if (has("video/3gpp")) add("h263")
        }
        val audio = buildList {
            if (has("audio/mp4a-latm")) add("aac")
            if (has("audio/mpeg")) add("mp3")
            if (has("audio/ac3")) add("ac3")
            if (has("audio/eac3")) add("eac3")
            if (has("audio/opus")) add("opus")
            if (has("audio/vorbis")) add("vorbis")
            if (has("audio/flac")) add("flac")
            if (has("audio/vnd.dts", "audio/vnd.dts.hd")) add("dts")
            if (has("audio/true-hd")) add("truehd")
            if (has("audio/alac")) add("alac")
            // PCM is decoded by ExoPlayer itself.
            add("pcm_s16le")
            add("pcm_s24le")
        }

        val hevcMain10 = decoders.any { info ->
            info.supportedTypes.any { it.equals("video/hevc", true) } && runCatching {
                info.getCapabilitiesForType("video/hevc").profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
            }.getOrDefault(false)
        }
        val maxAvcLevel = decoders.maxLevel("video/avc", AVC_LEVELS, 41)
        val maxHevcLevel = decoders.maxLevel("video/hevc", HEVC_LEVELS, 120)

        val hdr = buildList {
            val display = runCatching {
                (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            }.getOrNull()
            val types = runCatching { display?.hdrCapabilities?.supportedHdrTypes?.toList() }.getOrNull().orEmpty()
            if (Display.HdrCapabilities.HDR_TYPE_HDR10 in types) add("HDR10")
            if (Display.HdrCapabilities.HDR_TYPE_HLG in types) add("HLG")
            if (Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types) add("HDR10_PLUS")
            if (Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types && has("video/dolby-vision")) add("DOVI")
        }
        return Capabilities(video, audio, hevcMain10, hdr, maxAvcLevel, maxHevcLevel)
    }

    private fun List<MediaCodecInfo>.maxLevel(mime: String, table: Map<Int, Int>, fallback: Int): Int =
        mapNotNull { info ->
            if (info.supportedTypes.none { it.equals(mime, true) }) return@mapNotNull null
            runCatching { info.getCapabilitiesForType(mime).profileLevels.mapNotNull { table[it.level] }.maxOrNull() }.getOrNull()
        }.maxOrNull() ?: fallback

    private fun build(caps: Capabilities): DeviceProfile {
        val videoCodecs = caps.videoCodecs.joinToString(",")
        val audioCodecs = caps.audioCodecs.joinToString(",")
        val ranges = buildList {
            add("SDR")
            if ("HDR10" in caps.hdrTypes) add("HDR10")
            if ("HLG" in caps.hdrTypes) add("HLG")
            if ("HDR10_PLUS" in caps.hdrTypes) add("HDR10_PLUS")
            if ("DOVI" in caps.hdrTypes) addAll(listOf("DOVI", "DOVI_WITH_HDR10", "DOVI_WITH_HLG", "DOVI_WITH_SDR", "DOVI_WITH_EL", "DOVI_WITH_HDR10_PLUS"))
            else if ("HDR10" in caps.hdrTypes) addAll(listOf("DOVI_WITH_HDR10", "DOVI_WITH_HLG", "DOVI_WITH_SDR"))
        }.joinToString("|")

        val directPlay = listOf(
            DirectPlayProfile(container = "mp4,m4v,mov,mkv,webm,ts,mpegts,m2ts,avi,flv,3gp", type = DlnaProfileType.VIDEO, videoCodec = videoCodecs, audioCodec = audioCodecs),
            DirectPlayProfile(container = "mp3,aac,m4a,flac,ogg,oga,opus,wav", type = DlnaProfileType.AUDIO, audioCodec = audioCodecs),
        )

        val hlsVideo = buildList { add("h264"); if ("hevc" in caps.videoCodecs) add("hevc") }.joinToString(",")
        val hlsAudio = listOf("aac", "mp3", "ac3", "eac3").filter { it in caps.audioCodecs }.ifEmpty { listOf("aac") }.joinToString(",")
        val transcoding = listOf(
            TranscodingProfile(
                container = "ts",
                type = DlnaProfileType.VIDEO,
                videoCodec = hlsVideo,
                audioCodec = hlsAudio,
                protocol = MediaStreamProtocol.HLS,
                context = EncodingContext.STREAMING,
                minSegments = 1,
                breakOnNonKeyFrames = true,
                conditions = emptyList(),
            ),
            TranscodingProfile(
                container = "mp3",
                type = DlnaProfileType.AUDIO,
                videoCodec = "",
                audioCodec = "mp3",
                protocol = MediaStreamProtocol.HTTP,
                context = EncodingContext.STREAMING,
                conditions = emptyList(),
            ),
        )

        fun cond(property: ProfileConditionValue, type: ProfileConditionType, value: String, required: Boolean = false) =
            ProfileCondition(condition = type, property = property, value = value, isRequired = required)

        val codecProfiles = buildList {
            add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    conditions = listOf(
                        cond(ProfileConditionValue.IS_ANAMORPHIC, ProfileConditionType.NOT_EQUALS, "true"),
                        cond(ProfileConditionValue.VIDEO_PROFILE, ProfileConditionType.EQUALS_ANY, "high|main|baseline|constrained baseline"),
                        cond(ProfileConditionValue.VIDEO_LEVEL, ProfileConditionType.LESS_THAN_EQUAL, caps.maxAvcLevel.toString()),
                        cond(ProfileConditionValue.VIDEO_BIT_DEPTH, ProfileConditionType.LESS_THAN_EQUAL, "8"),
                        cond(ProfileConditionValue.VIDEO_RANGE_TYPE, ProfileConditionType.EQUALS_ANY, "SDR"),
                    ),
                    applyConditions = emptyList(),
                ),
            )
            if ("hevc" in caps.videoCodecs) add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "hevc",
                    conditions = listOf(
                        cond(ProfileConditionValue.IS_ANAMORPHIC, ProfileConditionType.NOT_EQUALS, "true"),
                        cond(ProfileConditionValue.VIDEO_PROFILE, ProfileConditionType.EQUALS_ANY, if (caps.hevcMain10) "main|main 10" else "main"),
                        cond(ProfileConditionValue.VIDEO_LEVEL, ProfileConditionType.LESS_THAN_EQUAL, caps.maxHevcLevel.toString()),
                        cond(ProfileConditionValue.VIDEO_BIT_DEPTH, ProfileConditionType.LESS_THAN_EQUAL, if (caps.hevcMain10) "10" else "8"),
                        cond(ProfileConditionValue.VIDEO_RANGE_TYPE, ProfileConditionType.EQUALS_ANY, ranges),
                    ),
                    applyConditions = emptyList(),
                ),
            )
            for (codec in listOf("av1", "vp9").filter { it in caps.videoCodecs }) add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = codec,
                    conditions = listOf(
                        cond(ProfileConditionValue.VIDEO_BIT_DEPTH, ProfileConditionType.LESS_THAN_EQUAL, "10"),
                        cond(ProfileConditionValue.VIDEO_RANGE_TYPE, ProfileConditionType.EQUALS_ANY, ranges),
                    ),
                    applyConditions = emptyList(),
                ),
            )
            add(
                CodecProfile(
                    type = CodecType.VIDEO_AUDIO,
                    conditions = listOf(cond(ProfileConditionValue.AUDIO_CHANNELS, ProfileConditionType.LESS_THAN_EQUAL, "8")),
                    applyConditions = emptyList(),
                ),
            )
        }

        val subtitles = buildList {
            // Text subtitles are side-loaded as WebVTT no matter how the video is delivered.
            for (format in listOf("vtt", "webvtt", "srt", "subrip", "ass", "ssa", "mov_text", "ttml"))
                add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EXTERNAL))
            // Image subtitles ExoPlayer can render from the container when direct playing.
            for (format in listOf("pgssub", "pgs", "dvdsub", "vobsub"))
                add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EMBED))
            add(SubtitleProfile(format = "dvbsub", method = SubtitleDeliveryMethod.ENCODE))
        }

        return DeviceProfile(
            name = "Jelly Schedule TV",
            maxStreamingBitrate = MAX_BITRATE,
            maxStaticBitrate = MAX_BITRATE,
            musicStreamingTranscodingBitrate = 320_000,
            directPlayProfiles = directPlay,
            transcodingProfiles = transcoding,
            containerProfiles = emptyList(),
            codecProfiles = codecProfiles,
            subtitleProfiles = subtitles,
        )
    }

    companion object {
        const val MAX_BITRATE = 120_000_000

        // MediaCodec level constants -> Jellyfin's numeric levels (H.264: level x10, HEVC: level x30).
        private val AVC_LEVELS = mapOf(
            MediaCodecInfo.CodecProfileLevel.AVCLevel1 to 10, MediaCodecInfo.CodecProfileLevel.AVCLevel1b to 11,
            MediaCodecInfo.CodecProfileLevel.AVCLevel11 to 11, MediaCodecInfo.CodecProfileLevel.AVCLevel12 to 12,
            MediaCodecInfo.CodecProfileLevel.AVCLevel13 to 13, MediaCodecInfo.CodecProfileLevel.AVCLevel2 to 20,
            MediaCodecInfo.CodecProfileLevel.AVCLevel21 to 21, MediaCodecInfo.CodecProfileLevel.AVCLevel22 to 22,
            MediaCodecInfo.CodecProfileLevel.AVCLevel3 to 30, MediaCodecInfo.CodecProfileLevel.AVCLevel31 to 31,
            MediaCodecInfo.CodecProfileLevel.AVCLevel32 to 32, MediaCodecInfo.CodecProfileLevel.AVCLevel4 to 40,
            MediaCodecInfo.CodecProfileLevel.AVCLevel41 to 41, MediaCodecInfo.CodecProfileLevel.AVCLevel42 to 42,
            MediaCodecInfo.CodecProfileLevel.AVCLevel5 to 50, MediaCodecInfo.CodecProfileLevel.AVCLevel51 to 51,
            MediaCodecInfo.CodecProfileLevel.AVCLevel52 to 52, MediaCodecInfo.CodecProfileLevel.AVCLevel6 to 60,
            MediaCodecInfo.CodecProfileLevel.AVCLevel61 to 61, MediaCodecInfo.CodecProfileLevel.AVCLevel62 to 62,
        )
        private val HEVC_LEVELS = mapOf(
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1 to 30, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1 to 30,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2 to 60, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2 to 60,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21 to 63, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21 to 63,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3 to 90, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3 to 90,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31 to 93, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31 to 93,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4 to 120, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4 to 120,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41 to 123, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41 to 123,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5 to 150, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5 to 150,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51 to 153, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51 to 153,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52 to 156, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52 to 156,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6 to 180, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6 to 180,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61 to 183, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61 to 183,
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62 to 186, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62 to 186,
        )
    }
}
