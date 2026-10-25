package com.afterplay.app.hyperlapse

/** Sampling follows active focus time, never wall time spent paused or resting. */
object HyperlapsePlan {
    const val FPS = 30
    const val SECONDS_PER_ROUND = 15
    const val FRAMES_PER_ROUND = FPS * SECONDS_PER_ROUND
    const val MAX_FRAMES = FRAMES_PER_ROUND * 4
    const val SIZE = 720

    fun slot(durationMs: Long, remainingMs: Long): Int {
        require(durationMs > 0)
        return ((durationMs - remainingMs.coerceIn(0, durationMs)) * FRAMES_PER_ROUND / durationMs)
            .toInt().coerceIn(0, FRAMES_PER_ROUND - 1)
    }

    fun presentationUs(frame: Int): Long = frame.toLong() * 1_000_000 / FPS

    /** Missing camera frames repeat the nearest earlier image; timestamps stay those of that image. */
    fun sourceSlots(available: List<Int>, completed: Boolean): List<Int> {
        if (available.isEmpty()) return emptyList()
        val slots = available.distinct().sorted()
        require(slots.all { it in 0 until FRAMES_PER_ROUND })
        val count = if (completed) FRAMES_PER_ROUND else slots.last() + 1
        var index = 0
        return List(count) { target ->
            while (index + 1 < slots.size && slots[index + 1] <= target) index++
            slots[index]
        }
    }
}
