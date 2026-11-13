package com.ba.terminal.ui.snippets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ba.terminal.BaTerminalApp
import com.ba.terminal.ui.components.GlassCard
import com.ba.terminal.ui.components.Muted
import com.ba.terminal.ui.theme.GlassText

/** Saved commands — tap Run to send one to the current terminal tab. */
@Composable
fun SnippetsScreen(app: BaTerminalApp, onRun: (String) -> Unit, onClose: () -> Unit) {
    val items = app.snippets.load()
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (title, cmd) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(title, color = GlassText)
                Muted(cmd)
                Button(onClick = { onRun(cmd); onClose() }, modifier = Modifier.padding(top = 6.dp)) { Text("Run") }
            }
        }
        OutlinedButton(onClick = onClose) { Text("Close") }
    }
}
