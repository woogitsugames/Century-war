"""Testy podziału zlecenia 99; nie wymagają Android SDK."""

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from ci_test_categories import category, patterns, verify


class CategoryTest(unittest.TestCase):
    def test_named_categories_include_methods_and_default_to_fast(self):
        rules = patterns()
        cases = {
            "com.centurywar.engine.LTest.languages": "fast",
            "com.centurywar.screens.ScreenshotTest.menus": "screenshots",
            "com.centurywar.screens.ScreenshotGoldenTest.identicalPixelsPass": "screenshots",
            "com.centurywar.game.ArtGalleryTest.gallery": "screenshots",
            "com.centurywar.game.BalanceReport.campaignReport": "balance",
            "com.centurywar.game.Doctrine69BattleSimTest.mirroredAiMatchesRemainBelowSixtyPercent": "balance",
            "com.centurywar.game.BattleSimTest.campaignStagesFinish": "balance",
            "com.centurywar.game.BattleSimTest.deterministicForSameSeed": "fast",
            "com.centurywar.server.EndlessBalanceReportTest.veteranTwentySeeds": "balance",
        }
        for name, expected in cases.items():
            with self.subTest(name=name):
                self.assertEqual(expected, category(name, rules))

    def test_duplicate_patterns_are_detected(self):
        with self.assertRaises(ValueError):
            category("example.ScreenshotBalanceTest.run", {
                "screenshots": ("*Screenshot*",),
                "balance": ("*Balance*",),
            })

    def test_invalid_patterns_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "patterns.properties"
            file.write_text("screenshots=*Screenshot*,\nbalance=*Balance*\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                patterns(file)

    def test_real_repository_methods_are_covered(self):
        counts = verify()
        self.assertGreater(sum(sum(v.values()) for v in counts.values()), 40)
        self.assertGreater(counts["app"]["screenshots"], 0)
        self.assertGreater(counts["sim"]["balance"], 0)
        self.assertGreater(counts["server"]["balance"], 0)


if __name__ == "__main__":
    unittest.main()
