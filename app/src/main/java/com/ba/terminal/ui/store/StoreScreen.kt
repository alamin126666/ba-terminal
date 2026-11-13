package com.ba.terminal.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.terminal.BaTerminalApp
import com.ba.terminal.ui.components.GlassCard
import com.ba.terminal.ui.components.Muted
import com.ba.terminal.ui.theme.GlassText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class Tool(val name: String, val pkg: String, val cmd: String, val desc: String)

private val TOOLS = listOf(
    Tool("Python", "python3", "python3", "python3 + pip3"),
    Tool("Node.js", "nodejs", "node", "node + npm"),
    Tool("Git", "git", "git", "version control"),
    Tool("Nano", "nano", "nano", "simple editor"),
    Tool("Vim", "vim", "vim", "powerful editor"),
    Tool("SSH", "openssh-client", "ssh", "ssh + scp"),
    Tool("Curl", "curl", "curl", "download / API calls"),
    Tool("GCC", "gcc", "gcc", "C compiler"),
    Tool("Go", "go", "go", "Go language"),
    Tool("Rust", "rust", "cargo", "Rust + cargo")
)

@Composable
fun StoreScreen(app: BaTerminalApp, modifier: Modifier = Modifier) {
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Store — one-tap install", color = GlassText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Muted("Runs pkg install in BA Linux. Anything else: pkg search <name> in the Terminal.")
        TOOLS.forEach { tool ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(tool.name, color = GlassText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Muted(tool.desc + "  •  pkg install " + tool.pkg)
                Button(
                    onClick = {
                        busy = tool.name
                        CoroutineScope(Dispatchers.Main).launch {
                            val r = app.terminals.current().runCommand("pkg install ${tool.pkg}", 300)
                            busy = null
                            if (r.ok) installed = installed + tool.name
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text(if (busy == tool.name) "Installing…" else if (installed.contains(tool.name)) "Installed ✓" else "Install") }
            }
        }
    }
}
