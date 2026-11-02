package com.centurywar.meta

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresja A156-06: odczyt nie wyprzedza zapisów z poprzedniej instancji. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ProfileLoad160Test {
    @get:Rule val folder = TemporaryFolder()

    @Test fun pendingWriterAfterTimeoutCannotBeOverwrittenByStaleProfile() {
        val file = File(folder.root, "profile.json")
        val old = Profile(file)
        val writerBlocked = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val loaded = Profile(file)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val loader = Thread({
            try { loaded.load() } catch (e: Throwable) { failure.set(e) }
            finally { finished.countDown() }
        }, "test-profile-loader")
        try {
            // Callback pierwszego zapisu przetrzymuje writer już po zapisie 10 medali.
            old.onSaved = {
                writerBlocked.countDown()
                releaseWriter.await(10, TimeUnit.SECONDS)
            }
            old.medals = 10
            old.save()
            assertTrue(writerBlocked.await(4, TimeUnit.SECONDS))
            old.medals = 500
            old.save() // Kolejna migawka czeka za zatrzymanym writerem.

            loader.start()
            assertFalse("load() udostępnił stary profil po 2 s", finished.await(2300, TimeUnit.MILLISECONDS))
            releaseWriter.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertEquals(500, loaded.medals)
            loaded.medals = 515
            loaded.save()
            Profile.awaitHintWrites()
            assertEquals(515, Profile(file).apply { load() }.medals)
        } finally {
            releaseWriter.countDown()
            loader.join(4000)
            old.onSaved = null
            Profile.awaitHintWrites()
        }
    }
}
