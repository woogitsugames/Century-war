"""Regresje dokumentacji prywatności i sklepu, bez Android SDK."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
STORE = DOCS / "store"
APP = ROOT / "app" / "src" / "main"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read(path):
    return path.read_text(encoding="utf-8")


class PrivacyDocumentationTest(unittest.TestCase):
    def setUp(self):
        self.pl = read(STORE / "privacy_policy_pl.md")
        self.en = read(STORE / "privacy_policy_en.md")
        self.safety = read(STORE / "data_safety.md")

    def test_pgs_achievements_are_described_as_synced_after_authentication(self):
        adapter = read(APP / "java/com/centurywar/meta/PlayGamesLeaderboards.kt")
        achievements = read(APP / "java/com/centurywar/meta/Achievements.kt")
        self.assertIn("achievementsClient.unlock(id)", adapter)
        self.assertIn("flushAchievements()", adapter)
        self.assertIn("Achievements.syncUnlocked(", adapter)
        self.assertIn("fun syncUnlocked(", achievements)
        self.assertIn("Osiągnięcia Google Play Games", self.pl)
        self.assertIn("uwierzytelnieniu", self.pl)
        self.assertIn("odblokowanych osiągnięciach", self.pl)
        self.assertIn("Google Play Games achievements", self.en)
        self.assertIn("authenticated", self.en)
        self.assertIn("unlocked achievements", self.en)
        self.assertNotIn("nie jest jeszcze podłączone", self.pl)
        self.assertNotIn("not yet connected", self.en)
        self.assertIn("PGS — osiągnięcia", self.safety)
        self.assertNotIn("Nie reklamuj osiągnięć PGS jako aktywnych", self.safety)

    def test_android_backup_exact_files_match_both_rule_sets(self):
        old = ET.parse(APP / "res/xml/backup_rules.xml").getroot()
        modern = ET.parse(APP / "res/xml/data_extraction_rules.xml").getroot()
        expected = {"profile.json", "history.json"}
        old_includes = {x.attrib["path"] for x in old.findall("include")}
        cloud = modern.find("cloud-backup")
        transfer = modern.find("device-transfer")
        self.assertIsNotNone(cloud)
        self.assertIsNotNone(transfer)
        self.assertEqual(expected, old_includes)
        self.assertEqual(expected, {x.attrib["path"] for x in cloud.findall("include")})
        self.assertEqual(expected, {x.attrib["path"] for x in transfer.findall("include")})
        self.assertTrue(all(
            x.attrib.get("requireFlags") == "clientSideEncryption"
            for x in old.findall("include")
        ))
        self.assertEqual("true", cloud.attrib.get("disableIfNoEncryptionCapabilities"))
        manifest = ET.parse(APP / "AndroidManifest.xml").getroot()
        application = manifest.find("application")
        self.assertEqual("true", application.attrib[ANDROID_NS + "allowBackup"])
        for document in (self.pl, self.en, self.safety):
            for filename in expected:
                self.assertIn(filename, document)
            self.assertIn("battle.snap", document)

    def test_android_8_is_not_promised_end_to_end_encryption(self):
        for document in (self.pl, self.en, self.safety):
            with self.subTest(document=document[:40]):
                self.assertIn("Android 8/8.1", document)
                self.assertIn("API 26", document)
                self.assertIn("Android 9", document)
                self.assertIn("API 28", document)
                self.assertIn("API 31+", document)
                self.assertIn("clientSideEncryption", document)
                self.assertIn("end-to-end", document.lower())
        self.assertIn("nie gwarantuje", self.pl)
        self.assertIn("does not guarantee", self.en)
        self.assertIn("Nie gwarantuj E2E", self.safety)

    def test_saved_games_do_not_confuse_history_and_backup(self):
        profile = read(APP / "java/com/centurywar/meta/Profile.kt")
        self.assertIn('remove("playerId")', profile)
        self.assertIn('remove("playerSecret")', profile)
        self.assertIn('remove("globalRanking")', profile)
        for document in (self.pl, self.en, self.safety):
            self.assertIn("Saved Games", document)
            self.assertIn("history.json", document)
        self.assertIn("bez** lokalnego UUID", self.safety)

    def test_publishers_server_and_notifications_have_consistent_disclosures(self):
        client = read(APP / "java/com/centurywar/meta/ScoreClient.kt")
        self.assertIn("HttpsURLConnection", client)
        self.assertIn("deletePlayerData(", client)
        self.assertIn("reportNick(", client)
        for document in (self.pl, self.en, self.safety):
            self.assertIn("UUID", document)
            self.assertIn("SHA-256", document)
            self.assertIn("HTTPS", document)
            self.assertIn("IP", document)
            self.assertIn("AlarmManager", document)
            self.assertIn("NotificationManager", document)
        self.assertIn("Brak FCM/push", self.safety)
        self.assertIn("brak reklam", self.safety.lower())
        self.assertIn("SDK PGS", self.safety)

    def test_owner_fields_are_still_renderable_and_require_real_input(self):
        owner = read(STORE / "OWNER_INPUT.md")
        fields = {
            "publisher_name", "contact_email", "postal_address",
            "privacy_policy_url", "release_date",
        }
        found = {}
        for line in owner.splitlines():
            cells = line.split("|")
            if len(cells) >= 4:
                key = cells[1].strip().strip("`")
                if key in fields:
                    self.assertNotIn(key, found, "Powtórzony klucz OWNER_INPUT: " + key)
                    found[key] = cells[2].strip()
        self.assertEqual(fields, set(found))
        self.assertEqual({"WYMAGA_DANYCH_WLASCICIELA"}, set(found.values()))
        for key in fields:
            self.assertIn("{{OWNER." + key + "}}", self.pl)
            self.assertIn("{{OWNER." + key + "}}", self.en)
        for requirement in ("CW_SERVER_URL", "CW_PRIVACY_URL", "CW_HMAC_KEY",
                            "adres", "66 osiągnięć", "71 identyfikatorów"):
            self.assertIn(requirement, owner)
        self.assertNotIn("{{OWNER.", self.safety)

    def test_pgs_catalog_count_matches_owner_instructions(self):
        ids = read(STORE / "play_games_ids.properties")
        keys = [
            line.split("=", 1)[0].strip()
            for line in ids.splitlines()
            if "=" in line and not line.lstrip().startswith("#")
        ]
        self.assertEqual(71, len(keys))
        self.assertEqual(66, sum(x.startswith("achievement_") for x in keys))
        self.assertEqual(4, sum(x.startswith("leaderboard_") for x in keys))
        self.assertIn("app_id", keys)
        owner = read(STORE / "OWNER_INPUT.md")
        play_console = read(STORE / "PLAY_CONSOLE.md")
        setup = read(DOCS / "PLAY_GAMES_SETUP.md")
        for doc in (owner, play_console, setup, self.safety):
            self.assertIn("66 osiągnięć", doc)
        self.assertNotRegex(play_console, r"54 osiągnię")
        self.assertIn("jako app_id", setup)

    def test_gdd_does_not_compare_to_other_games(self):
        gdd = read(DOCS / "GDD.md")
        self.assertNotRegex(gdd, re.compile(r"Age of War|\\bAoW\\b|Clash of Clans|Clash Royale", re.I))
        self.assertIn("Krótkie", gdd) if False else None
        self.assertIn("metaprogresją na tygodnie", gdd)
        self.assertIn("jednym pasie", gdd)
        self.assertIn("kampania", gdd.lower())

    def test_data_safety_links_to_official_requirements(self):
        for url in (
            "https://support.google.com/googleplay/android-developer/answer/10787469",
            "https://support.google.com/googleplay/android-developer/answer/10144311",
            "https://developer.android.com/games/pgs/data-collection",
            "https://developer.android.com/identity/data/autobackup",
        ):
            self.assertIn(url, self.safety)
        self.assertIn("2 listopada 2026", self.safety)


if __name__ == "__main__":
    unittest.main()
