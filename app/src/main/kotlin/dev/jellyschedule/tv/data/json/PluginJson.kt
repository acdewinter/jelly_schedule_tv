package dev.jellyschedule.tv.data.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The plugin speaks Jellyfin's JSON dialect: PascalCase names, enums as strings, GUIDs as 32 hex
 * characters and omitted nulls. Our Kotlin models use camelCase, so a naming strategy maps between
 * the two; everything else is handled by a lenient, case-insensitive configuration.
 */
object PascalCaseNamingStrategy : JsonNamingStrategy {
    override fun serialNameForJson(descriptor: SerialDescriptor, elementIndex: Int, serialName: String): String =
        if (serialName.isEmpty()) serialName else serialName[0].uppercaseChar() + serialName.substring(1)

    override fun toString(): String = "PascalCaseNamingStrategy"
}

val PluginJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    decodeEnumsCaseInsensitive = true
    encodeDefaults = false
    namingStrategy = PascalCaseNamingStrategy
}

/** ISO 8601 timestamps with an explicit offset (`2026-09-22T20:00:00+02:00`). Fraction digits are optional. */
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime = parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    fun parse(text: String): OffsetDateTime {
        val trimmed = text.trim()
        return try {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: java.time.format.DateTimeParseException) {
            // A timestamp without an offset is treated as UTC rather than failing the whole payload.
            LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atOffset(ZoneOffset.UTC)
        }
    }
}

/** Dates are `yyyy-MM-dd`; a full timestamp is tolerated by taking its date part. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        val text = decoder.decodeString().trim()
        return LocalDate.parse(if (text.length > 10) text.substring(0, 10) else text)
    }

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
}
