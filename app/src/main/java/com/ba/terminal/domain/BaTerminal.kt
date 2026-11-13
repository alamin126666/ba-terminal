package com.ba.terminal.domain

import com.ba.terminal.data.WorkspaceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BA Terminal — our own terminal, built into this app.
 *
 * The shell runs inside BA Linux (a full Alpine Linux environment on
 * this phone's own CPU and RAM, via PRoot) whenever the sandbox is
 * ready — that is the real Termux-like experience: `apk add python3
 * nodejs git`, real tools, a real package manager. If the sandbox
 * cannot run on this device, it falls back to the plain system shell.
 *
 * One persistent session, shared by the chat agent and the Terminal
 * tab, so `cd` and shell variables persist between commands.
 */
data class ToolResult(val ok: Boolean, val output: String)

class BaTerminal(
    private val workspace: WorkspaceStore,
    private val linux: BaLinux
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val startMutex = Mutex()
    private var process: Process? = null

    /** True when the running shell is inside BA Linux. */
    @Volatile
    var inLinux = false
        private set

    private val _output = MutableStateFlow(
        "BA Terminal — our own terminal, built into BA TERMINAL.\n" +
            "First command sets up BA Linux (Alpine on this phone).\n"
    )
    val output: StateFlow<String> = _output.asStateFlow()

    private val _cwd = MutableStateFlow(workspace.root.absolutePath)
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007|\u001B\\(B")
    private val maxOutput = 200_000

    fun isAlive(): Boolean = synchronized(lock) { process?.isAlive == true }

    /** Start the shell if it is not running. Safe to call repeatedly. */
    suspend fun start() = startMutex.withLock {
        if (process?.isAlive == true) return
        val useLinux = linux.ensureReady { appendOutput(it) }
        inLinux = useLinux
        try {
            val pb = if (useLinux) {
                _cwd.value = linux.guestWorkspace
                ProcessBuilder(linux.prootCommand()).apply {
                    environment().putAll(linux.guestEnv())
                }
            } else {
                _cwd.value = workspace.root.absolutePath
                ProcessBuilder("/system/bin/sh").directory(File(_cwd.value))
            }
            val proc = pb.redirectErrorStream(true).start()
            process = proc
            if (useLinux) {
                appendOutput("\nBA Linux shell started — Alpine Linux, on this phone.\n" +
                    "Your files live in /workspace. Install tools with: apk add python3 nodejs git\n")
            } else {
                appendOutput("\nSystem shell started (BA Linux is not available on this device).\n")
            }
            val t = Thread({
                val buf = ByteArray(4096)
                try {
                    val input = proc.inputStream
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        appendOutput(String(buf, 0, n, Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                    // Shell ended.
                }
                appendOutput("\n(shell ended — press Restart for a new one)\n")
            }, "ba-terminal-reader")
            t.isDaemon = true
            t.start()
        } catch (e: Exception) {
            appendOutput("\nCould not start shell: ${e.message}\n")
        }
    }

    /** Send a command from the Terminal tab (fire and forget). */
    fun send(raw: String) {
        val command = raw.trimEnd()
        if (command.isBlank()) return
        scope.launch {
            start()
            val toSend = rewriteBareCd(command)
            trackCd(toSend)
            _history.value = (_history.value + command).takeLast(100)
            appendOutput("\n$ $command\n")
            writeLine(toSend)
        }
    }

    /**
     * Run one command and wait for it to finish (used by the agent).
     * The marker echo tells us the exit code; output is everything the
     * command printed. Times out after [timeoutSec] seconds.
     */
    suspend fun runCommand(command: String, timeoutSec: Long = 30): ToolResult =
        withContext(Dispatchers.IO) {
            val cmd = command.trim()
            if (cmd.isEmpty()) return@withContext ToolResult(false, "(empty command)")
            start()
            if (!isAlive()) {
                return@withContext ToolResult(false, "Shell is not running. Restart the terminal and try again.")
            }
            val marker = "__BA_DONE_${System.nanoTime()}__"
            val toSend = rewriteBareCd(cmd)
            trackCd(toSend)
            _history.value = (_history.value + cmd).takeLast(100)
            appendOutput("\n$ $cmd\n")
            val before = _output.value.length
            writeLine(toSend)
            writeLine("echo $marker:\$?")
            val deadline = System.currentTimeMillis() + timeoutSec * 1000
            var result: ToolResult? = null
            while (System.currentTimeMillis() < deadline) {
                val text = _output.value
                val idx = text.indexOf(marker, startIndex = before)
                if (idx >= 0) {
                    val afterColon = text.substring(idx + marker.length + 1)
                    val codeStr = afterColon.takeWhile { it.isDigit() }
                    if (codeStr.isNotEmpty()) {
                        val code = codeStr.toIntOrNull() ?: 0
                        val raw = text.substring(before, idx).trim('\n')
                        // Hide the marker line from the terminal log.
                        _output.value = text.replaceFirst("$marker:$codeStr", "")
                        result = ToolResult(code == 0, raw.ifBlank { "(no output, exit $code)" })
                        break
                    }
                }
                if (!isAlive()) {
                    result = ToolResult(false, text.substring(before).trim().ifBlank { "Shell ended." })
                    break
                }
                delay(100)
            }
            if (result == null) {
                // The command is stuck (e.g. waiting for input). The shared
                // shell cannot take another command, so restart the session.
                restart()
                result = ToolResult(false, "Command timed out after ${timeoutSec}s: $cmd (session restarted)")
            }
            result
        }

    fun clear() {
        _output.value = "BA Terminal — our own terminal, built into BA TERMINAL.\n"
    }

    fun restart() {
        scope.launch {
            synchronized(lock) {
                try {
                    process?.destroyForcibly()
                } catch (_: Exception) {
                }
                process = null
            }
            _cwd.value = if (inLinux) linux.guestWorkspace else workspace.root.absolutePath
            _output.value = "BA Terminal — new shell started.\n"
            start()
        }
    }

    /** `cd` with no target goes to the workspace, so the shell and the label agree. */
    private fun rewriteBareCd(command: String): String {
        val t = command.trim()
        return if (t == "cd" || t == "cd ~") {
            if (inLinux) "cd ${linux.guestWorkspace}" else "cd ${workspace.root.absolutePath}"
        } else command
    }

    /** Track a leading `cd ...` so the prompt label follows the shell. */
    private fun trackCd(command: String) {
        val first = command.trim().split(";", "&&", "||").firstOrNull()?.trim() ?: return
        if (first != "cd" && !first.startsWith("cd ")) return
        val arg = first.removePrefix("cd").trim()
        if (arg.isEmpty() || arg == "~") {
            _cwd.value = if (inLinux) linux.guestWorkspace else workspace.root.absolutePath
            return
        }
        if (arg == "-") return // Shell handles OLDPWD; leave the label as-is.
        if (inLinux) {
            // Guest paths are lexical only — never touch the host filesystem.
            _cwd.value = resolveGuestPath(_cwd.value, arg)
            return
        }
        val f = if (arg.startsWith("/")) File(arg) else File(_cwd.value, arg)
        try {
            val target = f.canonicalPath
            if (File(target).isDirectory) _cwd.value = target
        } catch (_: Exception) {
        }
    }

    /** Resolve a guest path lexically (no host filesystem access). */
    private fun resolveGuestPath(cwd: String, arg: String): String {
        val parts = mutableListOf<String>()
        val base = if (arg.startsWith("/")) "" else cwd
        for (seg in (base + "/" + arg).split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
        }
        return "/" + parts.joinToString("/")
    }

    private fun writeLine(line: String) {
        synchronized(lock) {
            try {
                val out = process?.outputStream ?: return
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
                appendOutput("\n(shell is not accepting input)\n")
            }
        }
    }

    private fun appendOutput(chunk: String) {
        val clean = ansi.replace(chunk, "").replace("\r\n", "\n").replace("\r", "\n")
        if (clean.isEmpty()) return
        val next = _output.value + clean
        _output.value = if (next.length > maxOutput) next.takeLast(maxOutput) else next
    }
}
