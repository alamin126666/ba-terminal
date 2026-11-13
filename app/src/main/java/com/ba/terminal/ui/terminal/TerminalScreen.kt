package com.ba.terminal.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.terminal.BaTerminalApp
import com.ba.terminal.ui.components.Chip
import com.ba.terminal.ui.theme.GlassBlue
import com.ba.terminal.ui.theme.GlassCard
import com.ba.terminal.ui.theme.GlassGreen
import com.ba.terminal.ui.theme.GlassMuted
import com.ba.terminal.ui.theme.GlassText
import com.ba.terminal.ui.theme.MonoFont

@Composable
fun TerminalScreen(app: BaTerminalApp, modifier: Modifier = Modifier) {
    val tabs by app.terminals.tabs.collectAsState()
    val selected by app.terminals.selected.collectAsState()
    val term = app.terminals.current()
    val output by term.output.collectAsState()
    val cwd by term.cwd.collectAsState()
    val fontSize by app.settings.fontSize.collectAsState(initial = 14f)
    val extraKeys by app.settings.extraKeys.collectAsState(initial = true)
    var input by remember { mutableStateOf("") }
    var historyIdx by remember { mutableStateOf(-1) }
    val history by term.history.collectAsState()
    val scroll = rememberScrollState()

    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }
    LaunchedEffect(term) { term.start() }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, _ ->
                Chip(if (i == selected) "● Tab ${i + 1}" else "Tab ${i + 1}") { app.terminals.select(i) }
            }
            if (tabs.size < 5) Chip("+ New") { app.terminals.addTab() }
        }
        Text("ba@terminal:$cwd \$", color = GlassGreen, fontSize = 13.sp, fontFamily = MonoFont)
        Column(
            Modifier.weight(1f).fillMaxWidth().background(com.ba.terminal.ui.theme.GlassCard, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).verticalScroll(scroll).padding(12.dp)
        ) {
            Text(output, color = GlassText, fontSize = fontSize.sp, fontFamily = MonoFont)
        }
        if (extraKeys) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ESC", "TAB", "CTRL", "|", "/", "↑", "↓").forEach { key ->
                    Chip(key) {
                        when (key) {
                            "↑" -> { if (history.isNotEmpty()) { historyIdx = if (historyIdx < 0) history.lastIndex else (historyIdx - 1).coerceAtLeast(0); input = history.getOrNull(historyIdx) ?: input } }
                            "↓" -> { if (historyIdx >= 0) { historyIdx += 1; input = if (historyIdx > history.lastIndex) "" else history[historyIdx] } }
                            "TAB" -> term.send("\t")
                            else -> input += key
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command…  try: ba-help", color = GlassMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { term.send(input); input = ""; historyIdx = -1 } })
            )
            Button(onClick = { if (input.isNotBlank()) { term.send(input); input = ""; historyIdx = -1 } }) { Text("Run") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { term.clear() }) { Text("Clear") }
            OutlinedButton(onClick = { term.restart() }) { Text("Restart") }
            OutlinedButton(onClick = { term.send("ba-help") }) { Text("ba-help") }
        }
    }
}
