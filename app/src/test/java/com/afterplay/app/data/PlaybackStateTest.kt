package com.afterplay.app.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackStateTest {
    private val a = Track("aaaaaaaaaaa", "A", durationSeconds = 100)
    private val b = Track("bbbbbbbbbbb", "B", durationSeconds = 100)

    @Test fun resumesVideoByIdAfterReordering() {
        assertEquals(ResumePoint(0, 42_000), resumePoint(listOf(b, a), PlaybackBookmark(b.id, 42_000)))
        assertEquals(ResumePoint(1, 42_000), resumePoint(listOf(a, b), PlaybackBookmark(b.id, 42_000)))
    }
    @Test fun explicitDifferentVideoStartsAtBeginningAndSameVideoResumes() {
        val bookmark = PlaybackBookmark(b.id, 42_000)
        assertEquals(ResumePoint(0, 0), resumePoint(listOf(a, b), bookmark, 0))
        assertEquals(ResumePoint(1, 42_000), resumePoint(listOf(a, b), bookmark, 1))
    }
    @Test fun missingVideoAndCompletedVideoHaveSafeStartPositions() {
        assertEquals(ResumePoint(0, 0), resumePoint(listOf(a), PlaybackBookmark(b.id, 42_000)))
        assertEquals(ResumePoint(0, 0), resumePoint(listOf(a), PlaybackBookmark(a.id, 100_000)))
        assertEquals(ResumePoint(0, 0), resumePoint(emptyList(), PlaybackBookmark(a.id, 42_000)))
    }
    @Test fun movesBetweenPlaylistsWithoutDuplicateOrPartialUpdates() {
        val source = Playlist(id = "source", name = "Source", tracks = listOf(a, b))
        val target = Playlist(id = "target", name = "Target", tracks = listOf(b))
        val lists = listOf(source, target)
        val moved = lists.transferTrack(source.id, target.id, a.id)
        assertEquals(listOf(b), moved[0].tracks)
        assertEquals(listOf(b, a), moved[1].tracks)
        val deduplicated = lists.transferTrack(source.id, target.id, b.id)
        assertEquals(listOf(a), deduplicated[0].tracks)
        assertEquals(listOf(b), deduplicated[1].tracks)
        assertEquals(lists, lists.transferTrack(source.id, "missing", a.id))
        assertEquals(lists, lists.transferTrack(source.id, source.id, a.id))
        assertEquals(listOf(a, b), lists[0].tracks)
    }
}
