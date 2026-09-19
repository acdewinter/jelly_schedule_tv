package dev.jellyschedule.tv.data.api

import dev.jellyschedule.tv.data.json.PluginJson
import dev.jellyschedule.tv.data.model.ApiErrorBody
import dev.jellyschedule.tv.data.model.EpisodeInfo
import dev.jellyschedule.tv.data.model.GuideResult
import dev.jellyschedule.tv.data.model.LineupEntryStatus
import dev.jellyschedule.tv.data.model.NowResponse
import dev.jellyschedule.tv.data.model.PlayStateRequest
import dev.jellyschedule.tv.data.model.RecordRequest
import dev.jellyschedule.tv.data.model.Recording
import dev.jellyschedule.tv.data.model.RecordingStatus
import dev.jellyschedule.tv.data.model.StateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate

/** The server rejected the call. 401 means the token is gone; 403 means the caller may not edit. */
class ApiException(val status: Int, message: String) : IOException(message) {
    val isUnauthorized: Boolean get() = status == 401
    val isForbidden: Boolean get() = status == 403
}

/** No server or token is configured; the caller should go to the connect screen. */
class NotSignedInException : IOException("Not signed in")

/** Where and how to reach the plugin: the Jellyfin base URL plus a ready-made Authorization header. */
data class ApiTarget(val baseUrl: String, val authorization: String)

/**
 * Client for the plugin endpoints under `/JellySchedule/`. Uses OkHttp with the same token and header
 * format as the Jellyfin SDK. The target is looked up per call so a sign-in or sign-out takes effect at once.
 */
class JellyScheduleApi(
    private val http: OkHttpClient,
    private val target: () -> ApiTarget?,
    private val json: Json = PluginJson,
) {
    suspend fun state(): StateResponse = get("state")

    suspend fun now(): NowResponse = get("now")

    suspend fun guide(from: LocalDate, days: Int = 7): GuideResult = get("guide?from=$from&days=$days")

    suspend fun recordings(): List<RecordingStatus> = get("recordings")

    suspend fun lineup(refresh: Boolean = false): List<LineupEntryStatus> = get("lineup?refresh=$refresh")

    suspend fun episodes(seriesId: String): List<EpisodeInfo> = get("series/$seriesId/episodes")

    suspend fun record(request: RecordRequest): Recording = post("recordings", request)

    suspend fun cancelRecording(recordingId: String) = delete("recordings/$recordingId")

    suspend fun playState(request: PlayStateRequest) = postNoContent("playstate", request)

    // ---- plumbing

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(path)).get().build()
        json.decodeFromString<T>(execute(request))
    }

    private suspend inline fun <reified B, reified T> post(path: String, body: B): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(path)).post(json.encodeToString(body).toRequestBody(JSON_TYPE)).build()
        json.decodeFromString<T>(execute(request))
    }

    private suspend inline fun <reified B> postNoContent(path: String, body: B) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(path)).post(json.encodeToString(body).toRequestBody(JSON_TYPE)).build()
        execute(request)
        Unit
    }

    private suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(path)).delete().build()
        execute(request)
        Unit
    }

    private fun url(path: String): HttpUrl {
        val t = target() ?: throw NotSignedInException()
        val base = t.baseUrl.trimEnd('/')
        return "$base/JellySchedule/$path".toHttpUrlOrNull() ?: throw IOException("Invalid server address: ${t.baseUrl}")
    }

    private fun execute(request: Request): String {
        val t = target() ?: throw NotSignedInException()
        val authed = request.newBuilder()
            .header("Authorization", t.authorization)
            .header("Accept", "application/json")
            .build()
        http.newCall(authed).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching { json.decodeFromString<ApiErrorBody>(text).message }.getOrNull()
                throw ApiException(response.code, message ?: defaultMessage(response.code))
            }
            return text
        }
    }

    private fun defaultMessage(code: Int): String = when (code) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "You are not allowed to change the schedule."
        404 -> "Jelly Schedule is not installed on this server."
        else -> "The server answered with HTTP $code."
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
