package com.afterplay.app.data

import com.afterplay.app.playback.selectAudioStream
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.Locale

class AudioStreamSelectorTest {
    private fun stream(language: String? = null, type: AudioTrackType? = null, bitrate: Int = 128,
                       trackId: String? = null, isUrl: Boolean = true,
                       delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP): AudioStream = AudioStream.Builder()
        .setId("test").setContent("https://example.com/audio", isUrl)
        .setDeliveryMethod(delivery).setAverageBitrate(bitrate)
        .setAudioLocale(language?.let(Locale::forLanguageTag))
        .setAudioTrackId(trackId).setAudioTrackType(type).build()

    @Test fun koreanOriginalWinsOverHigherBitrateDubs() {
        val original = stream("ko-KR", AudioTrackType.ORIGINAL, 64)
        assertSame(original, selectAudioStream(listOf(
            stream("en", AudioTrackType.DUBBED, 256), original,
            stream("ko", AudioTrackType.DUBBED, 192), stream("ko", AudioTrackType.DESCRIPTIVE, 320))))
    }

    @Test fun koreanVoiceWinsWhenAvailable() {
        val korean = stream("ko", AudioTrackType.DUBBED, 64)
        assertSame(korean, selectAudioStream(listOf(stream("en", AudioTrackType.ORIGINAL, 256), korean)))
    }

    @Test fun fallsBackToOriginalWhenKoreanIsAbsent() {
        val original = stream("ja", AudioTrackType.ORIGINAL, 64)
        assertSame(original, selectAudioStream(listOf(stream("en", AudioTrackType.DUBBED, 256), original)))
    }

    @Test fun trackIdIdentifiesKoreanWhenLocaleIsMissing() {
        val korean = stream(type = AudioTrackType.ORIGINAL, trackId = "ko.4", bitrate = 64)
        assertSame(korean, selectAudioStream(listOf(stream("en", AudioTrackType.DUBBED, 256), korean)))
    }

    @Test fun legacyDefaultAudioAndBestQualityRemainSupported() {
        val best = stream(bitrate = 192)
        assertSame(best, selectAudioStream(listOf(stream(bitrate = 64), best,
            stream("en", AudioTrackType.DUBBED, 256))))
        assertNull(selectAudioStream(emptyList()))
    }

    @Test fun unsupportedDeliveryAndNonUrlsAreExcluded() {
        val playable = stream("ja", AudioTrackType.ORIGINAL)
        assertSame(playable, selectAudioStream(listOf(playable,
            stream("ko", AudioTrackType.ORIGINAL, delivery = DeliveryMethod.DASH),
            stream("ko", AudioTrackType.ORIGINAL, isUrl = false))))
    }
}
