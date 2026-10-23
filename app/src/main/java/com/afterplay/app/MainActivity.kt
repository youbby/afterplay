package com.afterplay.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.afterplay.app.ui.AfterPlayScreen

class MainActivity : ComponentActivity() {
    private val model: PlayerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars(model.theme.value.dark)
        if (savedInstanceState == null) acceptShare(intent)
        setContent { AfterPlayScreen(model, onAddQuickSettingsTile = ::requestQuickSettingsTile, onThemeChanged = ::updateSystemBars) }
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
