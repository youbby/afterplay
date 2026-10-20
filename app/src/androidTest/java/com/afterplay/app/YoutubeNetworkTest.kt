package com.afterplay.app

import android.content.ComponentName
import android.os.PowerManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterplay.app.playback.PlaybackService
import com.afterplay.app.playback.toMediaItem
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Explicit opt-in: -Pandroid.testInstrumentationRunnerArguments.youtubeNetwork=true */
@RunWith(AndroidJUnit4::class)
class YoutubeNetworkTest {
    @Test fun publicYoutubeAudioLoadsAndPlaysInBackground() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("youtubeNetwork") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AfterPlayApp
        // A longer public clip avoids reaching the end while a slow device locks its screen.
        val resolved = app.youtube.resolve("M7lc1UVf-VE")
        assertTrue(resolved.audioUrl.startsWith("https://"))
        assertTrue(resolved.track.title.isNotBlank())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val player = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { player.setMediaItem(resolved.track.toMediaItem()); player.prepare(); player.play() }
            val deadline = System.currentTimeMillis() + 60_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    player.playerError?.let { throw AssertionError("YouTube playback error", it) }
                    ready = player.playbackState == Player.STATE_READY && player.isPlaying
                }
                Thread.sleep(200)
            }
            assertTrue("YouTube never reached playing", ready)
            var beforeScreenOff = 0L
            instrumentation.runOnMainSync {
                beforeScreenOff = player.currentPosition
                assertFalse("A video track must never be selected", player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
            }
            scenario.onActivity { it.moveTaskToBack(true) }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            Thread.sleep(5000)
            assertFalse("Device screen did not turn off", app.getSystemService(PowerManager::class.java).isInteractive)
            instrumentation.runOnMainSync {
                assertTrue(player.isPlaying)
                assertTrue("Audio position did not advance with the screen off", player.currentPosition > beforeScreenOff + 3000)
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            instrumentation.runOnMainSync { player.stop(); player.clearMediaItems(); MediaController.releaseFuture(future) }
            scenario.close()
        }
    }
}
