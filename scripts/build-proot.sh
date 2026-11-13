#!/bin/bash
# ============================================================================
# Build PRoot for Android (BA Linux — our own Termux-like environment)
# ============================================================================
# Cross-compiles a statically-linked libtalloc and the PRoot fork for
# Android aarch64 using the Android NDK, then installs the proot binary
# into the app's jniLibs and downloads the vendored Termux-built ELF
# loaders (sha256-pinned) that the fork-built loaders cannot replace.
#
# Why the loaders are vendored: proot needs two ELF loaders to execute
# guest binaries, and Android 10+ W^X means only files in nativeLibraryDir
# (populated from lib/**/*.so in the APK) are executable. The fork-built
# loaders lack Termux's Android-specific loader patches and SEGV the
# guest's first instruction on some devices; the Termux-built pair works
# fleet-wide. They are open-source PRoot (GPL-2.0), baked into OUR app at
# build time — no Termux app is involved at runtime.
#
# Recipe adapted from OpenMinis' deps/build_proot.sh (same pins where it
# matters: talloc 2.4.2 single-file build, NDK clang API 26, loader hashes).
#
# Prerequisites: Android NDK r28+ (set $ANDROID_NDK_HOME, or the script
# finds it under $ANDROID_HOME/ndk), curl, tar, make.
#
# Usage: ./scripts/build-proot.sh
# Output: app/src/main/jniLibs/arm64-v8a/{libproot.so,libproot-loader.so,libproot-loader32.so}
# ============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="$SCRIPT_DIR/build-proot"
PROOT_DIR="$WORK_DIR/proot"
TALLOC_DIR="$WORK_DIR/talloc"
BUILD_DIR="$WORK_DIR/build"
JNILIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"

# Pinned sources — change a pin only deliberately, never by accident.
PROOT_REPO="https://github.com/OpenMinis/proot"
PROOT_COMMIT="2ca3bae3650988c0e098b4bb7686b79617b64194"
TALLOC_VERSION="2.4.2"
TALLOC_TARBALL_URL="https://download.samba.org/pub/talloc/talloc-${TALLOC_VERSION}.tar.gz"
LOADER_COMMIT="1a71df0f381cefde80bf912b723de6f9831bc177"
LOADER_BASE="https://raw.githubusercontent.com/OpenMinis/OpenMinis/${LOADER_COMMIT}/src/android/app/src/main/jniLibs/arm64-v8a"
LOADER64_SHA="44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04"
LOADER32_SHA="25f6bd90bc5a3d3088026289a0d3eaf3e502bd2b00e5cb74fadd9791132efa34"

ANDROID_API=26
NDK_TRIPLE="aarch64-linux-android"

log() { echo "[build-proot] $1"; }
fail() { echo "[build-proot] ERROR: $1" >&2; exit 1; }

resolve_ndk() {
    for cand in "$ANDROID_NDK_HOME" "$ANDROID_NDK_ROOT"; do
        if [ -n "$cand" ] && [ -d "$cand" ]; then echo "$cand"; return; fi
    done
    for base in "$ANDROID_HOME/ndk" "$ANDROID_SDK_ROOT/ndk" "$HOME/Android/Sdk/ndk" "$HOME/Library/Android/sdk/ndk"; do
        if [ -d "$base" ]; then
            local latest
            latest=$(ls "$base" 2>/dev/null | sort -V | tail -n 1)
            if [ -n "$latest" ]; then echo "$base/$latest"; return; fi
        fi
    done
    fail "Android NDK not found. Set \$ANDROID_NDK_HOME."
}

setup_toolchain() {
    NDK_HOME="$(resolve_ndk)"
    log "Using NDK: $NDK_HOME"
    TOOLCHAIN_BIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
    [ -d "$TOOLCHAIN_BIN" ] || fail "Toolchain dir missing: $TOOLCHAIN_BIN"
    CC="$TOOLCHAIN_BIN/${NDK_TRIPLE}${ANDROID_API}-clang"
    AR="$TOOLCHAIN_BIN/llvm-ar"
    STRIP="$TOOLCHAIN_BIN/llvm-strip"
    OBJCOPY="$TOOLCHAIN_BIN/llvm-objcopy"
    OBJDUMP="$TOOLCHAIN_BIN/llvm-objdump"
    RANLIB="$TOOLCHAIN_BIN/llvm-ranlib"
    for tool in "$CC" "$AR" "$STRIP" "$OBJCOPY" "$OBJDUMP" "$RANLIB"; do
        [ -x "$tool" ] || fail "Missing toolchain binary: $tool"
    done
    log "Clang: $CC"
}

