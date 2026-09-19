package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.data.model.RecordingStatus
import java.util.Locale

/** Title / subtitle / badge / image rules shared by every screen, mirroring the plugin's web app. */

val Airing.displayTitle: String
    get() = if (kind == AiringKind.Movie || seriesName.isNullOrBlank()) title else seriesName

val Airing.displaySubtitle: String
    get() = if (kind == AiringKind.Movie) {
        listOfNotNull(year?.toString(), officialRating, runtimeMinutes.takeIf { it > 0 }?.let(::formatMinutes)).joinToString(" · ")
    } else {
        listOfNotNull(episodeCode(season, episode), title.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

/** The item whose primary image is the poster: the series for episodes, the item itself otherwise. */
val Airing.posterItemId: String?
    get() = when {
        kind != AiringKind.Movie && seriesId != null && seriesHasPrimaryImage -> seriesId
        hasPrimaryImage -> itemId
        else -> null
    }

val Airing.backdropItemId: String?
    get() = when {
        !hasBackdrop -> null
        kind == AiringKind.Movie || seriesId == null -> itemId
        else -> seriesId
    }

enum class AiringBadge(val label: String) {
    OnNow("On now"),
    Movie("Movie"),
    ReRun("Re-run"),
    OneOff("One-off"),
    SeriesFinale("Series finale"),
    SeasonFinale("Season finale"),
    Premiere("Premiere"),
    Recorded("Rec"),
    Watched("Watched"),
    Missed("Missed"),
    InProgress("In progress"),
    RunsLate("Runs late"),
}

fun Airing.badges(compact: Boolean = false, skipLive: Boolean = false): List<AiringBadge> = buildList {
    if (isOnNow && !skipLive) add(AiringBadge.OnNow)
    when (kind) {
        AiringKind.Movie -> add(AiringBadge.Movie)
        AiringKind.ReRun -> add(AiringBadge.ReRun)
        AiringKind.OneOff -> add(AiringBadge.OneOff)
        AiringKind.Episode -> Unit
    }
    when {
        isSeriesFinale -> add(AiringBadge.SeriesFinale)
        isSeasonFinale -> add(AiringBadge.SeasonFinale)
        isSeasonPremiere -> add(AiringBadge.Premiere)
    }
    if (isRecorded) add(AiringBadge.Recorded)
    when {
        isWatched -> add(AiringBadge.Watched)
        isMissed -> add(AiringBadge.Missed)
        positionTicks > 0 && !compact -> add(AiringBadge.InProgress)
    }
    if (runsOver && !compact) add(AiringBadge.RunsLate)
}

fun episodeCode(season: Int?, episode: Int?): String? =
    if (season == null && episode == null) null else String.format(Locale.ROOT, "S%02dE%02d", season ?: 0, episode ?: 0)

fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

fun formatTicks(ticks: Long): String = formatSeconds(ticks / TuneInRules.TICKS_PER_SECOND)

fun formatSeconds(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

/** A recording presented like an airing so the player and rows can share code. */
fun RecordingStatus.toAiring(): Airing {
    val item = this.item
    val isEpisode = item?.type.equals("Episode", ignoreCase = true) || seriesId != null
    val recordedAt = recording.recordedAt ?: recording.airingStart ?: java.time.OffsetDateTime.now()
    return Airing(
        id = "recording-${recording.id}",
        start = recording.airingStart ?: recordedAt,
        end = recording.airingStart ?: recordedAt,
        durationMinutes = item?.runtimeMinutes ?: 0,
        runtimeMinutes = item?.runtimeMinutes ?: 0,
        kind = if (isEpisode) AiringKind.Episode else AiringKind.Movie,
        itemId = recording.itemId,
        seriesId = seriesId,
        lineupEntryId = recording.lineupEntryId,
        title = if (isEpisode) (recording.subtitle?.substringAfter(" · ", "")?.ifBlank { null } ?: item?.name ?: recording.title) else recording.title,
        seriesName = if (isEpisode) (seriesName ?: recording.title) else null,
        season = season,
        episode = episode,
        overview = item?.overview,
        year = item?.year,
        officialRating = item?.officialRating,
        communityRating = item?.communityRating,
        hasPrimaryImage = item?.hasPrimaryImage ?: false,
        seriesHasPrimaryImage = seriesId != null,
        hasBackdrop = item?.hasBackdrop ?: false,
        isWatched = isWatched,
        positionTicks = positionTicks,
        isRecorded = true,
        recordingId = recording.id,
    )
}
