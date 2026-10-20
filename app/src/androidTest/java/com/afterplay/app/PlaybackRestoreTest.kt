package com.afterplay.app

import android.content.ComponentName
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.Track
import com.afterplay.app.playback.PlaybackService
import com.afterplay.app.playback.PlaybackStore
import com.afterplay.app.playback.toMediaItem
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Run write, force-stop the app with adb, then run verify to test a real process restart. */
@RunWith(AndroidJUnit4::class)
class PlaybackRestoreTest {
    @Test fun stopAndProcessRestartKeepPositionAndSettings() {
        val stage = InstrumentationRegistry.getArguments().getString("restoreStage")
        assumeTrue(stage == "write" || stage == "verify")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AfterPlayApp
        val tracks = listOf(Track("M7lc1UVf-VE", "Restore first", durationSeconds = 120),
            Track("jNQXAC9IVRw", "Restore second", durationSeconds = 20))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val player = future.get(10, TimeUnit.SECONDS)
        try {
            if (stage == "write") {
                instrumentation.runOnMainSync {
                    player.setMediaItems(tracks.map { it.toMediaItem("restore-test") }, 1, 9000)
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                Thread.sleep(500)
                scenario.onActivity {
                    ViewModelProvider(it)[PlayerViewModel::class.java].stop()
                }
                Thread.sleep(500)
                val saved = PlaybackStore(app).checkpoint()!!
                assertEquals(1, saved.index)
                assertEquals(9000, saved.positionMs)
                assertTrue(PlaybackStore(app).shuffle)
                assertEquals(Player.REPEAT_MODE_ALL, PlaybackStore(app).repeat)
            } else {
                instrumentation.runOnMainSync {
                    assertEquals(2, player.mediaItemCount)
                    assertEquals("jNQXAC9IVRw", player.currentMediaItem?.mediaId)
                    assertEquals(9000, player.currentPosition)
                    assertTrue(player.shuffleModeEnabled)
                    assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
                    assertFalse(player.playWhenReady)
                }
                scenario.onActivity {
                    ViewModelProvider(it)[PlayerViewModel::class.java].play(tracks, playlistId = "restore-test")
                }
                val deadline = System.currentTimeMillis() + 60_000
                var ready = false
                while (!ready && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        player.playerError?.let { throw AssertionError("Resume playback error", it) }
                        ready = player.isPlaying
                    }
                    Thread.sleep(100)
                }
                assertTrue("Resumed audio did not play", ready)
                instrumentation.runOnMainSync {
                    assertEquals("jNQXAC9IVRw", player.currentMediaItem?.mediaId)
                    assertTrue(player.currentPosition >= 9000)
                    assertTrue(player.currentPosition < 15_000)
                    assertTrue(player.shuffleModeEnabled)
                    assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
                    player.pause()
                }
            }
        } finally {
            instrumentation.runOnMainSync { MediaController.releaseFuture(future) }
            scenario.close()
        }
    }
}
