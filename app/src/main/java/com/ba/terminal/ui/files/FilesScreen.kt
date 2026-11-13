package com.ba.terminal.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.ba.terminal.ui.editor.EditorScreen
import com.ba.terminal.ui.theme.GlassText
import java.io.File

@Composable
fun FilesScreen(app: BaTerminalApp, modifier: Modifier = Modifier) {
    var dir by remember { mutableStateOf(app.workspace.root) }
    var editing by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableStateOf(0) }
    if (editing != null) {
        EditorScreen(file = editing!!, onBack = { editing = null; refresh++ })
        return
    }
    val files = remember(dir, refresh) { (dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Files", color = GlassText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Muted(dir.absolutePath + "  •  inside BA Linux this is /workspace")
        if (dir != app.workspace.root) {
            OutlinedButton(onClick = { dir = dir.parentFile ?: app.workspace.root }) { Text("↑ Back") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { File(dir, "new-file.txt").writeText(""); refresh++ }) { Text("+ File") }
            OutlinedButton(onClick = { File(dir, "new-folder").mkdirs(); refresh++ }) { Text("+ Folder") }
        }
        if (files.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) { Muted("Empty folder. Create a file, or run commands in the Terminal to make files here.") }
        }
        files.forEach { f ->
            GlassCard(Modifier.fillMaxWidth().clickable {
                if (f.isDirectory) dir = f else editing = f
            }) {
                Text(if (f.isDirectory) "📁 ${f.name}" else "📄 ${f.name}", color = GlassText, fontSize = 15.sp)
                Muted(if (f.isDirectory) "Folder" else "${f.length()} bytes — tap to edit")
            }
        }
    }
}
