package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.NowResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** What the player does once a live programme has ended (API.md, "After a programme ends"). */
sealed interface AdvanceDecision {
    /** Something else is on right now: play it. */
    data class PlayNext(val airing: Airing) : AdvanceDecision

    /** The next programme starts soon: count down to `upNext.start`, then play it. */
    data class Countdown(val upNext: Airing) : AdvanceDecision

    /** The evening is over. */
    data class OffAir(val nextWindowStart: OffsetDateTime?) : AdvanceDecision
}

object AutoAdvance {
    val COUNTDOWN_WINDOW: Duration = Duration.ofHours(3)

    fun decide(
        now: NowResponse,
        current: Airing?,
        serverNow: Instant,
        countdownWindow: Duration = COUNTDOWN_WINDOW,
    ): AdvanceDecision {
        val onNow = now.onNow
        if (onNow != null && (current == null || onNow.itemId != current.itemId)) return AdvanceDecision.PlayNext(onNow)
        val next = now.upNext
        if (next != null && next.itemId != current?.itemId) {
            val untilStart = Duration.between(serverNow, next.start.toInstant())
            if (untilStart <= countdownWindow) return AdvanceDecision.Countdown(next)
        }
        return AdvanceDecision.OffAir(now.nextWindowStart)
    }

    /** Seconds left until [airing] starts, never negative. */
    fun secondsUntil(airing: Airing, serverNow: Instant): Long =
        Duration.between(serverNow, airing.start.toInstant()).seconds.coerceAtLeast(0)
}
