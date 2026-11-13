package com.ba.terminal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.terminal.BaTerminalApp
import com.ba.terminal.ui.components.GlassCard
import com.ba.terminal.ui.components.Muted
import com.ba.terminal.ui.components.SectionTitle
import com.ba.terminal.ui.theme.GlassText
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: BaTerminalApp, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val fontSize by app.settings.fontSize.collectAsState(initial = 14f)
    val alpha by app.settings.transparency.collectAsState(initial = 0.92f)
    val extraKeys by app.settings.extraKeys.collectAsState(initial = true)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = GlassText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Theme — Minimal Glass Clean")
            Muted("Dark charcoal + frosted glass + light blue. This is the style you picked (Option 3). More themes soon.")
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Font size — ${fontSize.toInt()} sp")
            Slider(value = fontSize, onValueChange = { scope.launch { app.settings.setFontSize(it) } }, valueRange = 11f..20f)
            SectionTitle("Transparency — ${(alpha * 100).toInt()}%")
            Slider(value = alpha, onValueChange = { scope.launch { app.settings.setTransparency(it) } }, valueRange = 0.7f..1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Extra-keys row", color = GlassText)
                Switch(checked = extraKeys, onCheckedChange = { scope.launch { app.settings.setExtraKeys(it) } })
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Backup")
            Muted("Workspace files are in the app workspace (/workspace in BA Linux). Copy them from Files, or back them up from the Terminal.")
            Button(onClick = { app.terminals.current().send("ls -la /workspace") }, modifier = Modifier.padding(top = 8.dp)) { Text("List workspace in Terminal") }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("About")
            Muted("BA TERMINAL 1.0.0  •  Alpine Linux 3.24.4 + PRoot  •  No server, no VPS, no root. All commands via pkg / apk.")
        }
    }
}
