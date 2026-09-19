package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.GuideDay
import dev.jellyschedule.tv.data.model.GuideResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Week / day grouping for the guide: Mon-Sun weeks, "this week" and "next week". */
object GuideWeeks {
    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekStarts(today: LocalDate): List<LocalDate> = listOf(mondayOf(today), mondayOf(today).plusWeeks(1))

    sealed interface DayStatus {
        data class Away(val label: String) : DayStatus
        data class OffAir(val hasWindows: Boolean) : DayStatus
        data class Scheduled(val programmes: Int) : DayStatus
    }

    fun status(day: GuideDay): DayStatus = when {
        day.blackout != null -> DayStatus.Away(day.blackout.label)
        day.airings.isEmpty() -> DayStatus.OffAir(day.windows.isNotEmpty())
        else -> DayStatus.Scheduled(day.airings.size)
    }

    /** Today when it is in the range, else the first day that is not in the past, else the first day. */
    fun defaultSelectedDay(days: List<GuideDay>, today: LocalDate): LocalDate? =
        days.firstOrNull { it.date == today }?.date
            ?: days.firstOrNull { !it.isPast }?.date
            ?: days.firstOrNull()?.date

    /** Up next plus the following programmes of the range, in order. */
    fun upcoming(guide: GuideResult, serverNow: Instant, limit: Int = 5): List<Airing> {
        val future = guide.days.flatMap { it.airings }.filter { it.start.toInstant().isAfter(serverNow) }
        val next = guide.upNext
        val head = if (next != null && future.none { it.id == next.id }) listOf(next) else emptyList()
        return (head + future).take(limit)
    }

    /** The programme to land focus on when a day opens: what is on, else the first one still to come. */
    fun focusIndex(day: GuideDay, serverNow: Instant): Int {
        val onNow = day.airings.indexOfFirst { it.isOnNow }
        if (onNow >= 0) return onNow
        val next = day.airings.indexOfFirst { it.end.toInstant().isAfter(serverNow) }
        return if (next >= 0) next else 0
    }
}
