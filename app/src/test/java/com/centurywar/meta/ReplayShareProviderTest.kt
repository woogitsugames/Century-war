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
            assertTrue(cursor!!.moveToFirst())
            assertEquals("share73.cwr", cursor.getString(
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
        }
        try {
            provider.openFile(uri, "w")
            throw AssertionError("Provider nie może zapisywać")
        } catch (_: FileNotFoundException) { }
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/../profile.json"))
        assertNull(provider.getType(Uri.parse("content://" + app.packageName +
            ".replays/../profile.json")))
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/profile.json"))
        val outside = File(app.filesDir, "outside.cwr").apply { writeBytes(ByteArray(24)) }
        val link = File(directory, "linked.cwr")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertNull(ReplayShareProvider.fileFor(app.filesDir, "/linked.cwr"))
        assertTrue(profile.exists())
        allowed.delete()
        outside.delete()
        link.delete()
    }
}
