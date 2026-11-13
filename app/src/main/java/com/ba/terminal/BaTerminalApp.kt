package com.ba.terminal

import android.app.Application
import com.ba.terminal.data.SettingsStore
import com.ba.terminal.data.SnippetStore
import com.ba.terminal.data.WorkspaceStore
import com.ba.terminal.domain.BaLinux
import com.ba.terminal.domain.TerminalManager

class BaTerminalApp : Application() {
    lateinit var workspace: WorkspaceStore
    lateinit var linux: BaLinux
    lateinit var terminals: TerminalManager
    lateinit var settings: SettingsStore
    lateinit var snippets: SnippetStore

    override fun onCreate() {
        super.onCreate()
        workspace = WorkspaceStore(this)
        linux = BaLinux(this, workspace)
        terminals = TerminalManager(workspace, linux)
        settings = SettingsStore(this)
        snippets = SnippetStore(this)
    }
}
