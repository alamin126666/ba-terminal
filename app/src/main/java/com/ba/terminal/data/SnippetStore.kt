package com.ba.terminal.data

import android.content.Context
import java.io.File

/** Snippets are stored one per line as: title<TAB>command */
class SnippetStore(context: Context) {
    private val file = File(context.filesDir, "snippets.txt")

    fun load(): List<Pair<String, String>> {
        if (!file.exists()) return listOf(
            "Update all" to "pkg update && pkg upgrade -y",
            "Python version" to "python3 --version",
            "Git status" to "git status",
            "List files" to "ls -la",
            "BA help" to "ba-help"
        )
        return file.readLines().mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    fun save(items: List<Pair<String, String>>) {
        file.writeText(items.joinToString("\n") { "${it.first}\t${it.second}" })
    }
}
