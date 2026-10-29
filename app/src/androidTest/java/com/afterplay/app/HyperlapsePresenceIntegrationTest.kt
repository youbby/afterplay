package com.afterplay.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.hyperlapse.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HyperlapsePresenceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun bundledDetectorRecognizesFaceAndEmptySceneWithoutModelDownload() {
        // Public TensorFlow example image; used only as a detector fixture, never for identity matching.
        val face = instrumentation.context.assets.open("face-presence.jpg").use { BitmapFactory.decodeStream(it) }!!
        val empty = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        try {
            HyperlapsePresenceDetector().use { detector ->
                assertTrue("Bundled detector must find the fixture face", detector.isPresent(face))
                assertFalse("Empty scene must count as away", detector.isPresent(empty))
            }
        } finally { face.recycle(); empty.recycle() }
    }

    @Test fun recoveredCutsAreCountedOnceAndSummaryIsEncodedAfterAllFourRounds() {
        val directory = File(context.cacheDir, "presence-summary-test").apply { mkdirs() }
        val spool = HyperlapseFrames(directory)
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            for (round in 0..3) {
                for ((slot, presence) in listOf(0 to "focused", 224 to "focused", 449 to "away")) {
                    File(directory, "${round}_${slot.toString().padStart(3, '0')}.jpg").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
                    }
                    File(directory, "${round}_${slot.toString().padStart(3, '0')}.presence").writeText(presence)
                }
                spool.complete(round)
            }
            File(directory, "0_001.presence").writeText("away") // Interrupted write, no actual camera cut.
            val recovered = HyperlapseFrames(directory)
            assertEquals(HyperlapsePresence(8, 4), recovered.presence())
            assertEquals(67, recovered.presence().focusedPercent)
            assertEquals(33, recovered.presence().awayPercent)
            val frames = recovered.exportFrames()
            assertEquals(HyperlapsePlan.MAX_ENCODED_FRAMES, frames.size)
            assertEquals(90, frames.takeLast(90).count { it.name == "summary.jpg" })
            assertEquals(1800, frames.count { it.name != "summary.jpg" })
            File(directory, "summary.jpg").copyTo(File(context.getExternalFilesDir(null), "presence-summary.jpg"), overwrite = true)
            val video = File(directory, "summary.mp4")
            HyperlapseEncoder.encode(frames, video)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(video.absolutePath)
                assertEquals(1, extractor.trackCount)
                assertEquals(63_000_000L, extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION))
            } finally { extractor.release() }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.absolutePath)
                val last = requireNotNull(retriever.getFrameAtTime(62_000_000, MediaMetadataRetriever.OPTION_CLOSEST))
                try {
                    assertTrue("Summary background replaces the blue footage", Color.blue(last.getPixel(10, 10)) < 80)
                    File(context.getExternalFilesDir(null), "presence-summary-encoded.png").outputStream().use {
                        last.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { last.recycle() }
            } finally { retriever.release() }
            video.copyTo(File(context.getExternalFilesDir(null), "presence-summary.mp4"), overwrite = true)
        } finally { bitmap.recycle(); spool.clear() }
    }

    @Test fun legacySpoolHasUnknownCutsAndAnEmptySpoolHasNoEndingOnlyVideo() {
        val spool = HyperlapseFrames(File(context.cacheDir, "presence-legacy-test"))
        try {
            assertTrue(spool.exportFrames().isEmpty())
            File(spool.directory, "0_000.jpg").writeBytes(byteArrayOf())
            assertEquals(HyperlapsePresence(0, 0, 1), spool.presence())
        } finally { spool.clear() }
    }
}
