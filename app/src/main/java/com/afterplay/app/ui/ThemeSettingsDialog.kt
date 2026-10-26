package com.afterplay.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterplay.app.PlayerViewModel
import com.afterplay.app.data.ThemePreset

@Composable
internal fun ThemeSettingsDialog(model: PlayerViewModel, theme: ThemePreset, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("테마 설정") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (preset in ThemePreset.entries) ThemeCard(preset, preset == theme) { model.setTheme(preset) }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("완료") } })
}

@Composable
private fun ThemeCard(preset: ThemePreset, active: Boolean, onSelect: () -> Unit) {
    val colors = preset.colors()
    Column(Modifier.fillMaxWidth().testTag("theme_${preset.name}")
        .border(if (active) 2.dp else 1.dp, if (active) Accent else Muted.copy(alpha = .25f), RoundedCornerShape(16.dp))
        .background(colors.background, RoundedCornerShape(16.dp))
        .clickable(role = Role.RadioButton, onClick = onSelect).semantics { selected = active }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.weight(1f))
            if (active) Icon(Icons.Rounded.CheckCircle, "선택됨", tint = colors.emphasis, modifier = Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            preset.swatches.forEach { color ->
                Box(Modifier.size(22.dp).background(Color(color), CircleShape).border(1.dp, colors.ink.copy(alpha = .12f), CircleShape))
            }
        }
        Text(preset.description, fontSize = 10.sp, color = colors.muted)
    }
}

