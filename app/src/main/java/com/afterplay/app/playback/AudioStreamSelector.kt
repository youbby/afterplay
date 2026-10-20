package com.afterplay.app.playback

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/** Choose the language before comparing quality, so a higher bitrate dub cannot win. */
internal fun selectAudioStream(streams: List<AudioStream>): AudioStream? = streams
    .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
    .maxWithOrNull(compareBy<AudioStream> { it.voicePriority() }.thenBy { it.averageBitrate })

private fun AudioStream.voicePriority(): Int {
    val language = audioLocale?.language?.takeIf { it.isNotBlank() }
        ?: audioTrackId?.substringBefore('.')?.substringBefore('-')?.substringBefore('_')
    val korean = language.equals("ko", ignoreCase = true) || language.equals("kor", ignoreCase = true)
    return when {
        korean && audioTrackType == AudioTrackType.ORIGINAL -> 6
        korean && (audioTrackType == null || audioTrackType == AudioTrackType.DUBBED) -> 5
        audioTrackType == AudioTrackType.ORIGINAL -> 4
        audioTrackType == null -> 3 // Older videos expose only their default audio, without language metadata.
        audioTrackType == AudioTrackType.DUBBED -> 2
        else -> 1
    }
}
