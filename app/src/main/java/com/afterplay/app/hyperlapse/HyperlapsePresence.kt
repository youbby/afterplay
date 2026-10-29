package com.afterplay.app.hyperlapse

import kotlin.math.roundToInt

/** Counts actual camera cuts, never the duplicate frames used to fill a video round. */
internal data class HyperlapsePresence(val focused: Int, val away: Int, val unknown: Int = 0) {
    val total: Int get() = focused + away + unknown
    val focusedPercent: Int get() = if (total == 0) 0 else (focused * 100.0 / total).roundToInt()
    val awayPercent: Int get() = if (unknown == 0 && total > 0) 100 - focusedPercent
        else if (total == 0) 0 else (away * 100.0 / total).roundToInt()

    companion object {
        fun from(cuts: List<Boolean?>) = HyperlapsePresence(cuts.count { it == true },
            cuts.count { it == false }, cuts.count { it == null })
    }
}
