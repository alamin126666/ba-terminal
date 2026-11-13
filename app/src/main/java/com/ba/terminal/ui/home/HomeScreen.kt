package com.ba.terminal.ui.home

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.terminal.BaTerminalApp
import com.ba.terminal.ui.components.Chip
import com.ba.terminal.ui.components.GlassCard
import com.ba.terminal.ui.components.Muted
import com.ba.terminal.ui.components.SectionTitle
import com.ba.terminal.ui.theme.GlassGreen
import com.ba.terminal.ui.theme.GlassText

@Composable
fun HomeScreen(app: BaTerminalApp, modifier: Modifier = Modifier, onOpenTerminal: () -> Unit) {
    val state by app.linux.state.collectAsState()
    val mem = remember { readMem(app) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BA TERMINAL", color = GlassText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Muted("Our own Linux terminal — no server, no VPS. Phone CPU + RAM only.")
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("BA Linux")
            Text(state, color = GlassGreen, fontSize = 14.sp)
            Muted("Alpine Linux 3.24.4 + PRoot. First run unpacks, then apk update runs.")
            Button(onClick = onOpenTerminal, modifier = Modifier.padding(top = 12.dp)) { Text("Open Terminal") }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("This phone")
            Text(mem, color = GlassText, fontSize = 14.sp)
            Muted("Workspace: /workspace  •  Files live in the app workspace.")
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Quick install")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Python") { app.terminals.current().send("pkg install python3") ; onOpenTerminal() }
                Chip("Node") { app.terminals.current().send("pkg install nodejs") ; onOpenTerminal() }
                Chip("Git") { app.terminals.current().send("pkg install git") ; onOpenTerminal() }
                Chip("Update") { app.terminals.current().send("pkg update && pkg upgrade -y") ; onOpenTerminal() }
            }
            Muted("Any command: pkg search <name>, then pkg install <name>. Try ba-help in the terminal.")
        }
    }
}

private fun readMem(app: BaTerminalApp): String {
    return try {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem / 1024 / 1024 / 1024
        val sf = StatFs(app.filesDir.path)
        val freeGb = sf.availableBytes / 1024 / 1024 / 1024
        "RAM total: ${totalGb} GB  •  Free storage: ${freeGb} GB"
    } catch (_: Exception) {
        "RAM / storage info not available"
    }
}
