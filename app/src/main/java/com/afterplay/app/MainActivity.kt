package com.afterplay.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.ContextCompat
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.afterplay.app.ui.AfterPlayScreen

class MainActivity : FragmentActivity() {
    private val model: PlayerViewModel by viewModels()
    private lateinit var unlockPrompt: BiometricPrompt
    private var orientationBeforeFocus: Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orientationBeforeFocus = savedInstanceState?.takeIf { it.containsKey("orientation_before_focus") }
            ?.getInt("orientation_before_focus")
        updateFocusOrientation(model.pomodoro.value.running)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.pomodoro.map { it.running }.distinctUntilChanged().collect(::updateFocusOrientation)
            }
        }
        updateSystemBars(model.theme.value.dark)
        unlockPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    (application as AfterPlayApp).focusGuard.unlockAfterAuthentication()
                }
            })
        if (savedInstanceState == null) acceptShare(intent)
        setContent { AfterPlayScreen(model, onAddQuickSettingsTile = ::requestQuickSettingsTile,
            onThemeChanged = ::updateSystemBars, onUnlockFocus = ::unlockFocus,
            onSetScreenLock = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) }
    }
    private fun updateFocusOrientation(running: Boolean) {
        if (running) {
            if (orientationBeforeFocus == null) orientationBeforeFocus = requestedOrientation
                .takeUnless { it == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (orientationBeforeFocus != null || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            val previous = orientationBeforeFocus ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationBeforeFocus = null
            if (requestedOrientation != previous) requestedOrientation = previous
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        orientationBeforeFocus?.let { outState.putInt("orientation_before_focus", it) }
        super.onSaveInstanceState(outState)
    }
    private fun unlockFocus() {
        val guard = (application as AfterPlayApp).focusGuard
        if (!guard.locked.value) return
        if (!guard.canLock) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            return
        }
        unlockPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("집중 보호 해제")
            .setSubtitle("기기에 등록된 지문 또는 화면 잠금으로 인증하세요")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build())
    }
    private fun updateSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }
    private fun acceptShare(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            model.sharedLink.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            model.message.value = getString(R.string.quick_settings_manual)
            return
        }
        val manager = getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            model.message.value = getString(R.string.quick_settings_manual)
            return
        }
        manager.requestAddTileService(
            ComponentName(this, QuickSettingsService::class.java),
            getString(R.string.quick_settings_label),
            Icon.createWithResource(this, R.drawable.ic_quick_settings),
            mainExecutor,
        ) { result ->
            model.message.value = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "퀵 설정에 이어듣기 버튼을 추가했어요."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "퀵 설정에 이미 이어듣기 버튼이 있어요."
                else -> getString(R.string.quick_settings_manual)
            }
        }
    }
}
