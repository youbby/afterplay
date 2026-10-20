package com.afterplay.app.data

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private val id = "jNQXAC9IVRw"

    @Test fun acceptsYoutubeShareAndVideoFormats() {
        listOf(
            "https://www.youtube.com/watch?v=$id&list=PL123",
            "https://youtu.be/$id?si=abc",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id",
            "https://youtube.com/shorts/$id",
            "https://youtube.com/live/$id",
            "https://www.youtube.com/embed/$id",
            "이 영상을 들어보세요 https://youtu.be/$id",
            "youtu.be/$id", id,
        ).forEach { assertEquals(it, id, YoutubeLink.videoId(it)) }
    }

    @Test fun rejectsOtherHostsMalformedLinksAndPlaylistOnlyLinks() {
        listOf("", "hello", "https://youtube.com.evil.com/watch?v=$id",
            "https://notyoutube.com/watch?v=$id", "https://youtube.com@evil.com/watch?v=$id",
            "https://youtube.com/playlist?list=PL123", "https://youtube.com/watch?v=123",
            "https://example.com/$id", "file://youtube.com/watch?v=$id", "https://youtu.be/%00broken"
        ).forEach { assertNull(it, YoutubeLink.videoId(it)) }
    }

    @Test fun deduplicatesAndReordersWithoutMutatingOriginal() {
        val a = Track("aaaaaaaaaaa", "A")
        val b = Track("bbbbbbbbbbb", "B")
        val c = Track("ccccccccccc", "C")
        val original = Playlist(name = "test", tracks = listOf(a, b, c))
        assertEquals(original, original.add(a))
        assertEquals(listOf(b, a, c), original.move(b.id, -1).tracks)
        assertEquals(listOf(a, c, b), original.move(b.id, 1).tracks)
        assertEquals(original, original.move(a.id, -1))
        assertEquals(original, original.move(c.id, 1))
        assertEquals(original, original.move("missing", 1))
        assertEquals(listOf(a, b, c), original.tracks)
    }
}
