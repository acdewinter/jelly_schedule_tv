package dev.jellyschedule.tv.domain

import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.LiveMode
import java.time.Duration
import java.time.Instant

/** Where playback starts when you tune in (API.md, "Tune-in position"). Positions are in 100 ns ticks. */
object TuneInRules {
    const val TICKS_PER_MILLISECOND = 10_000L
    const val TICKS_PER_SECOND = 10_000_000L

    /**
     * @param live true when joining the channel (as opposed to watching a programme from the guide or a recording)
     * @param resume true when the user explicitly asked to resume
     */
    fun startPositionTicks(airing: Airing, liveMode: LiveMode, serverNow: Instant, live: Boolean, resume: Boolean = false): Long {
        if (live && liveMode == LiveMode.Strict) {
            val offsetMs = Duration.between(airing.start.toInstant(), serverNow).toMillis()
            val runtimeMs = airing.runtimeMinutes * 60_000L
            return if (offsetMs > 0 && offsetMs < runtimeMs) offsetMs * TICKS_PER_MILLISECOND else 0L
        }
        if (airing.positionTicks > 0 && (resume || live)) return airing.positionTicks
        return 0L
    }

    /** Fraction of the programme that has elapsed on the channel, for the "on now" progress bar. */
    fun liveProgress(airing: Airing, serverNow: Instant): Float {
        val runtimeMs = airing.runtimeMinutes * 60_000L
        if (runtimeMs <= 0) return 0f
        val elapsed = Duration.between(airing.start.toInstant(), serverNow).toMillis()
        return (elapsed.toFloat() / runtimeMs).coerceIn(0f, 1f)
    }
}
