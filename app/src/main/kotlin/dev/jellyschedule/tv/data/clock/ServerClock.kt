package dev.jellyschedule.tv.data.clock

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The device clock may be wrong; the server's `Now` is authoritative. Every plugin response that carries
 * `Now` updates the offset, and "now" logic in the app reads [now] instead of the system clock.
 * The household zone offset from the last `Now` is kept for turning instants back into wall-clock time.
 */
class ServerClock {
    @Volatile private var offsetMillis: Long = 0

    @Volatile var householdOffset: ZoneOffset = ZoneOffset.UTC
        private set

    @Volatile var synced: Boolean = false
        private set

    fun sync(serverNow: OffsetDateTime) {
        offsetMillis = serverNow.toInstant().toEpochMilli() - System.currentTimeMillis()
        householdOffset = serverNow.offset
        synced = true
    }

    fun now(): Instant = Instant.ofEpochMilli(System.currentTimeMillis() + offsetMillis)

    fun nowInHouseholdZone(): OffsetDateTime = now().atOffset(householdOffset)
}
