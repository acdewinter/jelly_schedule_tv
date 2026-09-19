package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.Fixtures
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.data.model.Blackout
import dev.jellyschedule.tv.data.model.GuideDay
import dev.jellyschedule.tv.data.model.GuideResult
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.data.model.WindowSpan
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class GuideWeeksTest {
    private val guide: GuideResult = Fixtures.load("guide.json", GuideResult.serializer())

    @Test
    fun `weeks start on monday`() {
        assertEquals(LocalDate.of(2026, 9, 14), GuideWeeks.mondayOf(LocalDate.of(2026, 9, 19)))
        assertEquals(LocalDate.of(2026, 9, 14), GuideWeeks.mondayOf(LocalDate.of(2026, 9, 14)))
        assertEquals(LocalDate.of(2026, 9, 14), GuideWeeks.mondayOf(LocalDate.of(2026, 9, 20)))
        assertEquals(listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21)), GuideWeeks.weekStarts(LocalDate.of(2026, 9, 19)))
    }

    @Test
    fun `day status distinguishes away, off air and scheduled`() {
        val statuses = guide.days.associate { it.date to GuideWeeks.status(it) }
        assertEquals(GuideWeeks.DayStatus.Away("Away"), statuses[LocalDate.of(2026, 9, 22)])
        assertEquals(GuideWeeks.DayStatus.OffAir(hasWindows = false), statuses[LocalDate.of(2026, 9, 21)])
        assertEquals(GuideWeeks.DayStatus.OffAir(hasWindows = true), statuses[LocalDate.of(2026, 9, 15)]) // past Tuesday: window but nothing kept
        assertEquals(GuideWeeks.DayStatus.Scheduled(2), statuses[LocalDate.of(2026, 9, 19)])
    }

    @Test
    fun `default selected day prefers today, then the next day, then the first`() {
        assertEquals(LocalDate.of(2026, 9, 19), GuideWeeks.defaultSelectedDay(guide.days, LocalDate.of(2026, 9, 19)))
        val nextWeek = guide.days.filter { it.date >= LocalDate.of(2026, 9, 21) }
        assertEquals(LocalDate.of(2026, 9, 21), GuideWeeks.defaultSelectedDay(nextWeek, LocalDate.of(2026, 9, 19)))
        val allPast = guide.days.map { it.copy(isPast = true, isToday = false) }.take(3)
        assertEquals(LocalDate.of(2026, 9, 14), GuideWeeks.defaultSelectedDay(allPast, LocalDate.of(2026, 10, 1)))
        assertEquals(null, GuideWeeks.defaultSelectedDay(emptyList(), LocalDate.of(2026, 9, 19)))
    }

    @Test
    fun `upcoming starts with up next and continues through the week`() {
        val upcoming = GuideWeeks.upcoming(guide, guide.now.toInstant(), limit = 5)
        assertEquals(guide.upNext!!.id, upcoming.first().id)
        assertEquals(5, upcoming.size)
        assertTrue(upcoming.zipWithNext().all { (a, b) -> !a.start.isAfter(b.start) })
        assertTrue(upcoming.all { it.start.toInstant().isAfter(guide.now.toInstant()) })
    }

    @Test
    fun `focus lands on what is on, else the next programme`() {
        val today = guide.days.single { it.isToday }
        assertEquals(0, GuideWeeks.focusIndex(today, guide.now.toInstant()))
        assertEquals(1, GuideWeeks.focusIndex(today.copy(airings = today.airings.map { it.copy(isOnNow = false) }), today.airings[0].end.toInstant().plusSeconds(1)))
        assertEquals(0, GuideWeeks.focusIndex(today, today.airings.last().end.toInstant().plusSeconds(1)))
    }

    @Test
    fun `synthetic day statuses`() {
        val t = OffsetDateTime.of(2026, 9, 22, 20, 0, 0, 0, ZoneOffset.UTC)
        val day = GuideDay(date = t.toLocalDate(), windows = listOf(WindowSpan(t, t.plusHours(2))))
        assertEquals(GuideWeeks.DayStatus.OffAir(true), GuideWeeks.status(day))
        val blackout = day.copy(blackout = Blackout(from = t.toLocalDate(), to = t.toLocalDate(), label = "Holiday"))
        assertEquals(GuideWeeks.DayStatus.Away("Holiday"), GuideWeeks.status(blackout))
        val scheduled = day.copy(airings = listOf(Airing(id = "a", start = t, end = t.plusMinutes(30), itemId = "i")))
        assertEquals(GuideWeeks.DayStatus.Scheduled(1), GuideWeeks.status(scheduled))
    }

    @Test
    fun `presentation helpers follow the web app`() {
        val onNow = guide.onNow!!
        assertEquals("Severance", onNow.displayTitle)
        assertEquals("S01E01 · Severance 1x01", onNow.displaySubtitle)
        assertEquals(onNow.seriesId, onNow.posterItemId)
        assertEquals(onNow.seriesId, onNow.backdropItemId)
        assertEquals(listOf(AiringBadge.OnNow, AiringBadge.Premiere), onNow.badges())
        assertEquals(listOf(AiringBadge.Premiere), onNow.badges(skipLive = true))

        val movie = guide.days.flatMap { it.airings }.first { it.kind == AiringKind.Movie }
        assertEquals("Dune: Part Two", movie.displayTitle)
        assertEquals("2024 · 2h 46m", movie.displaySubtitle)
        assertEquals(movie.itemId, movie.posterItemId)
        assertEquals(listOf(AiringBadge.Movie), movie.badges())

        val rerun = guide.days.flatMap { it.airings }.first { it.kind == AiringKind.ReRun }
        assertEquals(listOf(AiringBadge.ReRun), rerun.badges())
        assertEquals(listOf(AiringBadge.Recorded, AiringBadge.Watched), onNow.copy(isOnNow = false, isSeasonPremiere = false, isRecorded = true, isWatched = true).badges())
        assertEquals(listOf(AiringBadge.Missed), onNow.copy(isOnNow = false, isSeasonPremiere = false, isMissed = true).badges())
        assertEquals(listOf(AiringBadge.InProgress, AiringBadge.RunsLate), onNow.copy(isOnNow = false, isSeasonPremiere = false, positionTicks = 5, runsOver = true).badges())
        assertEquals(emptyList<AiringBadge>(), onNow.copy(isOnNow = false, isSeasonPremiere = false, positionTicks = 5, runsOver = true).badges(compact = true))
    }

    @Test
    fun `recordings become airings for the player`() {
        val rec = Fixtures.load("recordings.json", ListSerializer(RecordingStatus.serializer())).first()
        val airing = rec.toAiring()
        assertEquals(AiringKind.Episode, airing.kind)
        assertEquals("The Bear", airing.displayTitle)
        assertEquals("S01E02 · The Bear 1x02", airing.displaySubtitle)
        assertEquals(rec.recording.itemId, airing.itemId)
        assertEquals(rec.recording.id, airing.recordingId)
        assertTrue(airing.isRecorded)
        assertEquals(30, airing.runtimeMinutes)
        assertEquals(rec.seriesId, airing.posterItemId)
    }
}
