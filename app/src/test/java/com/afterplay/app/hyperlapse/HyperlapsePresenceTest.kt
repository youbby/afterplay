package com.afterplay.app.hyperlapse

import org.junit.Assert.*
import org.junit.Test

class HyperlapsePresenceTest {
    @Test fun percentagesUseActualCutsAndAlwaysAddToOneHundred() {
        val summary = HyperlapsePresence.from(List(400) { true } + List(50) { false })
        assertEquals(450, summary.total)
        assertEquals(50, summary.away)
        assertEquals(89, summary.focusedPercent)
        assertEquals(11, summary.awayPercent)
        for (focused in 0..450) {
            val counts = HyperlapsePresence(focused, 450 - focused)
            assertEquals(100, counts.focusedPercent + counts.awayPercent)
        }
    }

    @Test fun missingLegacyRecordsAreNotInventedAsAbsence() {
        val summary = HyperlapsePresence.from(listOf(true, false, null, null))
        assertEquals(4, summary.total)
        assertEquals(1, summary.away)
        assertEquals(2, summary.unknown)
        assertEquals(25, summary.focusedPercent)
        assertEquals(25, summary.awayPercent)
        assertEquals(0, HyperlapsePresence.from(emptyList()).total)
    }
}
