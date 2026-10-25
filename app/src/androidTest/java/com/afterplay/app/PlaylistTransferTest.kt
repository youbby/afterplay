package com.afterplay.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.Playlist
import com.afterplay.app.data.Track
import com.afterplay.app.playback.PLAYLIST_ID
import com.afterplay.app.playback.PlaybackService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaylistTransferTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun browsingAnotherPlaylistKeepsTheCurrentQueueMarked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as AfterPlayApp
        val originalSelection = app.playbackStore.selectedPlaylistId
        val source = Playlist(UUID.randomUUID().toString(), "집중 음악")
        val target = Playlist(UUID.randomUUID().toString(), "휴식 음악")
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { app.focusGuard.unlockAfterAuthentication(); app.pomodoro.reset() }
            runBlocking { app.library.update { it + source + target } }
            compose.activityRule.scenario.onActivity {
                app.appMode.select(com.afterplay.app.data.AppMode.PLAYBACK)
                ViewModelProvider(it)[PlayerViewModel::class.java].selectPlaylist(source.id)
            }
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("playlist-indicator-test")
                    .setUri("file:///unused-test.wav")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("재생 목록 표시 테스트")
                        .setExtras(Bundle().apply { putString(PLAYLIST_ID, source.id) }).build()).build())
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("playlist_${source.id}").fetchSemanticsNodes().any {
                    it.config.contains(SemanticsProperties.StateDescription) && it.config[SemanticsProperties.StateDescription] == "현재 재생 목록"
                }
            }
            compose.onNodeWithTag("playlist_${target.id}").performScrollTo().performClick()
            compose.onNodeWithTag("playlist_${target.id}").assertIsSelected()
            compose.onNodeWithTag("playlist_${source.id}").assertIsNotSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "현재 재생 목록"))
            compose.onNodeWithText("목록추가").assertIsDisplayed()
            compose.onNodeWithContentDescription("재생 종료").assertDoesNotExist()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(context.getExternalFilesDir(null), "portrait-player-layout.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } } finally { bitmap.recycle() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("playlist_${source.id}").fetchSemanticsNodes().any {
                    it.config.contains(SemanticsProperties.StateDescription) && it.config[SemanticsProperties.StateDescription] == "현재 재생 목록"
                }
            }
            compose.onNodeWithTag("playlist_${source.id}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "현재 재생 목록"))
        } finally {
            instrumentation.runOnMainSync {
                controller.stop(); controller.clearMediaItems(); MediaController.releaseFuture(future)
            }
            runBlocking { app.library.update { it.filterNot { list -> list.id in setOf(source.id, target.id) } } }
            compose.activityRule.scenario.onActivity {
                ViewModelProvider(it)[PlayerViewModel::class.java].selectPlaylist(originalSelection)
            }
        }
    }

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
