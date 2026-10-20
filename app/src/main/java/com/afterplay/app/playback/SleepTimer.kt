package com.afterplay.app.playback

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** A one-shot timer owned by the playback service, independent of the activity. */
class SleepTimer(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var deadline = 0L
    private var onExpire: (() -> Unit)? = null
    private val alarm = AlarmManager.OnAlarmListener { checkDeadline() }
    private val timeout = Runnable { checkDeadline() }

    val remainingMs: Long get() = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    val active: Boolean get() = deadline != 0L

    fun attach(action: () -> Unit) { onExpire = action }

    fun startPreset(minutes: Int) {
        require(minutes in listOf(20, 40, 60))
        schedule(minutes * 60_000L)
    }

    // Internal duration allows device tests to exercise expiration without waiting 20 minutes.
    @SuppressLint("ScheduleExactAlarm")
    internal fun schedule(durationMs: Long) {
        check(onExpire != null) { "Playback service is not connected" }
        require(durationMs > 0)
        cancel()
        deadline = SystemClock.elapsedRealtime() + durationMs
        handler.postDelayed(timeout, durationMs)
        // Listener-based setExact does not need the special exact-alarm permission.
        // The playback service stays alive while audio is playing; Handler is a fallback.
        runCatching { alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline,
            "AfterPlay sleep timer", alarm, handler) }
    }

    fun checkDeadline() {
        if (deadline == 0L) return
        val remaining = remainingMs
        if (remaining > 0) {
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, remaining)
            return
        }
        cancel()
        onExpire?.invoke()
    }

    fun cancel() {
        deadline = 0
        handler.removeCallbacks(timeout)
        alarms.cancel(alarm)
    }

    fun detach() { cancel(); onExpire = null }
}
