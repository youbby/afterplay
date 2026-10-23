package com.afterplay.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FocusTouchGuard(locked: Boolean, onUnlock: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = locked) { }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().then(
            if (locked) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { content() }
            if (locked) Box(Modifier.matchParentSize().testTag("focus_touch_blocker").pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        }
        if (locked) Surface(color = Surface) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = Accent, modifier = Modifier.size(16.dp))
                Text("집중 보호 중", color = Accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onUnlock, modifier = Modifier.testTag("focus_unlock")) {
                    Icon(Icons.Rounded.Fingerprint, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("잠금 해제")
                }
            }
        }
    }
}
