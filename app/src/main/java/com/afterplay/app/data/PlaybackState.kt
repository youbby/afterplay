package com.afterplay.app.data

data class PlaybackBookmark(val trackId: String, val positionMs: Long)
data class ResumePoint(val index: Int, val positionMs: Long)

/** Match by video ID so changing the playlist order does not change the resumed song. */
fun resumePoint(tracks: List<Track>, bookmark: PlaybackBookmark?, explicitIndex: Int? = null): ResumePoint {
    if (tracks.isEmpty()) return ResumePoint(0, 0)
    val bookmarkedIndex = tracks.indexOfFirst { it.id == bookmark?.trackId }
    val index = explicitIndex?.coerceIn(tracks.indices) ?: bookmarkedIndex.takeIf { it >= 0 } ?: 0
    val position = if (index == bookmarkedIndex) bookmark?.positionMs?.coerceAtLeast(0) ?: 0 else 0
    val duration = tracks[index].durationSeconds * 1000
    return ResumePoint(index, if (duration > 0 && position >= duration) 0 else position)
}

/** One atomic move: the source is removed and the target keeps at most one copy. */
fun List<Playlist>.transferTrack(sourceId: String, targetId: String, trackId: String): List<Playlist> {
    if (sourceId == targetId) return this
    val source = find { it.id == sourceId } ?: return this
    if (none { it.id == targetId }) return this
    val track = source.tracks.find { it.id == trackId } ?: return this
    return map {
        when (it.id) {
            sourceId -> it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId })
            targetId -> it.add(track)
            else -> it
        }
    }
}
