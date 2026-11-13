# Third-party notices — BA TERMINAL (BA Linux)

BA TERMINAL's own code is the user's. The BA Linux sandbox is built
from the following open-source components, all fetched or built from
their public sources at build time (see `scripts/build-proot.sh` and
`.github/workflows/build-apk.yml`). Nothing from another *app* is used
at runtime — no Termux app, no VPS, no server.

## PRoot — GPL-2.0-or-later

User-mode `chroot` that runs the Alpine rootfs without root.
- Source (fork): https://github.com/OpenMinis/proot
  (fork of https://github.com/termux/proot), pinned commit
  `2ca3bae3650988c0e098b4bb7686b79617b64194`
- Built from source in CI with the Android NDK (see
  `scripts/build-proot.sh`); the built binary is NOT linked into the
  app — it runs as a separate executable the app starts.
- The two ELF loaders (`libproot-loader.so`,
  `libproot-loader32.so`) are the Termux project's builds of the same
  PRoot source, downloaded at build time from the OpenMinis repo
  (commit `1a71df0f381cefde80bf912b723de6f9831bc177`) and verified by
  SHA-256 in the build script. The fork-built loaders lack Termux's
  Android-specific loader patches and crash the guest on some
  devices, which is why the Termux-built pair is used.
- License: GPL-2.0-or-later. Source offer: the pinned commits above
  plus `scripts/build-proot.sh` reproduce the exact binaries.

## talloc — GPL-3.0-or-later

Memory-pool library linked into PRoot (single-file build, avoids
Samba's waf build system).
- Source: https://download.samba.org/pub/talloc/talloc-2.4.2.tar.gz
- License: GPL-3.0-or-later.

## Alpine Linux minirootfs — various open-source licenses

The Linux userland that BA Linux runs (shell, core tools, `apk`).
- Source: https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.4-aarch64.tar.gz
  (SHA-256 `4b14c18f7e90a063b61462ea1462a5732c154bf6adad594974fe6487cd98a8de`,
  verified in the CI workflow)
- License: the rootfs is a collection of Alpine packages under their
  own licenses (mostly MIT, BSD, GPL). See
  https://alpinelinux.org/ for the distribution's licensing.
