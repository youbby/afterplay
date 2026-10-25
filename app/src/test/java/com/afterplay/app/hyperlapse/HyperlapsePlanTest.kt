package com.afterplay.app.hyperlapse

import org.junit.Assert.*
import org.junit.Test

class HyperlapsePlanTest {
    @Test fun everyAllowedFocusDurationProducesExactlyFifteenSeconds() {
        for (minutes in 20..60) {
            val duration = minutes * 60_000L
            val slots = (0 until 450).map { frame ->
                val elapsed = (duration * frame + 449) / 450
                HyperlapsePlan.slot(duration, duration - elapsed)
            }
            assertEquals((0 until 450).toList(), slots)
            assertEquals(15_000_000, HyperlapsePlan.presentationUs(slots.size))
        }
    }

    @Test fun oneThroughFourCompletedRoundsHaveFixedBoundedDurations() {
        for (rounds in 1..4) {
            val frames = (0 until rounds).flatMap { HyperlapsePlan.sourceSlots((0 until 450).toList(), true) }
            assertEquals(rounds * 15_000_000L, HyperlapsePlan.presentationUs(frames.size))
            assertTrue(frames.size <= HyperlapsePlan.MAX_FRAMES)
        }
    }

    @Test fun sparseCameraDeliveryRepeatsActualImagesAndPartialRoundsStayPartial() {
        val full = HyperlapsePlan.sourceSlots(listOf(2, 90, 300, 449), true)
        assertEquals(450, full.size)
        assertEquals(2, full.first())
        assertEquals(90, full[200])
        assertEquals(449, full.last())
        assertEquals(91, HyperlapsePlan.sourceSlots(listOf(2, 90), false).size)
        assertTrue(HyperlapsePlan.sourceSlots(emptyList(), true).isEmpty())
    }

    @Test fun pauseAndRestCannotAlterTheMappingOfFocusRemainingTime() {
        val duration = 60 * 60_000L
        assertEquals(225, HyperlapsePlan.slot(duration, duration / 2))
        assertEquals(0, HyperlapsePlan.slot(duration, duration + 1))
        assertEquals(449, HyperlapsePlan.slot(duration, -1))
    }
}
