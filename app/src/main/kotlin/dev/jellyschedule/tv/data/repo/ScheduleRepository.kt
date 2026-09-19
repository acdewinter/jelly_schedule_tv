package dev.jellyschedule.tv.data.repo

import dev.jellyschedule.tv.data.api.ApiException
import dev.jellyschedule.tv.data.api.JellyScheduleApi
import dev.jellyschedule.tv.data.clock.ServerClock
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.GuideResult
import dev.jellyschedule.tv.data.model.NowResponse
import dev.jellyschedule.tv.data.model.PlayStateRequest
import dev.jellyschedule.tv.data.model.RecordRequest
import dev.jellyschedule.tv.data.model.Recording
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.data.model.StateResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Everything the app reads from and writes to the plugin. Keeps the last `state` so screens can read
 * the household settings without another round-trip, and feeds every `Now` into the [ServerClock].
 * A 401 anywhere clears the token through the [SessionRepository].
 */
class ScheduleRepository(
    private val api: JellyScheduleApi,
    private val sessions: SessionRepository,
    val clock: ServerClock,
) {
    private val _state = MutableStateFlow<StateResponse?>(null)
    val state: StateFlow<StateResponse?> = _state

    val cachedState: StateResponse? get() = _state.value

    suspend fun refreshState(): StateResponse = guard {
        api.state().also {
            clock.sync(it.now)
            _state.value = it
        }
    }

    suspend fun now(): NowResponse = guard { api.now().also { clock.sync(it.now) } }

    suspend fun guide(from: LocalDate, days: Int = 7): GuideResult = guard { api.guide(from, days).also { clock.sync(it.now) } }

    suspend fun recordings(): List<RecordingStatus> = guard { api.recordings() }

    suspend fun record(airing: Airing): Recording = guard {
        api.record(RecordRequest(itemId = airing.itemId, airingStart = airing.start, lineupEntryId = airing.lineupEntryId))
    }

    suspend fun cancelRecording(recordingId: String) = guard { api.cancelRecording(recordingId) }

    suspend fun setPlayed(itemId: String, played: Boolean) = guard {
        api.playState(PlayStateRequest(itemId = itemId, played = played, positionTicks = if (played) null else 0))
    }

    /** Mirror the household user's position (or completion) when somebody else is signed in. */
    suspend fun mirrorPlayState(itemId: String, positionTicks: Long, played: Boolean) = guard {
        api.playState(PlayStateRequest(itemId = itemId, positionTicks = if (played) 0 else positionTicks, played = if (played) true else null))
    }

    /** Finds a fresh copy of an airing (after a record / watched action) by asking the guide for its day. */
    suspend fun findAiring(airing: Airing): Airing? {
        val day = airing.start.toLocalDate()
        val guide = guide(day, 1)
        return guide.days.firstOrNull()?.airings?.firstOrNull { it.id == airing.id }
            ?: guide.days.flatMap { it.airings }.firstOrNull { it.itemId == airing.itemId }
    }

    private suspend inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.isUnauthorized) sessions.onUnauthorized()
        throw e
    }
}
