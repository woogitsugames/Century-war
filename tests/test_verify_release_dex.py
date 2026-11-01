"""Testy kontroli rzeczywistych deskryptorow DEX bez Android SDK."""

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "scripts/verify_release_dex.py"
spec = importlib.util.spec_from_file_location("verify_release_dex", SOURCE)
verify_dex = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify_dex)


def representative_classes():
    classes = {
        verify_dex.descriptor(name): "Ljava/lang/Object;"
        for name in verify_dex.REQUIRED_CLASSES
    }
    classes.update({
        verify_dex.descriptor(name): "Ljava/lang/Enum;"
        for name in verify_dex.REQUIRED_ENUMS
    })
    return classes


class ReleaseDexCheckTest(unittest.TestCase):
    def test_dexdump_parses_class_descriptors_and_enum_superclass(self):
        text = """Class #0              -
  Class descriptor  : 'La/b/C;'
  Access flags      : 0x0001 (PUBLIC)
  Superclass        : 'Ljava/lang/Object;'
Class #1              -
  Class descriptor  : 'La/b/E;'
  Superclass        : 'Ljava/lang/Enum;'
"""
        self.assertEqual({"La/b/C;": "Ljava/lang/Object;", "La/b/E;": "Ljava/lang/Enum;"},
                         verify_dex.parse_dexdump(text))

    def test_mapping_remaps_minified_class_names(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "mapping.txt"
            path.write_text("com.centurywar.engine.GameView -> a.b:\n" +
                            "com.centurywar.game.Replay$Kind -> a.c:\n")
            mapping = verify_dex.read_mapping(path)
            classes = representative_classes()
            classes["La/b;"] = classes.pop("Lcom/centurywar/engine/GameView;")
            classes["La/c;"] = classes.pop("Lcom/centurywar/game/Replay$Kind;")
            verify_dex.verify(mapping, classes)

    def test_missing_enum_fails_closed(self):
        classes = representative_classes()
        classes.pop("Lcom/centurywar/game/Mode;")
        with self.assertRaisesRegex(ValueError, "Brak klasy w DEX: com.centurywar.game.Mode"):
            verify_dex.verify({}, classes)

    def test_enum_replaced_with_non_enum_fails(self):
        classes = representative_classes()
        classes["Lcom/centurywar/meta/AchievementMetric;"] = "Ljava/lang/Object;"
        with self.assertRaisesRegex(ValueError, "Enum bez java.lang.Enum"):
            verify_dex.verify({}, classes)

    def test_manifest_component_cannot_be_obfuscated(self):
        classes = representative_classes()
        classes["La/b;"] = classes.pop("Lcom/centurywar/meta/ReplayShareProvider;")
        with self.assertRaisesRegex(ValueError, "Zmieniona nazwa komponentu manifestu"):
            verify_dex.verify({"com.centurywar.meta.ReplayShareProvider": "a.b"}, classes)

    def test_missing_dexdump_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "test.apk"
            with zipfile.ZipFile(apk, "w") as out:
                out.writestr("classes.dex", b"not-a-real-dex")
            with self.assertRaisesRegex(ValueError, "Brak dexdump"):
                verify_dex.dex_classes(apk, Path(tmp) / "missing-dexdump")

    def test_no_classes_dex_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "test.apk"
            binary = Path(tmp) / "dexdump"
            binary.write_text("fake")
            with zipfile.ZipFile(apk, "w") as out:
                out.writestr("res/a.xml", b"")
            with self.assertRaisesRegex(ValueError, "APK nie zawiera classes.dex"):
                verify_dex.dex_classes(apk, binary)


if __name__ == "__main__":
    unittest.main()
