package com.ba.terminal

import com.ba.terminal.TarGz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPOutputStream

/**
 * Verifies the minimal .tar.gz extractor used to unpack the BA Linux
 * (Alpine) rootfs on first run.
 */
class TarGzTest {

    @Test
    fun extractsFilesDirsSymlinksAndModes() {
        val gz = buildTarGz(
            TarEntry.Dir("etc"),
            TarEntry.File("etc/hello.txt", "hello ba\n".toByteArray(), 0x1A4), // 0644
            TarEntry.File("bin/run.sh", "#!/bin/sh\n".toByteArray(), 0x1ED), // 0755
            TarEntry.Symlink("bin/link", "run.sh"),
            TarEntry.File("../evil.txt", "nope".toByteArray(), 0x1A4) // must be rejected
        )
        val dest = Files.createTempDirectory("targz-test").toFile()
        try {
            TarGz.extract(ByteArrayInputStream(gz), dest)
            assertExtracted(dest)
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun extractsPlainTarToo() {
        // The APK packager may decompress a .tar.gz asset into a plain
        // .tar, so the extractor must handle both.
        val tar = buildTar(
            TarEntry.Dir("etc"),
            TarEntry.File("etc/hello.txt", "hello ba\n".toByteArray(), 0x1A4),
            TarEntry.File("bin/run.sh", "#!/bin/sh\n".toByteArray(), 0x1ED),
            TarEntry.Symlink("bin/link", "run.sh"),
            TarEntry.File("../evil.txt", "nope".toByteArray(), 0x1A4)
        )
        val dest = Files.createTempDirectory("tar-test").toFile()
        try {
            TarGz.extract(ByteArrayInputStream(tar), dest)
            assertExtracted(dest)
        } finally {
            dest.deleteRecursively()
        }
    }

    private fun assertExtracted(dest: File) {
        assertEquals("hello ba\n", File(dest, "etc/hello.txt").readText())
        assertEquals("#!/bin/sh\n", File(dest, "bin/run.sh").readText())
        val runPerms = Files.getPosixFilePermissions(File(dest, "bin/run.sh").toPath())
        assertTrue("run.sh should be owner-executable", runPerms.contains(PosixFilePermission.OWNER_EXECUTE))
        assertFalse("run.sh must not be world-writable", runPerms.contains(PosixFilePermission.OTHERS_WRITE))
        assertTrue(Files.isSymbolicLink(File(dest, "bin/link").toPath()))
        assertEquals("run.sh", Files.readSymbolicLink(File(dest, "bin/link").toPath()).toString())
        assertFalse("path traversal entry must not escape", File(dest, "evil.txt").exists())
        assertFalse("path traversal entry must not escape", File(dest.parentFile, "evil.txt").exists())
    }

    private sealed interface TarEntry {
        data class Dir(val name: String) : TarEntry
        data class File(val name: String, val data: ByteArray, val mode: Int) : TarEntry
        data class Symlink(val name: String, val target: String) : TarEntry
    }

    /** Builds a small ustar .tar.gz in memory for the test. */
    private fun buildTarGz(vararg entries: TarEntry): ByteArray {
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(buildTar(*entries)) }
        return gz.toByteArray()
    }

    /** Builds a small ustar .tar (no gzip) in memory for the test. */
    private fun buildTar(vararg entries: TarEntry): ByteArray {
        val tar = ByteArrayOutputStream()
        for (e in entries) {
            when (e) {
                is TarEntry.Dir -> tar.write(header(e.name, 0, '5', 0x1ED, ""))
                is TarEntry.File -> {
                    tar.write(header(e.name, e.data.size.toLong(), '0', e.mode, ""))
                    tar.write(e.data)
                    pad(tar, e.data.size.toLong())
                }
                is TarEntry.Symlink -> tar.write(header(e.name, 0, '2', 0x1FF, e.target))
            }
        }
        tar.write(ByteArray(1024)) // end-of-archive zero blocks
        return tar.toByteArray()
    }

    private fun header(name: String, size: Long, type: Char, mode: Int, link: String): ByteArray {
        val h = ByteArray(512)
        fun put(off: Int, len: Int, s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            System.arraycopy(b, 0, h, off, minOf(b.size, len))
        }
        put(0, 100, name)
        put(100, 8, "%07o\u0000".format(mode))
        put(108, 8, "0000000\u0000")
        put(116, 8, "0000000\u0000")
        put(124, 12, "%011o\u0000".format(size))
        put(136, 12, "00000000000\u0000")
        h[156] = type.code.toByte()
        put(157, 100, link)
        put(257, 6, "ustar\u0000")
        put(263, 2, "00")
        // checksum: spaces in the checksum field, sum of all header bytes.
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        var sum = 0
        for (b in h) sum += b.toInt() and 0xFF
        put(148, 8, "%06o\u0000 ".format(sum))
        return h
    }

    private fun pad(out: ByteArrayOutputStream, size: Long) {
        val rem = ((size + 511) / 512 * 512 - size).toInt()
        if (rem > 0) out.write(ByteArray(rem))
    }
}
