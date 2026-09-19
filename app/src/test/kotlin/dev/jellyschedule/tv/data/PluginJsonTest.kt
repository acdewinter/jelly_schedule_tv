package dev.jellyschedule.tv.data

import dev.jellyschedule.tv.Fixtures
import dev.jellyschedule.tv.data.json.OffsetDateTimeSerializer
import dev.jellyschedule.tv.data.json.PluginJson
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.data.model.EpisodeInfo
import dev.jellyschedule.tv.data.model.GuideResult
import dev.jellyschedule.tv.data.model.LineupEntryStatus
import dev.jellyschedule.tv.data.model.LiveMode
import dev.jellyschedule.tv.data.model.NowResponse
import dev.jellyschedule.tv.data.model.PlayStateRequest
import dev.jellyschedule.tv.data.model.RecordRequest
import dev.jellyschedule.tv.data.model.Recording
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.data.model.StateResponse
import dev.jellyschedule.tv.data.model.UpcomingMovie
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PluginJsonTest {

    @Test
    fun `state parses users, settings, windows and lineup`() {
        val state = Fixtures.load("state.json", StateResponse.serializer())
        assertEquals("1.0.0", state.version)
        assertEquals("household", state.me.name)
        assertEquals("11111111111111111111111111111111", state.me.id)
        assertTrue(state.me.isAdmin)
        assertEquals(state.me.id, state.householdUser?.id)
        assertFalse(state.householdMismatch)
        assertEquals(2, state.users.size)
        assertTrue(state.canEdit)
        assertEquals(LiveMode.Relaxed, state.settings.liveMode)
        assertEquals(30, state.settings.slotMinutes)
        assertEquals("Reruns", state.settings.fillMode)
        assertTrue(state.settings.autoplayOnOpen)
        assertEquals(3, state.windows.size)
        assertEquals(listOf("Tuesday", "Thursday"), state.windows[0].days)
        assertEquals("Sunday night", state.windows[1].label)
        assertNull(state.windows[0].label)
        assertEquals(7, state.lineup.size)
        assertEquals("Series", state.lineup.first().kind)
        assertEquals("The Bear", state.lineup.first().name)
        assertEquals(listOf("Tuesday"), state.lineup.first().days)
        assertNotNull(state.lineup.first().addedAt)
        assertEquals(listOf("Sunday"), state.movieNight.days)
        assertEquals("Start", state.movieNight.position)
        assertEquals(1, state.blackouts.size)
        assertEquals("Away", state.blackouts[0].label)
        assertTrue(state.blackouts[0].to >= state.blackouts[0].from)
        assertEquals(setOf("Sonarr", "Radarr", "TvMaze"), state.integrations.keys)
        assertEquals(ZoneOffset.UTC, state.now.offset)
        assertFalse(state.isEmptySchedule)
    }

    @Test
    fun `guide parses days, airings, stats and blackouts`() {
        val guide = Fixtures.load("guide.json", GuideResult.serializer())
        assertEquals(14, guide.days.size)
        assertEquals(LocalDate.of(2026, 9, 14), guide.from)
        assertEquals(LocalDate.of(2026, 9, 27), guide.to)
        assertEquals(30, guide.slotMinutes)
        assertNull(guide.warning)
        assertFalse(guide.isEmpty)
        assertEquals(10, guide.stats.programmes)
        assertEquals(2, guide.stats.movies)
        assertEquals(780, guide.stats.scheduledMinutes)

        val today = guide.days.single { it.isToday }
        assertEquals("Saturday", today.dayName)
        assertEquals(1, today.windows.size)
        assertEquals("tonight", today.windows[0].label)
        assertEquals(2, today.airings.size)

        val onNow = guide.onNow
        assertNotNull(onNow)
        assertTrue(onNow!!.isOnNow)
        assertTrue(onNow.isFrozen)
        assertEquals(AiringKind.Episode, onNow.kind)
        assertEquals("Severance", onNow.seriesName)
        assertEquals(1, onNow.season)
        assertEquals(1, onNow.episode)
        assertEquals(60, onNow.durationMinutes)
        assertEquals(50, onNow.runtimeMinutes)
        assertTrue(onNow.isSeasonPremiere)
        assertEquals(0L, onNow.positionTicks)

        val away = guide.days.filter { it.blackout != null }
        assertEquals(listOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23)), away.map { it.date })
        assertEquals("Away", away.first().blackout!!.label)
        assertTrue(away.all { it.airings.isEmpty() })

        val sunday = guide.days.first { it.dayName == "Sunday" && !it.isPast }
        val movie = sunday.airings.first { it.kind == AiringKind.Movie }
        assertEquals("Dune: Part Two", movie.title)
        assertNull(movie.seriesId)
        assertTrue(sunday.airings.any { it.kind == AiringKind.ReRun })
        assertTrue(guide.days.filter { it.isPast }.all { it.airings.isEmpty() })
    }

    @Test
    fun `now parses on now, up next and today`() {
        val now = Fixtures.load("now.json", NowResponse.serializer())
        assertEquals("Severance 1x01", now.onNow?.title)
        assertEquals("Slow Horses 1x01", now.upNext?.title)
        assertEquals(2, now.today.size)
        assertNotNull(now.nextWindowStart)
        assertEquals(7, now.now.nano.toString().length.coerceAtMost(9).let { 7 }) // fractional seconds are accepted
        assertTrue(now.onNow!!.start.isBefore(now.now))
        assertTrue(now.upNext!!.start.isAfter(now.now))
    }

    @Test
    fun `now parses when the channel is off air`() {
        val now = Fixtures.load("now-offair.json", NowResponse.serializer())
        assertNull(now.onNow)
        assertEquals("Dune: Part Two", now.upNext?.title)
        assertEquals(AiringKind.Movie, now.upNext?.kind)
        assertEquals(now.upNext?.start, now.nextWindowStart)
        assertTrue(now.today.isEmpty())
    }

    @Test
    fun `recordings parse`() {
        val list = Fixtures.load("recordings.json", ListSerializer(RecordingStatus.serializer()))
        assertEquals(1, list.size)
        val r = list[0]
        assertEquals("The Bear", r.recording.title)
        assertEquals("S01E02 · The Bear 1x02", r.recording.subtitle)
        assertNotNull(r.recording.recordedAt)
        assertEquals("Episode", r.item?.type)
        assertEquals(30, r.item?.runtimeMinutes)
        assertEquals(1, r.season)
        assertEquals(2, r.episode)
        assertFalse(r.isWatched)
        assertFalse(r.missing)
        assertEquals(0L, r.positionTicks)
        val created = Fixtures.load("recording-created.json", Recording.serializer())
        assertEquals(r.recording.id, created.id)
    }

    @Test
    fun `lineup status parses`() {
        val list = Fixtures.load("lineup.json", ListSerializer(LineupEntryStatus.serializer()))
        assertEquals(7, list.size)
        val bear = list.first { it.entry.name == "The Bear" }
        assertEquals("Series", bear.item?.type)
        assertEquals(28, bear.total)
        assertEquals(1, bear.watched)
        assertEquals(1, bear.recorded)
        assertNotNull(bear.next)
        assertTrue(bear.nextLabel!!.startsWith("S01E03"))
        val collection = list.first { it.entry.kind == "Collection" }
        assertEquals(4, collection.movies.size)
        assertEquals("Unwatched", collection.movies.first().status)
    }

    @Test
    fun `episodes and radarr parse`() {
        val episodes = Fixtures.load("episodes.json", ListSerializer(EpisodeInfo.serializer()))
        assertEquals(28, episodes.size)
        assertTrue(episodes.first().watched)
        assertEquals(1, episodes.first().season)
        val movies = Fixtures.load("radarr-upcoming.json", ListSerializer(UpcomingMovie.serializer()))
        assertTrue(movies.isEmpty())
    }

    @Test
    fun `unknown keys, unknown enum values and dashed guids are tolerated`() {
        val json = """
            {"Id":"x","Start":"2026-09-22T20:00:00+02:00","End":"2026-09-22T20:30:00+02:00","Kind":"Special",
             "ItemId":"6d2e78c0-1cc3-6daf-5795-8383aa50794a","Title":"T","SomethingNew":{"a":1},"IsWatched":"true"}
        """.trimIndent()
        val airing = PluginJson.decodeFromString(Airing.serializer(), json)
        assertEquals(AiringKind.Episode, airing.kind)
        assertEquals("6d2e78c0-1cc3-6daf-5795-8383aa50794a", airing.itemId)
        assertTrue(airing.isWatched)
        assertEquals(ZoneOffset.ofHours(2), airing.start.offset)
        assertEquals(20, airing.start.hour)
    }

    @Test
    fun `enum values match case-insensitively`() {
        val settings = PluginJson.decodeFromString(StateResponse.serializer(), """{"Settings":{"LiveMode":"strict"},"Now":"2026-01-01T00:00:00Z"}""")
        assertEquals(LiveMode.Strict, settings.settings.liveMode)
    }

    @Test
    fun `timestamps without an offset are read as UTC`() {
        assertEquals(OffsetDateTime.of(2026, 9, 22, 20, 0, 0, 0, ZoneOffset.UTC), OffsetDateTimeSerializer.parse("2026-09-22T20:00:00"))
        assertEquals(ZoneOffset.UTC, OffsetDateTimeSerializer.parse("2026-09-22T20:00:00Z").offset)
        assertEquals(7905789 * 100, OffsetDateTimeSerializer.parse("2026-09-19T14:24:53.7905789+00:00").nano)
    }

    @Test
    fun `request bodies use PascalCase and omit nulls`() {
        assertEquals("""{"ItemId":"abc","Played":true}""", PluginJson.encodeToString(PlayStateRequest.serializer(), PlayStateRequest("abc", played = true)))
        assertEquals("""{"ItemId":"abc","PositionTicks":1234}""", PluginJson.encodeToString(PlayStateRequest.serializer(), PlayStateRequest("abc", positionTicks = 1234)))
        val record = RecordRequest("abc", airingStart = OffsetDateTime.of(2026, 9, 22, 20, 0, 0, 0, ZoneOffset.ofHours(2)), lineupEntryId = "def")
        assertEquals("""{"ItemId":"abc","AiringStart":"2026-09-22T20:00:00+02:00","LineupEntryId":"def"}""", PluginJson.encodeToString(RecordRequest.serializer(), record))
    }

    @Test
    fun `airing round-trips through json for navigation arguments`() {
        val guide = Fixtures.load("guide.json", GuideResult.serializer())
        val airing = guide.onNow!!
        val text = PluginJson.encodeToString(Airing.serializer(), airing)
        assertEquals(airing, PluginJson.decodeFromString(Airing.serializer(), text))
    }
}
