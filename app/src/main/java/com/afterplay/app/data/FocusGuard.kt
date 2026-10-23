package com.afterplay.app.data

import android.app.KeyguardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** This is an app touch guard; device authentication stays with Android. */
class FocusGuard(context: Context) {
    private val preferences = context.getSharedPreferences("focus_guard_v1", Context.MODE_PRIVATE)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val mutableLocked = MutableStateFlow(preferences.getBoolean("locked", false))
    val locked = mutableLocked.asStateFlow()
    val canLock: Boolean get() = keyguard?.isDeviceSecure == true

    fun lock(): Boolean {
        if (!canLock) return false
        setLocked(true)
        return true
    }

    fun unlockAfterAuthentication() = setLocked(false)

    private fun setLocked(value: Boolean) {
        // Persist before changing the UI so a restart cannot dismiss the guard.
        preferences.edit().putBoolean("locked", value).commit()
        mutableLocked.value = value
    }
}
