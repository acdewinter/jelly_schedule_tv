package dev.jellyschedule.tv.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Plugin timestamps carry the household offset: the wall-clock part is shown as-is and "today" is
 * decided against the server's now expressed in that same offset.
 */
object TimeText {
    fun wallTime(t: OffsetDateTime): String = String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)

    fun dayDifference(t: OffsetDateTime, now: OffsetDateTime): Long =
        ChronoUnit.DAYS.between(now.withOffsetSameInstant(t.offset).toLocalDate(), t.toLocalDate())

    /** "today 20:00", "tomorrow 20:00", "Thursday 20:00" or "Thu 25 Sep 20:00". */
    fun relative(t: OffsetDateTime, now: OffsetDateTime, locale: Locale = Locale.getDefault()): String {
        val hm = wallTime(t)
        return when (val diff = dayDifference(t, now)) {
            0L -> "today $hm"
            1L -> "tomorrow $hm"
            -1L -> "yesterday $hm"
            in 2L..6L -> "${t.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} $hm"
            else -> "${dateLabel(t.toLocalDate(), locale)} $hm".also { if (diff < 0) Unit }
        }
    }

    /** "Thu 25 Sep" */
    fun dateLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, locale)}"

    fun dayShort(day: DayOfWeek, locale: Locale = Locale.getDefault()): String = day.getDisplayName(TextStyle.SHORT, locale)

    /** "1h 23m", "12m 05s" or "0:45" for a countdown. */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "${h}h ${String.format(Locale.ROOT, "%02d", m)}m"
            m >= 10 -> "${m}m ${String.format(Locale.ROOT, "%02d", sec)}s"
            else -> String.format(Locale.ROOT, "%d:%02d", m, sec)
        }
    }
}
