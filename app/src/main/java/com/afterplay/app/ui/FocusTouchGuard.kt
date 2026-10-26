package com.afterplay.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private data class FocusLockTarget(val bounds: Rect, val tint: Color)
private val LocalFocusLockTarget = staticCompositionLocalOf<(FocusLockTarget?) -> Unit> { {} }

@Composable
internal fun FocusTouchGuard(locked: Boolean, onUnlock: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = locked) { }
    var target by remember { mutableStateOf<FocusLockTarget?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val register = remember { { value: FocusLockTarget? -> target = value } }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        CompositionLocalProvider(LocalFocusLockTarget provides register) {
            Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { content() }
        }
        if (locked) {
            Box(Modifier.matchParentSize().testTag("focus_touch_blocker").pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
            // Keep the normal layout intact, exposing only its lock button above the touch/semantics guard.
            target?.let { button ->
                val position = button.bounds.topLeft - origin
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    FocusLockControl(locked = true, onClick = onUnlock, tint = button.tint, modifier = Modifier
                        .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                        .size(with(density) { button.bounds.width.toDp() }, with(density) { button.bounds.height.toDp() })
                        .testTag("focus_unlock").semantics { stateDescription = "잠김" })
                }
            }
        }
    }
}

@Composable
internal fun FocusLockButton(locked: Boolean, onLock: () -> Unit, tint: Color) {
    val register = LocalFocusLockTarget.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val target = bounds?.let { FocusLockTarget(it, tint) }
    SideEffect { target?.let(register) }
    DisposableEffect(register) { onDispose { register(null) } }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        FocusLockControl(locked, onLock, tint, modifier = Modifier.size(48.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .testTag("focus_lock").semantics { stateDescription = if (locked) "잠김" else "잠금 해제됨" })
    }
}

@Composable
private fun FocusLockControl(locked: Boolean, onClick: () -> Unit, tint: Color, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = if (locked) tint else tint.copy(alpha = .12f),
        contentColor = if (locked) LocalPalette.current.background else tint,
        border = BorderStroke(1.5.dp, tint), shadowElevation = if (locked) 2.dp else 0.dp) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                if (locked) "집중 보호 잠금 해제" else "집중 보호 켜기",
                modifier = Modifier.size(22.dp))
            Text(if (locked) "잠김" else "열림", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1)
        }
    }
}
