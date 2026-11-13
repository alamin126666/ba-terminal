package com.ba.terminal

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * Minimal tar extractor (no extra dependency). Accepts a .tar.gz or a
 * plain .tar stream — the format is detected from the magic bytes, so
 * it still works if the packager already decompressed the archive.
 * Handles regular files, directories, symlinks and hardlinks; skips
 * device nodes and anything else. Rejects entries that would escape
 * the destination directory.
 */
object TarGz {
    fun extract(archive: InputStream, destDir: File) {
        val buffered = if (archive is BufferedInputStream) archive else BufferedInputStream(archive)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val tar: InputStream = if (b0 == 0x1F && b1 == 0x8B) GZIPInputStream(buffered) else buffered
        tar.use { input ->
            var pendingName: String? = null
            var pendingLink: String? = null
            while (true) {
                val header = ByteArray(512)
                if (!readFully(input, header)) return
                if (header.all { it == 0.toByte() }) {
                    // End of archive is two zero blocks; one is enough to stop.
                    return
                }
                val name = parseName(header)
                val size = parseOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val linkName = parseString(header, 157, 100)
                val mode = parseOctal(header, 100, 8)
                val dataBlocks = ((size + 511) / 512).toInt()
                val data = ByteArray(size.toInt().coerceAtLeast(0))
                if (size > 0) readFully(input, data)
                // Skip padding to the next 512-byte boundary.
                val pad = dataBlocks * 512 - size
                if (pad > 0) skipFully(input, pad)

                when (type) {
                    'L' -> { pendingName = String(data, Charsets.UTF_8).trimEnd('\u0000') }
                    'K' -> { pendingLink = String(data, Charsets.UTF_8).trimEnd('\u0000') }
                    '5' -> {
                        val dir = resolve(destDir, pendingName ?: name) ?: continue
                        dir.mkdirs()
                        applyMode(dir, mode)
                    }
                    '0', '\u0000' -> {
                        val file = resolve(destDir, pendingName ?: name) ?: continue
                        file.parentFile?.mkdirs()
                        file.writeBytes(data)
                        applyMode(file, mode)
                    }
                    '2' -> {
                        val link = resolve(destDir, pendingName ?: name) ?: continue
                        link.parentFile?.mkdirs()
                        if (link.exists() || Files.isSymbolicLink(link.toPath())) link.delete()
                        Files.createSymbolicLink(link.toPath(), File(pendingLink ?: linkName).toPath())
                    }
                    '1' -> {
                        val target = resolve(destDir, pendingLink ?: linkName)
                        val file = resolve(destDir, pendingName ?: name)
                        if (target != null && file != null && target.exists()) {
                            file.parentFile?.mkdirs()
                            target.copyTo(file, overwrite = true)
                            applyMode(file, mode)
                        }
                    }
                    else -> { /* device nodes, fifos, etc. — skip */ }
                }
                if (type != 'L') pendingName = null
                if (type != 'K') pendingLink = null
            }
        }
    }

    private fun resolve(destDir: File, entryName: String): File? {
        // Guard against path traversal: the entry must stay inside destDir.
        val clean = entryName.trimStart('/').replace('\\', '/')
        val parts = clean.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return File(destDir, parts.joinToString("/"))
    }

    private fun parseName(header: ByteArray): String {
        val prefix = parseString(header, 345, 155)
        val name = parseString(header, 0, 100)
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun parseString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: (offset + length)
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        val s = parseString(header, offset, length).trim()
        if (s.isEmpty()) return 0
        return s.toLongOrNull(8) ?: 0
    }

    private fun applyMode(file: File, mode: Long) {
        if (mode == 0L) return
        try {
            val perms = mutableSetOf<PosixFilePermission>()
            if (mode and 0x100L != 0L) perms += PosixFilePermission.OWNER_READ
            if (mode and 0x80L != 0L) perms += PosixFilePermission.OWNER_WRITE
            if (mode and 0x40L != 0L) perms += PosixFilePermission.OWNER_EXECUTE
            if (mode and 0x20L != 0L) perms += PosixFilePermission.GROUP_READ
            if (mode and 0x10L != 0L) perms += PosixFilePermission.GROUP_WRITE
            if (mode and 0x8L != 0L) perms += PosixFilePermission.GROUP_EXECUTE
            if (mode and 0x4L != 0L) perms += PosixFilePermission.OTHERS_READ
            if (mode and 0x2L != 0L) perms += PosixFilePermission.OTHERS_WRITE
            if (mode and 0x1L != 0L) perms += PosixFilePermission.OTHERS_EXECUTE
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch (_: Exception) {
            // Fallback for filesystems without POSIX permissions.
            if (mode and 0x40L != 0L) file.setExecutable(true)
            if (mode and 0x100L != 0L) file.setReadable(true)
            if (mode and 0x80L != 0L) file.setWritable(true)
        }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) return
            remaining -= n
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off > 0
            off += n
        }
        return true
    }
}
