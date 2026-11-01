"""Testy narzędzi dzielenia CI, niezależne od Android SDK."""

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from ci_test_shards import SCREENSHOTS, discover, selectors
from ci_test_timings import collect, markdown
from ci_synthetic_play_games_ids import main as synthetic_ids, substitute


class CiToolsTest(unittest.TestCase):
    def test_groups_cover_every_method_without_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "SimTest.kt").write_text(
                "package com.example\nclass SimTest {\n  @Test fun sim() {}\n}\n",
                encoding="utf-8",
            )
            (folder / "RoboTest.kt").write_text(
                "package com.example\n@RunWith(RobolectricTestRunner::class)\n"
                "class RoboTest {\n  @Test fun ui() {}\n}\n",
                encoding="utf-8",
            )
            (folder / "ScreenshotTest.kt").write_text(
                "package com.centurywar.screens\n@RunWith(RobolectricTestRunner::class)\n"
                "class ScreenshotTest {\n"
                "  @Test fun screens() {}\n"
                "  @Test fun tutorial() {}\n"
                "  @Test\n  @Config(sdk = [35])\n  fun menus() {}\n"
                "}\n",
                encoding="utf-8",
            )
            (folder / "LowEndScreensPerformanceTest.kt").write_text(
                "package com.example\n@RunWith(RobolectricTestRunner::class)\n"
                "class LowEndScreensPerformanceTest {\n  @Test fun measured() {}\n}\n",
                encoding="utf-8",
            )
            groups = selectors(discover(folder))
            self.assertEqual(["com.example.SimTest"], groups["jvm"])
            self.assertEqual(["com.example.RoboTest"], groups["robolectric"])
            self.assertCountEqual(
                [SCREENSHOTS + ".menus", "com.example.LowEndScreensPerformanceTest"],
                groups["visual"],
            )
            self.assertEqual(
                [SCREENSHOTS + ".screens", SCREENSHOTS + ".tutorial"],
                groups["screen-device"],
            )

    def test_methods_belong_to_nearest_preceding_class(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / "LTest.kt").write_text(
                "package com.example\nimport org.junit.Test\n"
                "class LTest {\n  @Test fun plain() {}\n  @Test fun other() {}\n}\n\n"
                "@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)\n"
                "@org.robolectric.annotation.Config(sdk = [35])\n"
                "class LScreenTest {\n  @org.junit.Test fun screen() {}\n}\n\n"
                "class LHelper {\n  fun notATest() {}\n}\n",
                encoding="utf-8",
            )
            sources = {s.name: s for s in discover(Path(directory))}
            self.assertEqual(
                {"com.example.LTest", "com.example.LScreenTest"}, set(sources)
            )
            self.assertEqual(("plain", "other"), sources["com.example.LTest"].methods)
            self.assertFalse(sources["com.example.LTest"].robolectric)
            self.assertEqual(("screen",), sources["com.example.LScreenTest"].methods)
            self.assertTrue(sources["com.example.LScreenTest"].robolectric)

    def test_unrecognised_test_annotation_fails_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / "OopsTest.kt").write_text(
                "package com.example\nclass OopsTest {\n"
                "  @Test\n  val ignored = 1\n}\n",
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                discover(Path(directory))

    def test_synthetic_play_games_ids_replace_only_placeholders(self):
        text = "# app_id=REPLACE_ME\napp_id=REPLACE_ME\nach_a=REPLACE_ME_A\nach_b=CgkIreal\n"
        result = substitute(text)
        self.assertIn("# app_id=REPLACE_ME", result)
        self.assertIn("app_id=123456789012", result)
        self.assertIn("ach_a=CgkI000000000001EAIQAg", result)
        self.assertIn("ach_b=CgkIreal", result)
        with tempfile.TemporaryDirectory() as directory:
            # Brak pliku w starszej bazie PR nie jest błędem.
            self.assertEqual(0, synthetic_ids(["x", str(Path(directory) / "missing")]))

    def test_timing_report_sums_shards(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "shard-a").mkdir()
            (folder / "shard-b").mkdir()
            for shard, duration in (("shard-a", "2.50"), ("shard-b", "1.25")):
                (folder / shard / "TEST-com.example.A.xml").write_text(
                    '<testsuite name="com.example.A">'
                    '<testcase classname="com.example.A" name="run" time="'
                    + duration + '"/></testsuite>',
                    encoding="utf-8",
                )
            classes, methods, count = collect([folder])
            self.assertEqual(2, count)
            self.assertAlmostEqual(3.75, classes["com.example.A"])
            self.assertEqual((3.75, 2), methods["com.example.A.run"])
            result = markdown(classes, methods, count, 20)
            self.assertIn("| `com.example.A.run` | 3.75 s | 2 |", result)


if __name__ == "__main__":
    unittest.main()
