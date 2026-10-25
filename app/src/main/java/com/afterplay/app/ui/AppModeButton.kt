package com.afterplay.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.afterplay.app.data.AppMode

@Composable
internal fun AppModeButton(mode: AppMode, onToggle: () -> Unit, tint: Color) {
    val pomodoro = mode == AppMode.POMODORO
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        IconButton(onClick = onToggle, modifier = Modifier.size(40.dp).testTag("app_mode_toggle").semantics {
            stateDescription = if (pomodoro) "뽀모도로 모드" else "재생 모드"
        }) {
            Icon(if (pomodoro) Icons.Rounded.Headphones else Icons.Rounded.Timer,
                if (pomodoro) "재생 모드로 전환" else "뽀모도로 모드로 전환",
                tint = tint, modifier = Modifier.size(21.dp))
        }
    }
}