fetch_talloc() {
    if [ -f "$TALLOC_DIR/talloc.c" ] && [ -f "$TALLOC_DIR/talloc.h" ]; then
        log "talloc source already present, skipping download"
        return
    fi
    log "Downloading talloc $TALLOC_VERSION..."
    mkdir -p "$TALLOC_DIR" "$BUILD_DIR"
    local tarball="$BUILD_DIR/talloc-${TALLOC_VERSION}.tar.gz"
    [ -f "$tarball" ] || curl -fsSL "$TALLOC_TARBALL_URL" -o "$tarball"
    local tmp
    tmp=$(mktemp -d)
    tar xzf "$tarball" -C "$tmp"
    cp "$tmp/talloc-${TALLOC_VERSION}/talloc.c" "$TALLOC_DIR/"
    cp "$tmp/talloc-${TALLOC_VERSION}/talloc.h" "$TALLOC_DIR/"
    rm -rf "$tmp"
    # talloc.c expects Samba's `replace.h` — a minimal standalone shim so it
    # builds against bionic without the rest of Samba.
    cat > "$TALLOC_DIR/replace.h" <<'EOF'
#ifndef REPLACE_H
#define REPLACE_H

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/auxv.h>

#define TALLOC_BUILD_VERSION_MAJOR   2
#define TALLOC_BUILD_VERSION_MINOR   4
#define TALLOC_BUILD_VERSION_RELEASE 2

#define HAVE_SYS_AUXV_H 1
#define HAVE_INTPTR_T 1
#define HAVE_VA_COPY 1

#define VALGRIND_MAKE_MEM_UNDEFINED(p, n) do { (void)(p); (void)(n); } while (0)
#define VALGRIND_MAKE_MEM_DEFINED(p, n)   do { (void)(p); (void)(n); } while (0)
#define VALGRIND_MAKE_MEM_NOACCESS(p, n)  do { (void)(p); (void)(n); } while (0)

#ifndef ZERO_STRUCT
#define ZERO_STRUCT(x) memset((char *)&(x), 0, sizeof(x))
#endif

#ifndef discard_const
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))
#endif

#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif

#define HAVE_CONSTRUCTOR_ATTRIBUTE 1

#endif /* REPLACE_H */
EOF
    log "talloc $TALLOC_VERSION unpacked"
}

build_talloc() {
    local out="$BUILD_DIR/libtalloc.a"
    if [ -f "$out" ]; then log "libtalloc.a already built, skipping"; return; fi
    log "Compiling libtalloc.a for android-$ANDROID_API (arm64-v8a)..."
    mkdir -p "$BUILD_DIR/talloc-obj"
    "$CC" -c "$TALLOC_DIR/talloc.c" \
        -o "$BUILD_DIR/talloc-obj/talloc.o" \
        -I"$TALLOC_DIR" \
        -fPIC -O2 -Wall -std=gnu99 \
        -DHAVE_STDARG_H=1 -DHAVE_VA_COPY=1 -DHAVE_UNISTD_H=1 -DHAVE_INTPTR_T=1
    "$AR" rcs "$out" "$BUILD_DIR/talloc-obj/talloc.o"
    "$RANLIB" "$out"
    log "libtalloc.a built ($(du -h "$out" | awk '{print $1}'))"
}

fetch_proot() {
    if [ -d "$PROOT_DIR/src" ]; then
        log "proot source already present, skipping clone"
        return
    fi
    log "Cloning proot fork @ $PROOT_COMMIT..."
    mkdir -p "$WORK_DIR"
    git clone --depth 1 "$PROOT_REPO" "$PROOT_DIR"
    (cd "$PROOT_DIR" && git fetch --depth 1 origin "$PROOT_COMMIT" && git checkout "$PROOT_COMMIT")
    log "proot source ready"
}

