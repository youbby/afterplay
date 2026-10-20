package com.afterplay.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.afterplay.app.data.PlaybackBookmark
import com.google.gson.Gson

const val PLAYLIST_ID = "afterplay.playlist_id"
const val DURATION_MS = "afterplay.duration_ms"

data class SavedMedia(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artwork: String,
    val durationMs: Long = 0,
) {
    fun mediaItem(playlistId: String): MediaItem = MediaItem.Builder()
        .setMediaId(id).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setExtras(Bundle().apply { putString(PLAYLIST_ID, playlistId); putLong(DURATION_MS, durationMs) }).build())
        .build()
}

data class PlaybackCheckpoint(
    val items: List<SavedMedia>,
    val index: Int,
    val positionMs: Long,
    val playlistId: String,
)

/** Shared by the service and UI; playback survives activity and process destruction. */
class PlaybackStore(context: Context) {
    private val preferences = context.getSharedPreferences("playback_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    val shuffle: Boolean get() = preferences.getBoolean("shuffle", false)
    val repeat: Int get() = preferences.getInt("repeat", Player.REPEAT_MODE_OFF).coerceIn(0, 2)
    var selectedPlaylistId: String
        get() = preferences.getString("selected_playlist", "default").orEmpty()
        set(value) { preferences.edit().putString("selected_playlist", value).apply() }

    fun checkpoint(): PlaybackCheckpoint? = runCatching {
        gson.fromJson(preferences.getString("checkpoint", null), PlaybackCheckpoint::class.java)
            ?.takeIf { it.items.isNotEmpty() && it.index in it.items.indices && it.positionMs >= 0 }
    }.getOrNull()

    fun bookmark(playlistId: String): PlaybackBookmark? = runCatching {
        gson.fromJson(preferences.getString("bookmark_$playlistId", null), PlaybackBookmark::class.java)
    }.getOrNull()

    @Synchronized
    fun save(player: Player, durable: Boolean = false) {
        val editor = preferences.edit().putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
        if (player.mediaItemCount > 0) {
            val items = (0 until player.mediaItemCount).mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                val uri = item.localConfiguration?.uri?.toString()
                    ?: item.mediaId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "youtube://$it" }
                    ?: return@mapNotNull null
                val duration = item.mediaMetadata.extras?.getLong(DURATION_MS)?.takeIf { it > 0 }
                    ?: if (index == player.currentMediaItemIndex) player.duration.coerceAtLeast(0) else 0
                SavedMedia(item.mediaId, uri, item.mediaMetadata.title?.toString().orEmpty(),
                    item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString().orEmpty(), duration)
            }
            if (items.size == player.mediaItemCount) {
                val ended = player.playbackState == Player.STATE_ENDED
                val index = if (ended) 0 else player.currentMediaItemIndex.coerceIn(items.indices)
                val position = if (ended) 0 else player.currentPosition.coerceAtLeast(0)
                val playlistId = player.getMediaItemAt(index).mediaMetadata.extras?.getString(PLAYLIST_ID).orEmpty()
                val checkpoint = PlaybackCheckpoint(items, index, position, playlistId)
                editor.putString("checkpoint", gson.toJson(checkpoint))
                if (playlistId.isNotBlank()) editor.putString("bookmark_$playlistId",
                    gson.toJson(PlaybackBookmark(items[index].id, position)))
            }
        }
        if (durable) editor.commit() else editor.apply()
    }
}
