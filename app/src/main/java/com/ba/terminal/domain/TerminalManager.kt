package com.ba.terminal.domain

import com.ba.terminal.data.WorkspaceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Multi-tab terminal: each tab owns one persistent BaTerminal session. */
class TerminalManager(
    private val workspace: WorkspaceStore,
    private val linux: BaLinux
) {
    private val _tabs = MutableStateFlow<List<BaTerminal>>(listOf(BaTerminal(workspace, linux)))
    val tabs: StateFlow<List<BaTerminal>> = _tabs.asStateFlow()

    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    fun current(): BaTerminal = _tabs.value[_selected.value.coerceIn(_tabs.value.indices)]

    fun addTab() {
        if (_tabs.value.size >= 5) return
        _tabs.value = _tabs.value + BaTerminal(workspace, linux)
        _selected.value = _tabs.value.lastIndex
    }

    fun select(i: Int) { if (i in _tabs.value.indices) _selected.value = i }

    fun closeTab(i: Int) {
        if (_tabs.value.size <= 1 || i !in _tabs.value.indices) return
        val list = _tabs.value.toMutableList()
        list[i].restart()
        list.removeAt(i)
        _tabs.value = list
        _selected.value = _selected.value.coerceIn(list.indices)
    }
}
