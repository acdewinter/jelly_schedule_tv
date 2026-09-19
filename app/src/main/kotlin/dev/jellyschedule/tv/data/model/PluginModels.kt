@file:UseSerializers(OffsetDateTimeSerializer::class, LocalDateSerializer::class)

package dev.jellyschedule.tv.data.model

import dev.jellyschedule.tv.data.json.LocalDateSerializer
import dev.jellyschedule.tv.data.json.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.OffsetDateTime

// Objects returned by the Jelly Schedule plugin API (docs/API.md in the plugin repository).
// GUIDs are kept as the 32-character hex strings the server sends.

@Serializable
enum class LiveMode { Relaxed, Strict }

@Serializable
enum class AiringKind { Episode, Movie, ReRun, OneOff }

@Serializable
data class UserInfo(
    val id: String = "",
    val name: String = "",
    val isAdmin: Boolean = false,
)

@Serializable
data class ScheduleSettings(
    val householdUserId: String = "",
    val timeZoneId: String = "",
    val slotMinutes: Int = 30,
    val fillMode: String = "Reruns",
    val liveMode: LiveMode = LiveMode.Relaxed,
    val autoplayOnOpen: Boolean = true,
    val includeSpecials: Boolean = false,
    val rerunPicker: String = "Random",
    val adminOnlyEditing: Boolean = false,
    val calendarKey: String = "",
    val joinGraceMinutes: Int = 0,
)

@Serializable
data class ViewingWindow(
    val id: String = "",
    val days: List<String> = emptyList(),
    val start: String = "20:00",
    val end: String = "22:00",
    val label: String? = null,
)

@Serializable
data class LineupEntry(
    val id: String = "",
    val itemId: String = "",
    val kind: String = "Series",
    val mode: String = "InOrder",
    val name: String = "",
    val days: List<String> = emptyList(),
    val episodesPerAiring: Int = 1,
    val order: Int = 0,
    val paused: Boolean = false,
    val startFrom: String? = null,
    val addedAt: OffsetDateTime? = null,
)

@Serializable
data class MovieNightSettings(
    val days: List<String> = emptyList(),
    val position: String = "Start",
    val order: String = "AsAdded",
    val startTime: String? = null,
)

@Serializable
data class Blackout(
    val id: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val label: String = "Away",
)

@Serializable
data class OneOff(
    val id: String = "",
    val date: LocalDate,
    val start: String = "20:00",
    val itemId: String = "",
    val name: String = "",
    val durationMinutes: Int? = null,
)

@Serializable
data class StateResponse(
    val version: String = "",
    val me: UserInfo = UserInfo(),
    val householdUser: UserInfo? = null,
    val users: List<UserInfo> = emptyList(),
    val canEdit: Boolean = false,
    val settings: ScheduleSettings = ScheduleSettings(),
    val windows: List<ViewingWindow> = emptyList(),
    val lineup: List<LineupEntry> = emptyList(),
    val movieNight: MovieNightSettings = MovieNightSettings(),
    val blackouts: List<Blackout> = emptyList(),
    val oneOffs: List<OneOff> = emptyList(),
    val integrations: Map<String, Boolean> = emptyMap(),
    val serverTimeZone: String = "",
    val now: OffsetDateTime,
) {
    /** True when the signed-in user is not the household user whose watch history drives the schedule. */
    val householdMismatch: Boolean
        get() = householdUser != null && !me.id.equals(householdUser.id, ignoreCase = true)

    /** Nothing configured yet: the guide would carry `Warning: "empty"`. */
    val isEmptySchedule: Boolean
        get() = windows.isEmpty() || lineup.isEmpty()
}

@Serializable
data class Airing(
    val id: String,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val durationMinutes: Int = 0,
    val runtimeMinutes: Int = 0,
    val kind: AiringKind = AiringKind.Episode,
    val itemId: String,
    val seriesId: String? = null,
    val lineupEntryId: String? = null,
    val title: String = "",
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val year: Int? = null,
    val officialRating: String? = null,
    val communityRating: Float? = null,
    val hasPrimaryImage: Boolean = false,
    val seriesHasPrimaryImage: Boolean = false,
    val hasBackdrop: Boolean = false,
    val isSeasonPremiere: Boolean = false,
    val isSeasonFinale: Boolean = false,
    val isSeriesFinale: Boolean = false,
    val runsOver: Boolean = false,
    val isWatched: Boolean = false,
    val positionTicks: Long = 0,
    val isRecorded: Boolean = false,
    val recordingId: String? = null,
    val isMissed: Boolean = false,
    val isOnNow: Boolean = false,
    val isFrozen: Boolean = false,
)

@Serializable
data class WindowSpan(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val label: String? = null,
)

