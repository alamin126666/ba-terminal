package com.ba.terminal.domain

import android.content.Context
import com.ba.terminal.TarGz
import com.ba.terminal.data.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * BA Linux — our own full Linux environment, built into this app.
 *
 * How it works (the same idea Termux was built on, but ours):
 * - An Alpine Linux rootfs (small, open-source) ships inside the APK.
 * - On first use it is unpacked into the app's private storage.
 * - PRoot (open-source, built from source in our CI) runs the rootfs as
 *   a user-mode sandbox — no root, no mount, no other app.
 * - Everything runs on THIS phone's CPU and RAM. No VPS, no server.
 * - Inside, `apk` is the package manager: `apk add python3 nodejs git`
 *   installs real tools, exactly like Termux's `pkg`.
 *
 * On a device where the sandbox cannot run (anything but 64-bit ARM),
 * the terminal falls back to the plain system shell.
 */
class BaLinux(private val context: Context, private val workspace: WorkspaceStore) {
    val rootfsDir = File(context.filesDir, "ba-linux/rootfs")
    val homeDir = File(context.filesDir, "ba-linux/home")
    private val readyMarker = File(rootfsDir, ".ba-linux-ready")

    private val _state = MutableStateFlow("BA Linux not set up yet")
    val state: StateFlow<String> = _state.asStateFlow()

    private val setupMutex = Mutex()
    @Volatile
    var ready = false
        private set

    /** Guest path of the app workspace inside the sandbox. */
    val guestWorkspace = "/workspace"

    private val prootFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    private val loaderFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")

    /** True only when the sandbox binaries shipped with the APK. */
    fun isSupported(): Boolean = prootFile.exists() && loaderFile.exists()

