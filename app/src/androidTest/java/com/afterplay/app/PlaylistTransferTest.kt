package com.afterplay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.Playlist
import com.afterplay.app.data.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaylistTransferTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun movingVideoThroughMenuUpdatesBothListsAndRemovesHero() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AfterPlayApp
        val originalSelection = app.playbackStore.selectedPlaylistId
        val track = Track("jNQXAC9IVRw", "이동 테스트용 영상", durationSeconds = 20)
        val source = Playlist(UUID.randomUUID().toString(), "이동 테스트 원본", listOf(track))
        val target = Playlist(UUID.randomUUID().toString(), "이동 테스트 대상")
        try {
            runBlocking { app.library.update { it + source + target } }
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[PlayerViewModel::class.java].selectPlaylist(source.id)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("화면은 꺼도,\n음악은 계속.").assertDoesNotExist()
            compose.onNodeWithContentDescription("${track.title} 관리").performScrollTo().performClick()
            compose.onNodeWithText("다른 재생목록으로 이동").performClick()
            compose.onAllNodesWithText(target.name).onLast().performClick()
            compose.waitUntil(5_000) {
                runBlocking { app.library.playlists.first().find { it.id == source.id }?.tracks?.isEmpty() == true }
            }
            val saved = runBlocking { app.library.playlists.first() }
            assertEquals(listOf(track), saved.first { it.id == target.id }.tracks)
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[PlayerViewModel::class.java].selectPlaylist(target.id)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(track.title).performScrollTo().assertIsDisplayed()
        } finally {
            runBlocking { app.library.update { it.filterNot { list -> list.id == source.id || list.id == target.id } } }
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[PlayerViewModel::class.java].selectPlaylist(originalSelection)
            }
        }
    }
}
