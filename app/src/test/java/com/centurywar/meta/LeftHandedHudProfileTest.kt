package com.centurywar.meta

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Ustawienie nie wymaga migracji starych profili. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeftHandedHudProfileTest {
    @Test
    fun defaultsToRightHandedAndSurvivesSaveLoad() {
        val file = File(Files.createTempDirectory("hud110-profile").toFile(), "profile.json")
        val profile = Profile(file)
        assertFalse(profile.leftHandedHud)
        profile.leftHandedHud = true
        profile.save()
        Profile.awaitHintWrites()
        val saved = Profile(file)
        saved.load()
        assertTrue(saved.leftHandedHud)
        assertTrue(JSONObject(saved.toJson()).getBoolean("leftHandedHud"))
        val oldJson = JSONObject(saved.toJson()).apply { remove("leftHandedHud") }.toString()
        saved.fromJson(oldJson)
        assertFalse(saved.leftHandedHud)
        saved.fromJson(JSONObject(oldJson).put("leftHandedHud", "true").toString())
        assertFalse("Niepoprawny typ nie może aktywować opcji", saved.leftHandedHud)
        saved.leftHandedHud = true
        saved.reset()
        assertTrue("Reset postępu powinien zachować dostępność", saved.leftHandedHud)
    }
}
