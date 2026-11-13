package com.ba.terminal.data

import android.content.Context
import java.io.File

/** App workspace on the host: bound to /workspace inside BA Linux. */
class WorkspaceStore(context: Context) {
    val root: File = File(context.filesDir, "workspace").apply { mkdirs() }
}
