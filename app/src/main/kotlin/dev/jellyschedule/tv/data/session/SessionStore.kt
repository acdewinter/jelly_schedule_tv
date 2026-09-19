package dev.jellyschedule.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** What is remembered on this device: the server, the token, who signed in, and the client-side preferences. */
data class StoredSession(
    val deviceId: String,
    val serverUrl: String? = null,
    val serverName: String? = null,
    val accessToken: String? = null,
    val userId: String? = null,
    val userName: String? = null,
) {
    val hasServer: Boolean get() = !serverUrl.isNullOrBlank()
    val isSignedIn: Boolean get() = hasServer && !accessToken.isNullOrBlank()
}

data class ClientPreferences(
    val autoplayOnLaunch: Boolean = true,
    val subtitleLanguage: String? = null,
    val alwaysTranscode: Boolean = false,
)

class SessionStore(context: Context) {
    private val store = context.applicationContext.sessionDataStore

    val session: Flow<StoredSession> = store.data.map { it.toSession() }
    val preferences: Flow<ClientPreferences> = store.data.map { it.toClientPreferences() }

    suspend fun snapshot(): StoredSession {
        val prefs = store.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (existing.isNullOrBlank()) {
            // First launch: mint a stable device id so the server can tell this TV apart.
            val id = UUID.randomUUID().toString().replace("-", "")
            store.edit { it[KEY_DEVICE_ID] = id }
            return prefs.toSession().copy(deviceId = id)
        }
        return prefs.toSession()
    }

    suspend fun preferencesSnapshot(): ClientPreferences = store.data.first().toClientPreferences()

    suspend fun saveServer(url: String, name: String?) {
        store.edit {
            it[KEY_SERVER_URL] = url
            if (name.isNullOrBlank()) it.remove(KEY_SERVER_NAME) else it[KEY_SERVER_NAME] = name
        }
    }

    suspend fun saveSignIn(accessToken: String, userId: String, userName: String?) {
        store.edit {
            it[KEY_TOKEN] = accessToken
            it[KEY_USER_ID] = userId
            if (userName.isNullOrBlank()) it.remove(KEY_USER_NAME) else it[KEY_USER_NAME] = userName
        }
    }

    /** Forget the token but keep the server, so the next launch lands on sign-in rather than connect. */
    suspend fun clearToken() {
        store.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USER_ID)
            it.remove(KEY_USER_NAME)
        }
    }

    suspend fun clearServer() {
        store.edit {
            it.remove(KEY_SERVER_URL)
            it.remove(KEY_SERVER_NAME)
            it.remove(KEY_TOKEN)
            it.remove(KEY_USER_ID)
            it.remove(KEY_USER_NAME)
        }
    }

    suspend fun setAutoplayOnLaunch(value: Boolean) = store.edit { it[KEY_AUTOPLAY] = value }

    suspend fun setSubtitleLanguage(value: String?) = store.edit {
        if (value.isNullOrBlank()) it.remove(KEY_SUBTITLE_LANGUAGE) else it[KEY_SUBTITLE_LANGUAGE] = value
    }

    suspend fun setAlwaysTranscode(value: Boolean) = store.edit { it[KEY_ALWAYS_TRANSCODE] = value }

    private fun Preferences.toSession() = StoredSession(
        deviceId = this[KEY_DEVICE_ID] ?: "",
        serverUrl = this[KEY_SERVER_URL],
        serverName = this[KEY_SERVER_NAME],
        accessToken = this[KEY_TOKEN],
        userId = this[KEY_USER_ID],
        userName = this[KEY_USER_NAME],
    )

    private fun Preferences.toClientPreferences() = ClientPreferences(
        autoplayOnLaunch = this[KEY_AUTOPLAY] ?: true,
        subtitleLanguage = this[KEY_SUBTITLE_LANGUAGE],
        alwaysTranscode = this[KEY_ALWAYS_TRANSCODE] ?: false,
    )

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        val KEY_TOKEN = stringPreferencesKey("access_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_on_launch")
        val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val KEY_ALWAYS_TRANSCODE = booleanPreferencesKey("always_transcode")
    }
}