    /**
     * Make the sandbox ready (first run: unpack the rootfs, then
     * `apk update` once). Safe to call repeatedly; only the first call
     * does the work. Returns true when the shell can run inside BA Linux.
     */
    suspend fun ensureReady(log: (String) -> Unit): Boolean = setupMutex.withLock {
        if (ready) return true
        if (!isSupported()) {
            _state.value = "BA Linux needs a 64-bit ARM phone — using the system shell instead."
            log("\nBA Linux is not available on this device (needs 64-bit ARM).\nFalling back to the system shell.\n")
            return false
        }
        if (readyMarker.exists() && File(rootfsDir, "bin/sh").exists()) {
            writePkgShim()
            writeBaHelpers()
            ready = true
            _state.value = "BA Linux ready (Alpine)"
            return true
        }
        return try {
            log("\nSetting up BA Linux (first run) — unpacking Alpine Linux…\n")
            _state.value = "Setting up BA Linux… (unpacking Alpine)"
            rootfsDir.mkdirs()
            homeDir.mkdirs()
            context.assets.open("linux/alpine-aarch64.bin").use { input ->
                TarGz.extract(input, rootfsDir)
            }
            // Minimal network config so apk and friends can reach the internet.
            File(rootfsDir, "etc/resolv.conf").apply {
                parentFile?.mkdirs()
                writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            File(rootfsDir, "etc/hosts").apply {
                writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
            File(rootfsDir, "workspace").mkdirs()
            File(rootfsDir, "root").mkdirs()
            writePkgShim()
            writeBaHelpers()
            readyMarker.createNewFile()
            ready = true
            _state.value = "BA Linux ready (Alpine)"
            log("BA Linux ready — Alpine Linux on this phone.\nUpdating package lists (apk update)…\n")
            // Best effort: a failed update does not block the shell.
            val (ok, out) = runGuestCommand("apk update", 120)
            if (ok) {
                log("Package lists updated. Installing base tools (python, git, curl, vim, nano, ssh, node)…\n")
                val (ok2, _) = runGuestCommand("apk add busybox-extras coreutils bash curl wget git vim nano openssh-client python3 py3-pip nodejs npm ca-certificates tzdata", 300)
                if (ok2) log("Base tools ready. Try: ba-help\n") else log("Some base tools did not install — try `pkg install python3 git` by hand.\n")
                log("Install anything else with: pkg install <name>  (try: pkg search <name>)\n")
            }
            else log("apk update did not finish cleanly — try `apk update` by hand later.\n$out\n")
            true
        } catch (e: Exception) {
            _state.value = "BA Linux setup failed — using the system shell."
            log("\nBA Linux setup failed (${e.message}). Falling back to the system shell.\n")
            false
        }
    }

    /**
     * Termux-style `pkg` command inside the sandbox, so Termux habits
     * work: `pkg update`, `pkg install python`, `pkg upgrade`, `pkg
     * search`. It is just a wrapper around Alpine's `apk` — it strips
     * `-y` (apk never asks) and maps `python` to `python3`. Written on
     * every setup, so phones that already unpacked the rootfs get it too.
     */
    private fun writePkgShim() {
        try {
            val pkg = File(rootfsDir, "usr/local/bin/pkg")
            pkg.parentFile?.mkdirs()
            pkg.writeText(PKG_SCRIPT)
            try {
                Files.setPosixFilePermissions(
                    pkg.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                    )
                )
            } catch (_: Exception) {
                pkg.setExecutable(true)
            }
        } catch (_: Exception) {
            // A missing pkg shim must never block the shell.
        }
    }


    /** Extra guest helpers: `ba-help` + a shell profile hint. */
    private fun writeBaHelpers() {
        try {
            val help = File(rootfsDir, "usr/local/bin/ba-help")
            help.parentFile?.mkdirs()
            help.writeText(BA_HELP_SCRIPT)
            help.setExecutable(true)
            val profile = File(rootfsDir, "etc/profile.d/ba-help.sh")
            profile.parentFile?.mkdirs()
            profile.writeText("alias ba-help='/usr/local/bin/ba-help'\n")
        } catch (_: Exception) {
        }
    }

    /** The full command line that starts the guest shell. */
    fun prootCommand(): List<String> = listOf(
        prootFile.absolutePath,
        "-0",
        "-r", rootfsDir.absolutePath,
        "-b", "${workspace.root.absolutePath}:/workspace",
        "-b", "${homeDir.absolutePath}:/root",
        "-b", "/dev",
        "-b", "/dev/pts",
        "-b", "/proc",
        "-b", "/sys",
        "-w", "/workspace",
        "--link2symlink",
        "--kill-on-exit",
        "/bin/sh"
    )

    /** Environment for the guest shell. */
    fun guestEnv(): Map<String, String> = mapOf(
        "PROOT_LOADER" to loaderFile.absolutePath,
        "HOME" to "/root",
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8",
        "TMPDIR" to "/tmp",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin"
    )

    /** Run one guest command (used for the first-run `apk update`). */
    private suspend fun runGuestCommand(command: String, timeoutSec: Long): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(prootCommand() + listOf("-c", command))
                pb.environment().putAll(guestEnv())
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    return@withContext false to "(timed out)"
                }
                val out = proc.inputStream.bufferedReader().readText().takeLast(2000)
                (proc.exitValue() == 0) to out
            } catch (e: Exception) {
                false to (e.message ?: "error")
            }
        }

    companion object {
        private const val BA_HELP_SCRIPT = """#!/bin/sh
# ba-help — the 40 most common commands and how to get them in BA Linux.
echo "BA TERMINAL — all commands work like Termux. If one is missing:"
echo "  pkg search <name>   then   pkg install <name>"
echo ""
echo "Shell: ls cd pwd cat echo mkdir rm cp mv touch grep find sed awk chmod"
echo "Text:  nano vim less head tail sort uniq wc"
echo "Net:   curl wget ssh scp ping (pkg install iputils)"
echo "Code:  python3 (pkg install python3)  node npm (pkg install nodejs)"
echo "       git gcc go rust (pkg install git gcc go rust)"
echo "       pip3 (pkg install py3-pip)"
echo "Files: tar unzip zip (pkg install tar unzip zip)"
echo ""
echo "Examples:"
echo "  pkg update && pkg upgrade -y"
echo "  pkg install python3 git nodejs"
echo "  python3 --version ; git --version ; node --version"
"""
        // In a Kotlin raw string, `$` followed by a letter starts an
        // interpolation, so shell variables use ${'$'} below.
        private const val PKG_SCRIPT = """#!/bin/sh
# pkg — Termux-style wrapper around Alpine's apk, for BA Linux.
# Lets Termux habits work: pkg update / pkg install / pkg upgrade / pkg search.
# Strips -y/--yes (apk never asks) and maps python -> python3.
filtered=""
for a in "$@"; do
  case "${'$'}a" in
    -y|--yes) ;;
    python) filtered="${'$'}filtered python3" ;;
    pip) filtered="${'$'}filtered pip3" ;;
    *) filtered="${'$'}filtered ${'$'}a" ;;
  esac
done
set -- ${'$'}filtered
cmd="$1"
[ $# -gt 0 ] && shift
case "${'$'}cmd" in
  update) exec apk update ;;
  upgrade) exec apk upgrade "$@" ;;
  install) exec apk add "$@" ;;
  uninstall|remove) exec apk del "$@" ;;
  search) exec apk search "$@" ;;
  list-installed|list) exec apk info ;;
  list-all) exec apk search "$@" ;;
  show) exec apk info "$@" ;;
  *) echo "pkg: unknown command '${'$'}cmd'"; echo "Try: pkg update, pkg install <name>, pkg upgrade, pkg search <name>"; exit 1 ;;
esac
"""
    }
}