@Serializable
data class GuideDay(
    val date: LocalDate,
    val dayName: String = "",
    val isToday: Boolean = false,
    val isPast: Boolean = false,
    val blackout: Blackout? = null,
    val windows: List<WindowSpan> = emptyList(),
    val airings: List<Airing> = emptyList(),
)

@Serializable
data class WeekStats(
    val programmes: Int = 0,
    val episodes: Int = 0,
    val movies: Int = 0,
    val reruns: Int = 0,
    val scheduledMinutes: Int = 0,
    val watched: Int = 0,
    val missed: Int = 0,
)

@Serializable
data class ComingUpItem(
    val seriesId: String = "",
    val lineupEntryId: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val title: String = "",
    val airDate: OffsetDateTime? = null,
    val network: String? = null,
    val source: String = "",
    val hasFile: Boolean = false,
)

@Serializable
data class GuideResult(
    val now: OffsetDateTime,
    val timeZone: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val slotMinutes: Int = 30,
    val earliestStart: String? = null,
    val latestEnd: String? = null,
    val days: List<GuideDay> = emptyList(),
    val onNow: Airing? = null,
    val upNext: Airing? = null,
    val nextWindowStart: OffsetDateTime? = null,
    val stats: WeekStats = WeekStats(),
    val comingUp: List<ComingUpItem> = emptyList(),
    val warning: String? = null,
) {
    val isEmpty: Boolean get() = warning == "empty"
}

/** `GET now`: what is on, what is next, and today's programmes. */
@Serializable
data class NowResponse(
    val now: OffsetDateTime,
    val onNow: Airing? = null,
    val upNext: Airing? = null,
    val nextWindowStart: OffsetDateTime? = null,
    val today: List<Airing> = emptyList(),
)

@Serializable
data class ItemSummary(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val year: Int? = null,
    val overview: String? = null,
    val hasPrimaryImage: Boolean = false,
    val hasBackdrop: Boolean = false,
    val status: String? = null,
    val runtimeMinutes: Int = 0,
    val officialRating: String? = null,
    val communityRating: Float? = null,
)

@Serializable
data class UpcomingEpisode(
    val season: Int = 0,
    val episode: Int = 0,
    val title: String = "",
    val airDate: OffsetDateTime? = null,
    val hasFile: Boolean = false,
)

@Serializable
data class SeriesAiringInfo(
    val source: String = "",
    val status: String? = null,
    val network: String? = null,
    val nextEpisode: UpcomingEpisode? = null,
    val upcoming: List<UpcomingEpisode> = emptyList(),
    val airedNotAvailable: List<UpcomingEpisode> = emptyList(),
    val fetchedAt: OffsetDateTime? = null,
    val error: String? = null,
)

@Serializable
data class LineupEntryStatus(
    val entry: LineupEntry = LineupEntry(),
    val item: ItemSummary? = null,
    val missing: Boolean = false,
    val total: Int = 0,
    val watched: Int = 0,
    val recorded: Int = 0,
    val remaining: Int = 0,
    val caughtUp: Boolean = false,
    val next: ItemSummary? = null,
    val nextLabel: String? = null,
    val airing: SeriesAiringInfo? = null,
    val movies: List<ItemSummary> = emptyList(),
)

@Serializable
data class Recording(
    val id: String = "",
    val itemId: String = "",
    val lineupEntryId: String? = null,
    val airingStart: OffsetDateTime? = null,
    val recordedAt: OffsetDateTime? = null,
    val title: String = "",
    val subtitle: String? = null,
)

@Serializable
data class RecordingStatus(
    val recording: Recording = Recording(),
    val item: ItemSummary? = null,
    val seriesName: String? = null,
    val seriesId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isWatched: Boolean = false,
    val positionTicks: Long = 0,
    val missing: Boolean = false,
)

@Serializable
data class EpisodeInfo(
    val id: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val name: String = "",
    val watched: Boolean = false,
    val runtimeMinutes: Int = 0,
)

@Serializable
data class UpcomingMovie(
    val radarrId: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val tmdbId: Int? = null,
    val digitalRelease: OffsetDateTime? = null,
    val physicalRelease: OffsetDateTime? = null,
    val inCinemas: OffsetDateTime? = null,
    val status: String? = null,
    val isAvailable: Boolean = false,
)

// Request bodies.

@Serializable
data class RecordRequest(
    val itemId: String,
    val airingStart: OffsetDateTime? = null,
    val lineupEntryId: String? = null,
)

@Serializable
data class PlayStateRequest(
    val itemId: String,
    val positionTicks: Long? = null,
    val played: Boolean? = null,
)

@Serializable
data class ApiErrorBody(val message: String? = null)