build_proot() {
    [ -d "$PROOT_DIR/src" ] || fail "PRoot source missing at $PROOT_DIR"
    log "Building proot (aarch64)..."
    local cppflags="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -DARG_MAX=131072 -I$TALLOC_DIR"
    local cflags="-O2 -Wall -Wextra -fPIE"
    local ldflags="-Wl,-z,noexecstack -pie -L$BUILD_DIR -ltalloc"
    (
        cd "$PROOT_DIR/src"
        make \
            CC="$CC" \
            STRIP="$STRIP" \
            OBJCOPY="$OBJCOPY" \
            OBJDUMP="$OBJDUMP" \
            CPPFLAGS="$cppflags" \
            CFLAGS="$cflags" \
            LDFLAGS="$ldflags" \
            -j"$(nproc)"
    )
    local built="$PROOT_DIR/src/proot"
    [ -f "$built" ] || fail "Build finished but $built is missing"
    "$STRIP" "$built"
    "$OBJDUMP" -a "$built" | grep -q 'aarch64' || fail "Output is not aarch64 ELF"
    log "proot built: $built ($(du -h "$built" | awk '{print $1}'))"
    BUILT_PROOT="$built"
}

fetch_loaders() {
    log "Downloading vendored Termux-built loaders (sha256-pinned)..."
    mkdir -p "$JNILIBS_DIR"
    local f64="$JNILIBS_DIR/libproot-loader.so"
    local f32="$JNILIBS_DIR/libproot-loader32.so"
    curl -fsSL "$LOADER_BASE/libproot-loader.so" -o "$f64"
    curl -fsSL "$LOADER_BASE/libproot-loader32.so" -o "$f32"
    local have64 have32
    have64=$(sha256sum "$f64" | awk '{print $1}')
    have32=$(sha256sum "$f32" | awk '{print $1}')
    [ "$have64" = "$LOADER64_SHA" ] || fail "libproot-loader.so sha256 mismatch: $have64"
    [ "$have32" = "$LOADER32_SHA" ] || fail "libproot-loader32.so sha256 mismatch: $have32"
    log "Loaders verified (sha256-pinned)"
}

install_proot() {
    [ -f "$BUILT_PROOT" ] || fail "No proot binary to install"
    mkdir -p "$JNILIBS_DIR"
    install -m 0755 "$BUILT_PROOT" "$JNILIBS_DIR/libproot.so"
    log "Installed: $JNILIBS_DIR/libproot.so"
}

verify_artifacts() {
    log "Verifying installed artifacts..."
    local failed=0
    for entry in \
        "$JNILIBS_DIR/libproot.so:100000:aarch64" \
        "$JNILIBS_DIR/libproot-loader.so:4000:aarch64" \
        "$JNILIBS_DIR/libproot-loader32.so:2000:elf32-littlearm"; do
        local path="${entry%%:*}"
        local rest="${entry#*:}"
        local min="${rest%%:*}"
        local arch="${rest#*:}"
        local name="${path##*/}"
        if [ ! -f "$path" ]; then log "MISSING: $path"; failed=1; continue; fi
        local size
        size=$(wc -c < "$path" | tr -d ' ')
        if [ "$size" -lt "$min" ]; then log "TOO SMALL: $name is $size bytes (expected >= $min)"; failed=1; continue; fi
        if ! "$OBJDUMP" -a "$path" 2>/dev/null | grep -q "$arch"; then log "WRONG ARCH: $name is not $arch"; failed=1; continue; fi
        log "  ok $name ($size bytes)"
    done
    [ "$failed" -eq 0 ] || fail "Artifact verification failed — the APK would ship a broken sandbox."
    log "All sandbox artifacts present and valid"
}

setup_toolchain
fetch_talloc
build_talloc
fetch_proot
build_proot
install_proot
fetch_loaders
verify_artifacts
log "All done. proot ready in $JNILIBS_DIR"
