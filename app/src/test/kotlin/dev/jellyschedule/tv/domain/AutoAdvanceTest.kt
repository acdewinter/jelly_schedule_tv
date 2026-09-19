package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.Fixtures
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.NowResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AutoAdvanceTest {
    private val t0 = OffsetDateTime.of(2026, 9, 22, 20, 0, 0, 0, ZoneOffset.ofHours(2))
    private fun airing(id: String, itemId: String, start: OffsetDateTime, onNow: Boolean = false) =
        Airing(id = id, start = start, end = start.plusMinutes(30), itemId = itemId, title = id, isOnNow = onNow, runtimeMinutes = 25, durationMinutes = 30)

    @Test
    fun `plays the programme that is on now when it is a different item`() {
        val current = airing("a", "item-a", t0)
        val next = airing("b", "item-b", t0.plusMinutes(30), onNow = true)
        val now = NowResponse(now = t0.plusMinutes(31), onNow = next, upNext = airing("c", "item-c", t0.plusMinutes(60)))
        assertEquals(AdvanceDecision.PlayNext(next), AutoAdvance.decide(now, current, now.now.toInstant()))
    }

    @Test
    fun `counts down when the same item is still in its slot and the next one is soon`() {
        val current = airing("a", "item-a", t0, onNow = true)
        val next = airing("b", "item-b", t0.plusMinutes(30))
        val now = NowResponse(now = t0.plusMinutes(25), onNow = current, upNext = next)
        assertEquals(AdvanceDecision.Countdown(next), AutoAdvance.decide(now, current, now.now.toInstant()))
    }

    @Test
    fun `counts down when nothing is on and the next programme is within three hours`() {
        val current = airing("a", "item-a", t0)
        val next = airing("b", "item-b", t0.plusHours(2))
        val now = NowResponse(now = t0.plusMinutes(25), upNext = next, nextWindowStart = next.start)
        assertEquals(AdvanceDecision.Countdown(next), AutoAdvance.decide(now, current, now.now.toInstant()))
        // exactly three hours still counts down; a second more is off air
        assertEquals(AdvanceDecision.Countdown(next), AutoAdvance.decide(now, current, next.start.toInstant().minus(Duration.ofHours(3))))
        assertEquals(AdvanceDecision.OffAir(next.start), AutoAdvance.decide(now, current, next.start.toInstant().minus(Duration.ofHours(3)).minusSeconds(1)))
    }

    @Test
    fun `goes off air with the next window when nothing is coming up`() {
        val current = airing("a", "item-a", t0)
        val window = t0.plusDays(2)
        val now = NowResponse(now = t0.plusMinutes(25), nextWindowStart = window)
        assertEquals(AdvanceDecision.OffAir(window), AutoAdvance.decide(now, current, now.now.toInstant()))
        assertEquals(AdvanceDecision.OffAir(null), AutoAdvance.decide(NowResponse(now = t0), current, t0.toInstant()))
    }

    @Test
    fun `starts the first programme when nothing was playing yet`() {
        val next = airing("b", "item-b", t0, onNow = true)
        val now = NowResponse(now = t0, onNow = next)
        assertEquals(AdvanceDecision.PlayNext(next), AutoAdvance.decide(now, null, t0.toInstant()))
    }

    @Test
    fun `real off-air payload counts down or goes off air depending on the time`() {
        val now = Fixtures.load("now-offair.json", NowResponse.serializer())
        val next = now.upNext!!
        val current = airing("x", "item-x", next.start.minusHours(1))
        assertEquals(AdvanceDecision.Countdown(next), AutoAdvance.decide(now, current, next.start.toInstant().minus(Duration.ofMinutes(90))))
        assertEquals(AdvanceDecision.OffAir(now.nextWindowStart), AutoAdvance.decide(now, current, next.start.toInstant().minus(Duration.ofHours(5))))
    }

    @Test
    fun `real on-air payload plays the next item after the current one ended`() {
        val now = Fixtures.load("now.json", NowResponse.serializer())
        val onNow = now.onNow!!
        val upNext = now.upNext!!
        // The programme before it just ended: what is on now is a different item.
        assertEquals(AdvanceDecision.PlayNext(onNow), AutoAdvance.decide(now, upNext.copy(itemId = "previous"), now.now.toInstant()))
        // The current item finished early but is still in its slot: count down to what is next.
        assertEquals(AdvanceDecision.Countdown(upNext), AutoAdvance.decide(now, onNow, now.now.toInstant()))
    }

    @Test
    fun `seconds until never go negative`() {
        val next = airing("b", "item-b", t0)
        assertEquals(0L, AutoAdvance.secondsUntil(next, t0.toInstant().plusSeconds(10)))
        assertEquals(90L, AutoAdvance.secondsUntil(next, t0.toInstant().minusSeconds(90)))
        assertTrue(AutoAdvance.COUNTDOWN_WINDOW == Duration.ofHours(3))
    }
}
