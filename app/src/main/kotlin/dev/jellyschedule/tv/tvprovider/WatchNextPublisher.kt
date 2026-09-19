package dev.jellyschedule.tv.tvprovider

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dev.jellyschedule.tv.data.model.Airing
import dev.jellyschedule.tv.data.model.AiringKind
import dev.jellyschedule.tv.data.repo.SessionRepository
import dev.jellyschedule.tv.domain.displayTitle
import dev.jellyschedule.tv.domain.posterItemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes the programme that is on to the Google TV home screen's Watch Next row while it plays, and
 * removes it again when the channel goes off air. Best effort: devices without the TV provider are ignored.
 */
class WatchNextPublisher(context: Context, private val sessions: SessionRepository) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val supported: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    suspend fun publish(airing: Airing, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        if (!supported) return@withContext
        try {
            val builder = WatchNextProgram.Builder()
                .setType(if (airing.kind == AiringKind.Movie) TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(airing.displayTitle)
                .setDescription(airing.overview ?: "On now on Jelly Schedule")
                .setIntentUri(Uri.parse(INTENT_URI))
                .setInternalProviderId(PROVIDER_ID)
                .setLastPlaybackPositionMillis(positionMs.coerceAtLeast(0).toInt())
                .setDurationMillis(durationMs.coerceAtLeast(0).toInt())
            if (airing.kind != AiringKind.Movie) {
                builder.setEpisodeTitle(airing.title)
                airing.season?.let { builder.setSeasonNumber(it) }
                airing.episode?.let { builder.setEpisodeNumber(it) }
            }
            airing.posterItemId?.let { id -> sessions.imageUrl(id, "Primary", maxHeight = 600)?.let { builder.setPosterArtUri(Uri.parse(it)) } }
            val values = builder.build().toContentValues()
            val existing = findExisting()
            if (existing != null) {
                resolver.update(ContentUris.withAppendedId(TvContractCompat.WatchNextPrograms.CONTENT_URI, existing), values, null, null)
            } else {
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish to Watch Next", e)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (!supported) return@withContext
        try {
            val existing = findExisting() ?: return@withContext
            resolver.delete(ContentUris.withAppendedId(TvContractCompat.WatchNextPrograms.CONTENT_URI, existing), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear Watch Next", e)
        }
    }

    private fun findExisting(): Long? {
        val projection = arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == PROVIDER_ID) return cursor.getLong(0)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "WatchNext"
        const val PROVIDER_ID = "jellyschedule-on-now"
        const val INTENT_URI = "jellyschedule://tunein"
    }
}
