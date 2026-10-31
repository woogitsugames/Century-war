package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.centurywar.BuildConfig
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.SecureLinks
import com.centurywar.engine.Ui
import kotlin.math.max
import kotlin.math.min

/** Ekran informacji o produkcie i licencjach kodu umieszczonego w APK. */
class AboutScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val privacy = btn(1)
    private val fullLicense = btn(2)
    private val report = btn(3)
    private val entries = LicenseCatalog.app
    private val links = Array(entries.size) { i -> btn(100 + i).apply { iconId = Icons.GLOBE } }
    private val privacyUrl = BuildConfig.CW_PRIVACY_URL
    private val hasPrivacy = SecureLinks.isHttps(privacyUrl)
    private val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val panel = RectF()
    private val row = RectF()
    private var scroll = 0f
    private var maxScroll = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var textStart = 0f
    private var firstRow = 0f
    private var footerTop = 0f
    private var dt = 0f
    private var versionText = ""
    private var authorText = ""
    private var creditText = ""
    private var licenseTitle = ""

    init { refreshLabels() }

    private fun refreshLabels() {
        back.label = L["back"]
        privacy.label = L["about_privacy"]
        privacy.iconId = Icons.GLOBE
        report.label = L["report_problem"]
        report.iconId = Icons.BOOK
        fullLicense.label = L["about_apache_full"]
        fullLicense.iconId = Icons.BOOK
        versionText = L["about_version"] + ": " + version
        authorText = L["about_author"]
        creditText = L["about_credits"]
        licenseTitle = L["about_licenses"]
        for (b in links) b.label = L["about_link"]
    }

    override fun onLanguageChanged() {
        refreshLabels()
        layout()
    }

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        layout()
    }

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(820 * dp, w - g.insetL - g.insetR - 24 * dp)
        val left = (w - pw) / 2f
        panel.set(left, chrome.top + 7 * dp, left + pw, h - g.insetB - 11 * dp)
        contentTop = panel.top + 11 * dp
        contentBottom = panel.bottom - 11 * dp
        val introDp = if (hasPrivacy) 222f else 174f
        val step = 103 * dp
        val fullHeight = (introDp + 24 + 70) * dp + entries.size * step
        maxScroll = max(0f, fullHeight - (contentBottom - contentTop))
        scroll = scroll.coerceIn(0f, maxScroll)
        textStart = panel.top + 24 * dp - scroll
        firstRow = textStart + introDp * dp
        footerTop = firstRow + entries.size * step + 12 * dp
        privacy.set(panel.left + 20 * dp, textStart + 98 * dp, min(250 * dp, pw - 40 * dp), 39 * dp)
        privacy.visible = hasPrivacy && privacy.r.bottom > contentTop && privacy.r.top < contentBottom
        report.set(panel.left + 20 * dp, textStart + (if (hasPrivacy) 141 else 98) * dp,
            min(250 * dp, pw - 40 * dp), 39 * dp)
        report.visible = report.r.bottom > contentTop && report.r.top < contentBottom
        fullLicense.set(panel.left + 20 * dp, footerTop, min(310 * dp, pw - 40 * dp), 42 * dp)
        fullLicense.visible = fullLicense.r.bottom > contentTop && fullLicense.r.top < contentBottom
        for (i in entries.indices) {
            val y = firstRow + i * step
            links[i].set(panel.right - 114 * dp, y + 5 * dp, 96 * dp, 33 * dp)
            links[i].visible = y + 96 * dp > contentTop && y < contentBottom
        }
    }

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        c.save()
        c.clipRect(panel.left + 8 * dp, contentTop, panel.right - 8 * dp, contentBottom)
        val tx = panel.left + 22 * dp
        val textWidth = panel.width() - 44 * dp
        ui.textO(c, "Century War", tx, textStart + 9 * dp, 25f, Ui.GOLD)
        ui.textO(c, versionText, tx, textStart + 35 * dp, 15f, Ui.TEXT)
        ui.textO(c, authorText, tx, textStart + 58 * dp, 15f, Ui.TEXT)
        ui.textO(c, creditText, tx, textStart + 84 * dp,
            ui.fit(creditText, 15f, textWidth, 10f), Ui.TEXT)
        if (privacy.visible) ui.button(c, privacy, 13f)
        if (report.visible) ui.button(c, report, 13f)
        ui.textO(c, licenseTitle, tx, firstRow - 18 * dp, 19f, Ui.GOLD)
        val step = 103 * dp
        for (i in entries.indices) {
            val y = firstRow + i * step
            if (y + 96 * dp < contentTop || y > contentBottom) continue
            val e = entries[i]
            row.set(panel.left + 14 * dp, y, panel.right - 14 * dp, y + 96 * dp)
            ui.fill.color = if (i % 2 == 0) Color.argb(55, 255, 255, 255)
                else Color.argb(30, 255, 255, 255)
            c.drawRoundRect(row, 10 * dp, 10 * dp, ui.fill)
            val x = row.left + 13 * dp
            val width = row.width() - 26 * dp
            ui.textO(c, e.name, x, y + 22 * dp,
                ui.fit(e.name, 17f, width - 110 * dp, 11f), Ui.GOLD)
            ui.textO(c, e.owner, x, y + 44 * dp,
                ui.fit(e.owner, 13f, width, 10f), Ui.TEXT)
            ui.textO(c, e.license, x, y + 65 * dp,
                ui.fit(e.license, 13f, width, 10f), Ui.TEXT)
            ui.textO(c, e.url, x, y + 84 * dp,
                ui.fit(e.url, 11f, width, 8f), Ui.TEXT)
            if (links[i].visible) ui.button(c, links[i], 12f)
        }
        if (fullLicense.visible) ui.button(c, fullLicense, 12f)
        c.restore()
        chrome.title(c, L["about"])
        ui.button(c, back, 16f)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            1 -> if (hasPrivacy) g.host.openExternalUrl(privacyUrl)
            2 -> g.host.showApacheLicense()
            3 -> g.host.shareProblemReport()
            else -> if (b.id >= 100) entries.getOrNull(b.id - 100)?.let {
                if (SecureLinks.isHttps(it.url)) g.host.openExternalUrl(it.url)
            }
        }
    }
}

