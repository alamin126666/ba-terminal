# BA TERMINAL — our own Termux, no server, no VPS

BA TERMINAL is a standalone Android terminal app (Kotlin + Jetpack Compose).
It runs a full Alpine Linux environment on the phone's own CPU and RAM,
via PRoot — no root, no VPS, no other app. First run unpacks Alpine,
then `pkg` / `apk` installs real tools.

- Package: `com.ba.terminal`
- Version: 1.0.0 (code 1)
- UI: Option 3 — Minimal Glass Clean (dark charcoal, frosted glass,
  soft white + light blue, Material 3)
- Pages: Home, Terminal (multi-tab), Files, Store, Settings,
  Snippets, Editor

## All commands — honest note

No terminal ships *every* command preinstalled, Termux included.
What BA TERMINAL does, exactly like Termux:

- Core shell commands work out of the box: `ls cd pwd cat echo
  mkdir rm cp mv touch grep find sed awk chmod`, pipes `|`,
  redirection `> >>`, `&& || ;`, history, env vars.
- Base pack installed on first setup (best effort): busybox-extras,
  coreutils, bash, curl, wget, git, vim, nano, openssh-client,
  python3, py3-pip, nodejs, npm, ca-certificates, tzdata.
- Anything else in the Alpine apk repository (~13,000 packages):
  `pkg search <name>` then `pkg install <name>`.
  Examples: `pkg install python3`, `pkg install nodejs`,
  `pkg install go`, `pkg install rust`, `pkg install gcc`.
- `ba-help` lists the 40 most common commands and their install line.
- If a command is missing, the terminal tells you the exact
  `pkg install` line instead of just "not found".

Commands that cannot work here (same as Termux, by design):
systemd services, kernel modules, and anything that needs real
root / a different CPU architecture. BA Linux needs 64-bit ARM;
other phones fall back to the plain Android system shell.

## Build

Local Gradle build is blocked in the sandbox (daemon cannot connect
over loopback — same as BA BLAST GRAM / BA AI AGENT). Build via
GitHub Actions: push to `main`, download the `ba-terminal-apk`
artifact. Release APK is signed with the debug key so it installs.

## Test on phone

1. Uninstall any old BA TERMINAL first, then install the APK.
2. Open Terminal — first run shows "Setting up BA Linux…".
3. `cat /etc/os-release` — should say Alpine Linux.
4. `pkg update && pkg upgrade -y`
5. `pkg install python git` then `python3 --version` and `git --version`.
6. `ba-help` to see the command list.

## Credits

Alpine Linux (MIT-style), PRoot (GPL-2.0, OpenMinis fork),
Termux-built ELF loaders (open-source PRoot, sha256-pinned in
scripts/build-proot.sh). See THIRD_PARTY_NOTICES.md.
