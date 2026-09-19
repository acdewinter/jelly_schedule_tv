package dev.jellyschedule.tv.di

import android.content.Context
import dev.jellyschedule.tv.data.api.JellyScheduleApi
import dev.jellyschedule.tv.data.clock.ServerClock
import dev.jellyschedule.tv.data.repo.PlaybackRepository
import dev.jellyschedule.tv.data.repo.ScheduleRepository
import dev.jellyschedule.tv.data.repo.SessionRepository
import dev.jellyschedule.tv.data.session.SessionStore
import dev.jellyschedule.tv.player.DeviceProfileBuilder
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency graph; the app is small enough not to need a DI framework. */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val sessionStore = SessionStore(appContext)
    val sessions = SessionRepository(appContext, sessionStore, okHttp)
    val clock = ServerClock()
    val pluginApi = JellyScheduleApi(okHttp, target = { sessions.apiTarget() })
    val schedule = ScheduleRepository(pluginApi, sessions, clock)
    val deviceProfiles = DeviceProfileBuilder(appContext)
    val playback = PlaybackRepository(sessions, deviceProfiles)
}
