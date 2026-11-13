package com.ba.terminal.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ba.terminal.ui.components.Muted
import com.ba.terminal.ui.theme.MonoFont
import java.io.File

@Composable
fun EditorScreen(file: File, onBack: () -> Unit) {
    var text by remember { mutableStateOf(if (file.exists() && file.length() < 200_000) file.readText() else "") }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(file.name)
        Muted(file.absolutePath)
        OutlinedTextField(
            value = text, onValueChange = { text = it; saved = false },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { file.writeText(text); saved = true }) { Text("Save") }
            OutlinedButton(onClick = onBack) { Text("Back") }
            if (saved) Muted("Saved ✓")
        }
    }
}
