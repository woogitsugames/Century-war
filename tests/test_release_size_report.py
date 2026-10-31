"""Testy raportu rozmiaru bez Android SDK."""

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "scripts/release_size_report.py"
spec = importlib.util.spec_from_file_location("release_size_report", SOURCE)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class ReleaseSizeReportTest(unittest.TestCase):
    def test_comparison_and_r8_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mapping = root / "mapping.txt"
            usage = root / "usage.txt"
            mapping.write_text("\n".join(c + " -> x.y:" for c in report.CRITICAL_CLASSES) + "\n", encoding="utf-8")
            usage.write_text("com.centurywar.unused.OldClass:\n", encoding="utf-8")
            before_apk, after_apk, before_aab, after_aab = [root / p for p in ("before.apk", "after.apk", "before.aab", "after.aab")]
            for p, length in ((before_apk, 200), (after_apk, 100), (before_aab, 100), (after_aab, 200)):
                with zipfile.ZipFile(p, "w", compression=zipfile.ZIP_STORED) as archive:
                    archive.writestr("classes.dex", b"X" * length)
                    archive.writestr("assets/dexopt/baseline.prof", b"123")
            text = report.build_report(after_apk, after_aab, before_apk, before_aab, mapping, usage)
            self.assertIn("APK", text)
            self.assertIn("AAB", text)
            self.assertIn("Kod DEX", text)
            self.assertIn("Profil ART", text)
            self.assertIn("OldClass", text)
            self.assertIn("krytyczne klasy sa obecne", text)

    def test_mapping_missing_core_class_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mapping = root / "mapping.txt"
            mapping.write_text("com.centurywar.MainActivity -> a.b:\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "brak klas krytycznych"):
                report.r8_audit(mapping, root / "usage.txt")

    def test_mapping_missing_is_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaisesRegex(ValueError, "Brak mapping"):
                report.r8_audit(root / "mapping.txt", root / "usage.txt")


if __name__ == "__main__":
    unittest.main()
