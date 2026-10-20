package com.afterplay.app.data

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class Track(
    val id: String,
    val title: String,
    val artist: String = "YouTube",
    val durationSeconds: Long = 0,
    val artwork: String = "",
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tracks: List<Track> = emptyList(),
)

object YoutubeLink {
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val link = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)

    fun videoId(input: String): String? {
        val raw = input.trim()
        if (videoId.matches(raw)) return raw
        val candidate = link.find(raw)?.value ?: if (
            raw.startsWith("youtube.com/") || raw.startsWith("www.youtube.com/") ||
            raw.startsWith("youtu.be/") || raw.startsWith("music.youtube.com/")
        ) "https://$raw" else return null
        return runCatching {
            val uri = URI(candidate)
            val host = uri.host?.lowercase() ?: return null
            val parts = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
            val id = when (host) {
                "youtu.be", "www.youtu.be" -> parts.firstOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> {
                    if (parts.firstOrNull() in setOf("shorts", "live", "embed", "v")) {
                        parts.getOrNull(1)
                    } else if (uri.path == "/watch") {
                        uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
                            .firstOrNull { it.first() == "v" }?.getOrNull(1)
                            ?.let { URLDecoder.decode(it, "UTF-8") }
                    } else null
                }
                else -> null
            }
            id?.takeIf(videoId::matches)
        }.getOrNull()
    }
}

fun Playlist.add(track: Track): Playlist =
    if (tracks.any { it.id == track.id }) this else copy(tracks = tracks + track)

fun Playlist.move(trackId: String, offset: Int): Playlist {
    val from = tracks.indexOfFirst { it.id == trackId }
    val to = from + offset
    if (from !in tracks.indices || to !in tracks.indices) return this
    return copy(tracks = tracks.toMutableList().apply { add(to, removeAt(from)) })
}