/** Licencje runtime aplikacji; biblioteki serwera wymieniono wyłącznie w docs/LICENSES.md. */
data class LicenseNotice(val name: String, val owner: String, val license: String, val url: String, val module: String)

object LicenseCatalog {
    val app = arrayOf(
        LicenseNotice("Play Games Services v2 (22.1.0)", "Google LLC", "Android SDK License",
            "https://developer.android.com/studio/terms",
            "com.google.android.gms:play-services-games-v2:22.1.0"),
        LicenseNotice("Play Services Base (18.5.0)", "Google LLC", "Android SDK License",
            "https://developer.android.com/studio/terms",
            "com.google.android.gms:play-services-base:18.5.0"),
        LicenseNotice("Play Services Basement (18.9.0)", "Google LLC", "Android SDK License",
            "https://developer.android.com/studio/terms",
            "com.google.android.gms:play-services-basement:18.9.0"),
        LicenseNotice("Play Services Tasks (18.2.0)", "Google LLC", "Android SDK License",
            "https://developer.android.com/studio/terms",
            "com.google.android.gms:play-services-tasks:18.2.0"),
        LicenseNotice("AndroidX Activity (1.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.activity:activity:1.0.0"),
        LicenseNotice("AndroidX Annotation (1.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.annotation:annotation:1.1.0"),
        LicenseNotice("AndroidX Arch Core Common (2.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.arch.core:core-common:2.1.0"),
        LicenseNotice("AndroidX Arch Core Runtime (2.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.arch.core:core-runtime:2.0.0"),
        LicenseNotice("AndroidX Collection (1.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.collection:collection:1.1.0"),
        LicenseNotice("AndroidX Core (1.2.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.core:core:1.2.0"),
        LicenseNotice("AndroidX CustomView (1.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.customview:customview:1.0.0"),
        LicenseNotice("AndroidX Fragment (1.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.fragment:fragment:1.1.0"),
        LicenseNotice("AndroidX Lifecycle Common (2.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.lifecycle:lifecycle-common:2.1.0"),
        LicenseNotice("AndroidX Lifecycle LiveData (2.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.lifecycle:lifecycle-livedata:2.0.0"),
        LicenseNotice("AndroidX LiveData Core (2.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.lifecycle:lifecycle-livedata-core:2.0.0"),
        LicenseNotice("AndroidX Lifecycle Runtime (2.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.lifecycle:lifecycle-runtime:2.1.0"),
        LicenseNotice("AndroidX Lifecycle ViewModel (2.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.lifecycle:lifecycle-viewmodel:2.1.0"),
        LicenseNotice("AndroidX Loader (1.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.loader:loader:1.0.0"),
        LicenseNotice("AndroidX SavedState (1.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.savedstate:savedstate:1.0.0"),
        LicenseNotice("AndroidX VersionedParcelable (1.1.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.versionedparcelable:versionedparcelable:1.1.0"),
        LicenseNotice("AndroidX ViewPager (1.0.0)", "Android Open Source Project", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "androidx.viewpager:viewpager:1.0.0"),
        LicenseNotice("Kotlin Standard Library (2.2.20)", "JetBrains s.r.o.", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "org.jetbrains.kotlin:kotlin-stdlib:2.2.20"),
        LicenseNotice("JetBrains Annotations (13.0)", "JetBrains s.r.o.", "Apache License 2.0",
            "https://www.apache.org/licenses/LICENSE-2.0",
            "org.jetbrains:annotations:13.0"),
    )
}
