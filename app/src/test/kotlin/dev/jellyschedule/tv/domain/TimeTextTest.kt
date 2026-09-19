package dev.jellyschedule.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

class TimeTextTest {
    private val now = OffsetDateTime.of(2026, 9, 22, 18, 30, 0, 0, ZoneOffset.ofHours(2))

    @Test
    fun `wall time shows the household clock as sent`() {
        assertEquals("20:05", TimeText.wallTime(OffsetDateTime.of(2026, 9, 22, 20, 5, 0, 0, ZoneOffset.ofHours(2))))
    }

    @Test
    fun `relative dates`() {
        assertEquals("today 20:00", TimeText.relative(now.withHour(20).withMinute(0), now, Locale.ENGLISH))
        assertEquals("tomorrow 20:00", TimeText.relative(now.plusDays(1).withHour(20).withMinute(0), now, Locale.ENGLISH))
        assertEquals("yesterday 20:00", TimeText.relative(now.minusDays(1).withHour(20).withMinute(0), now, Locale.ENGLISH))
        assertEquals("Thursday 20:00", TimeText.relative(now.plusDays(2).withHour(20).withMinute(0), now, Locale.ENGLISH))
        assertEquals("Tue 29 Sep 20:00", TimeText.relative(now.plusDays(7).withHour(20).withMinute(0), now, Locale.ENGLISH))
    }

    @Test
    fun `today is decided in the timestamp's own offset`() {
        // 23:30 in Amsterdam is still today there, even though it is 21:30 UTC.
        val serverNowUtc = OffsetDateTime.of(2026, 9, 22, 21, 30, 0, 0, ZoneOffset.UTC)
        val late = OffsetDateTime.of(2026, 9, 22, 23, 45, 0, 0, ZoneOffset.ofHours(2))
        assertEquals(0L, TimeText.dayDifference(late, serverNowUtc))
        val nextDay = OffsetDateTime.of(2026, 9, 23, 0, 15, 0, 0, ZoneOffset.ofHours(2))
        assertEquals(1L, TimeText.dayDifference(nextDay, serverNowUtc))
    }

    @Test
    fun `countdown formats`() {
        assertEquals("1h 05m", TimeText.countdown(3900))
        assertEquals("12m 05s", TimeText.countdown(725))
        assertEquals("9:59", TimeText.countdown(599))
        assertEquals("0:00", TimeText.countdown(-5))
    }

    @Test
    fun `durations and ticks`() {
        assertEquals("2h", formatMinutes(120))
        assertEquals("2h 46m", formatMinutes(166))
        assertEquals("45m", formatMinutes(45))
        assertEquals("1:02:03", formatSeconds(3723))
        assertEquals("2:05", formatSeconds(125))
        assertEquals("0:10", formatTicks(10 * TuneInRules.TICKS_PER_SECOND))
    }
}
