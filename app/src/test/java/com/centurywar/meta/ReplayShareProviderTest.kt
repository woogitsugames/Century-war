package com.centurywar.meta

import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplayShareProviderTest {
    @Test fun onlyReadOnlyReplayFilesAreExposed() {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.filesDir, "replays").apply { mkdirs() }
        val allowed = File(directory, "share73.cwr").apply { writeBytes(ByteArray(24) { 1 }) }
        val profile = File(app.filesDir, "profile.json").apply { writeText("private") }
        val provider = ReplayShareProvider().apply {
            attachInfo(app, ProviderInfo().apply {
                authority = app.packageName + ".replays"
                exported = false
                grantUriPermissions = true
            })
        }
        val uri = ReplayShareProvider.uriFor(app, "share73")
        assertEquals(ReplayShareProvider.MIME, provider.getType(uri))
        assertEquals(allowed.readBytes().toList(), provider.openFile(uri, "r").use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.readBytes().toList() }
        })
        provider.query(uri, null, null, null, null).use { cursor ->
            val row = requireNotNull(cursor)
            assertTrue(row.moveToFirst())
            assertEquals("share73.cwr", row.getString(
                row.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
        }
        try {
            provider.openFile(uri, "w")
            throw AssertionError("Provider nie może zapisywać")
        } catch (_: FileNotFoundException) { }
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/../profile.json"))
        assertNull(provider.getType(Uri.parse("content://" + app.packageName +
            ".replays/../profile.json")))
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/profile.json"))
        assertNull(provider.getType(Uri.parse(uri.toString() + "?unexpected=1")))
        val short = File(directory, "short.cwr").apply { writeBytes(ByteArray(23)) }
        val oversized = File(directory, "oversized.cwr").apply {
            writeBytes(ByteArray(ReplayStore.MAX_SHARE_BYTES + 1))
        }
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/short.cwr"))
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/oversized.cwr"))
        val outside = File(app.filesDir, "outside.cwr").apply { writeBytes(ByteArray(24)) }
        val link = File(directory, "linked.cwr")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/linked.cwr"))
        val fakeRoot = File(app.cacheDir, "replay-share-symlink-root").apply { mkdirs() }
        val externalReplays = File(app.cacheDir, "replay-share-external").apply { mkdirs() }
        val privateReplay = File(externalReplays, "private.cwr").apply { writeBytes(ByteArray(24)) }
        val directoryLink = File(fakeRoot, "replays")
        Files.createSymbolicLink(directoryLink.toPath(), externalReplays.toPath())
        assertNull(ReplayShareProvider.fileFor(fakeRoot, "/private.cwr"))
        assertTrue(profile.exists())
        allowed.delete()
        short.delete()
        oversized.delete()
        outside.delete()
        link.delete()
        directoryLink.delete()
        privateReplay.delete()
        fakeRoot.delete()
        externalReplays.delete()
    }
}
