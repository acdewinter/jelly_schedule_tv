package dev.jellyschedule.tv.data.repo

import android.content.Context
import dev.jellyschedule.tv.BuildConfig
import dev.jellyschedule.tv.data.api.ApiTarget
import dev.jellyschedule.tv.data.session.ClientPreferences
import dev.jellyschedule.tv.data.session.SessionStore
import dev.jellyschedule.tv.data.session.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import java.io.IOException

/** A server the user has pointed the app at but not signed in to yet. */
data class PendingServer(val url: String, val name: String?)

/**
 * Owns the Jellyfin SDK instance, the remembered session and everything about signing in and out.
 * The [api] client is updated in place whenever the server or token changes, so the rest of the app
 * can hold on to it.
 */
class SessionRepository(
    context: Context,
    private val store: SessionStore,
    okHttp: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val initial: StoredSession = runBlocking { store.snapshot() }

    val deviceId: String = initial.deviceId
    val deviceName: String = androidDevice(appContext).name.ifBlank { "Android TV" }

    val clientInfo = ClientInfo(name = CLIENT_NAME, version = BuildConfig.VERSION_NAME)
    private val deviceInfo = DeviceInfo(id = deviceId, name = deviceName)

    val jellyfin: Jellyfin = createJellyfin {
        this.context = appContext
        clientInfo = this@SessionRepository.clientInfo
        deviceInfo = this@SessionRepository.deviceInfo
        apiClientFactory = OkHttpFactory(okHttp)
    }

    val api: ApiClient = jellyfin.createApi(baseUrl = initial.serverUrl, accessToken = initial.accessToken)

    private val _session = MutableStateFlow(initial)
    val session: StateFlow<StoredSession> = _session

    val preferences: StateFlow<ClientPreferences> =
        store.preferences.stateIn(scope, SharingStarted.Eagerly, runBlocking { store.preferencesSnapshot() })

    @Volatile var pendingServer: PendingServer? = null

    init {
        scope.launch {
            store.session.collect { stored ->
                _session.value = stored
                api.update(baseUrl = stored.serverUrl, accessToken = stored.accessToken)
            }
        }
    }

    val current: StoredSession get() = _session.value

    /** The target for plugin calls, or null when not signed in. */
    fun apiTarget(): ApiTarget? {
        val s = _session.value
        val url = s.serverUrl ?: return null
        val token = s.accessToken ?: return null
        return ApiTarget(url, authorizationHeader(token))
    }

    fun authorizationHeader(token: String? = _session.value.accessToken): String {
        val parts = mutableListOf(
            "Client=\"${clientInfo.name}\"",
            "Device=\"${deviceName.replace("\"", "")}\"",
            "DeviceId=\"$deviceId\"",
            "Version=\"${clientInfo.version}\"",
        )
        if (!token.isNullOrBlank()) parts.add(0, "Token=\"$token\"")
        return "MediaBrowser " + parts.joinToString(", ")
    }

    // ---- connecting

    fun discoverLocalServers(): Flow<ServerDiscoveryInfo> = jellyfin.discovery.discoverLocalServers()

    /** Normalises the address the user typed: adds a scheme, strips trailing slashes. */
    fun normalizeServerUrl(input: String): String {
        var url = input.trim().trimEnd('/')
        if (url.isNotEmpty() && !url.contains("://")) url = "http://$url"
        return url
    }

    /** Checks that a Jellyfin server answers at [url] and remembers it as the pending server. */
    suspend fun connect(url: String): PublicSystemInfo {
        val normalized = normalizeServerUrl(url)
        if (normalized.isBlank()) throw IOException("Enter the address of your Jellyfin server.")
        val probe = jellyfin.createApi(baseUrl = normalized)
        val info = probe.systemApi.getPublicSystemInfo().content
        pendingServer = PendingServer(normalized, info.serverName)
        return info
    }

    private suspend fun apiForPending(): ApiClient {
        val pending = pendingServer
        if (pending != null) return jellyfin.createApi(baseUrl = pending.url)
        val url = _session.value.serverUrl ?: throw IOException("No server selected.")
        return jellyfin.createApi(baseUrl = url)
    }

    private suspend fun serverUrlForSignIn(): String =
        pendingServer?.url ?: _session.value.serverUrl ?: throw IOException("No server selected.")

    suspend fun signIn(username: String, password: String) {
        val url = serverUrlForSignIn()
        val client = apiForPending()
        val result = client.userApi.authenticateUserByName(AuthenticateUserByName(username = username, pw = password)).content
        val token = result.accessToken ?: throw IOException("The server did not return a token.")
        val user = result.user ?: throw IOException("The server did not return a user.")
        store.saveServer(url, pendingServer?.name ?: _session.value.serverName)
        store.saveSignIn(token, user.id.toString().replace("-", ""), user.name)
        pendingServer = null
    }

    suspend fun quickConnectEnabled(): Boolean = apiForPending().quickConnectApi.getQuickConnectEnabled().content

    suspend fun startQuickConnect(): QuickConnectResult = apiForPending().quickConnectApi.initiateQuickConnect().content

    suspend fun quickConnectState(secret: String): QuickConnectResult =
        apiForPending().quickConnectApi.getQuickConnectState(secret).content

    suspend fun finishQuickConnect(secret: String) {
        val url = serverUrlForSignIn()
        val client = apiForPending()
        val result = client.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = secret)).content
        val token = result.accessToken ?: throw IOException("The server did not return a token.")
        val user = result.user ?: throw IOException("The server did not return a user.")
        store.saveServer(url, pendingServer?.name ?: _session.value.serverName)
        store.saveSignIn(token, user.id.toString().replace("-", ""), user.name)
        pendingServer = null
    }

    suspend fun signOut() {
        runCatching { api.sessionApi.reportSessionEnded() }
        store.clearToken()
    }

    /** The token was rejected: forget it but keep the server so the user only has to sign in again. */
    suspend fun onUnauthorized() {
        if (_session.value.accessToken != null) store.clearToken()
    }

    suspend fun forgetServer() {
        runCatching { api.sessionApi.reportSessionEnded() }
        store.clearServer()
    }

    // ---- preferences

    suspend fun setAutoplayOnLaunch(value: Boolean) = store.setAutoplayOnLaunch(value)
    suspend fun setSubtitleLanguage(value: String?) = store.setSubtitleLanguage(value)
    suspend fun setAlwaysTranscode(value: Boolean) = store.setAlwaysTranscode(value)

    // ---- images (no auth needed)

    fun imageUrl(itemId: String, type: String = "Primary", maxHeight: Int? = null, maxWidth: Int? = null): String? {
        val base = _session.value.serverUrl?.trimEnd('/') ?: return null
        val query = listOfNotNull(maxHeight?.let { "maxHeight=$it" }, maxWidth?.let { "maxWidth=$it" }).joinToString("&")
        return "$base/Items/$itemId/Images/$type" + if (query.isEmpty()) "" else "?$query"
    }

    companion object {
        const val CLIENT_NAME = "Jelly Schedule TV"
    }
}
