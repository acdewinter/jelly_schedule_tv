package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.LiveMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TuneInRulesTest {
    private val start = OffsetDateTime.of(2026, 9, 22, 20, 0, 0, 0, ZoneOffset.ofHours(2))
    private fun airing(positionTicks: Long = 0, runtime: Int = 50) = Airing(
        id = "a", start = start, end = start.plusMinutes(60), durationMinutes = 60, runtimeMinutes = runtime,
        itemId = "item", title = "T", positionTicks = positionTicks,
    )
    private val minute = TuneInRules.TICKS_PER_SECOND * 60

    @Test
    fun `strict live joins in progress`() {
        val now = start.toInstant().plus(Duration.ofMinutes(10))
        assertEquals(10 * minute, TuneInRules.startPositionTicks(airing(positionTicks = 5 * minute), LiveMode.Strict, now, live = true))
    }

    @Test
    fun `strict live starts from zero before the start and after the runtime`() {
        assertEquals(0L, TuneInRules.startPositionTicks(airing(), LiveMode.Strict, start.toInstant().minusSeconds(30), live = true))
        assertEquals(0L, TuneInRules.startPositionTicks(airing(), LiveMode.Strict, start.toInstant().plus(Duration.ofMinutes(50)), live = true))
        assertEquals(0L, TuneInRules.startPositionTicks(airing(), LiveMode.Strict, start.toInstant().plus(Duration.ofMinutes(55)), live = true))
    }

    @Test
    fun `strict live with unknown runtime starts from zero`() {
        assertEquals(0L, TuneInRules.startPositionTicks(airing(runtime = 0), LiveMode.Strict, start.toInstant().plusSeconds(600), live = true))
    }

    @Test
    fun `relaxed live resumes from the saved position`() {
        val now = start.toInstant().plus(Duration.ofMinutes(10))
        assertEquals(7 * minute, TuneInRules.startPositionTicks(airing(positionTicks = 7 * minute), LiveMode.Relaxed, now, live = true))
        assertEquals(0L, TuneInRules.startPositionTicks(airing(), LiveMode.Relaxed, now, live = true))
    }

    @Test
    fun `watching from the guide only resumes when asked`() {
        val now = start.toInstant()
        assertEquals(0L, TuneInRules.startPositionTicks(airing(positionTicks = 3 * minute), LiveMode.Relaxed, now, live = false))
        assertEquals(3 * minute, TuneInRules.startPositionTicks(airing(positionTicks = 3 * minute), LiveMode.Relaxed, now, live = false, resume = true))
        assertEquals(3 * minute, TuneInRules.startPositionTicks(airing(positionTicks = 3 * minute), LiveMode.Strict, now, live = false, resume = true))
    }

    @Test
    fun `live progress is clamped`() {
        assertEquals(0f, TuneInRules.liveProgress(airing(), start.toInstant().minusSeconds(5)))
        assertEquals(0.5f, TuneInRules.liveProgress(airing(), start.toInstant().plus(Duration.ofMinutes(25))), 0.001f)
        assertEquals(1f, TuneInRules.liveProgress(airing(), start.toInstant().plus(Duration.ofHours(2))))
        assertEquals(0f, TuneInRules.liveProgress(airing(runtime = 0), start.toInstant()))
    }
}
