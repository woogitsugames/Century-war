"""Kontrola kompletności PNG po wykonaniu i odtworzeniu testów z cache."""

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from verify_ci_screenshots import DEVICES, PNG_SIGNATURE, SCENES, verify


class ScreenshotArtifactTest(unittest.TestCase):
    def test_empty_artifact_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "brak 30"):
                verify(Path(temporary))

    def test_complete_artifact_passes_and_corrupt_png_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for device in DEVICES:
                for scene in SCENES:
                    (directory / f"{device}_reg100_{scene}.png").write_bytes(
                        PNG_SIGNATURE + b"test-image"
                    )
            self.assertEqual(30, verify(directory))
            (directory / "foldOpen_reg100_menu.png").write_bytes(b"not-a-png")
            with self.assertRaisesRegex(ValueError, "niepoprawne 1"):
                verify(directory)


if __name__ == "__main__":
    unittest.main()
