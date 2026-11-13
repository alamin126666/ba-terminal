package com.ba.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import com.ba.terminal.ui.files.FilesScreen
import com.ba.terminal.ui.home.HomeScreen
import com.ba.terminal.ui.settings.SettingsScreen
import com.ba.terminal.ui.store.StoreScreen
import com.ba.terminal.ui.terminal.TerminalScreen
import com.ba.terminal.ui.theme.BaTerminalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BaTerminalApp
        setContent { BaTerminalTheme { MainShell(app) } }
    }
}

private data class Tab(val name: String, val icon: ImageVector)

@Composable
private fun MainShell(app: BaTerminalApp) {
    val tabs = listOf(
        Tab("Home", Icons.Filled.Home),
        Tab("Terminal", Icons.Filled.PlayArrow),
        Tab("Files", Icons.Filled.Folder),
        Tab("Store", Icons.Filled.Search),
        Tab("Settings", Icons.Filled.Settings)
    )
    var selected by remember { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(tab.icon, contentDescription = tab.name) },
                        label = { Text(tab.name) }
                    )
                }
            }
        }
    ) { pad ->
        val mod = Modifier.padding(pad)
        when (selected) {
            0 -> HomeScreen(app, mod) { selected = 1 }
            1 -> TerminalScreen(app, mod)
            2 -> FilesScreen(app, mod)
            3 -> StoreScreen(app, mod)
            else -> SettingsScreen(app, mod)
        }
    }
}
