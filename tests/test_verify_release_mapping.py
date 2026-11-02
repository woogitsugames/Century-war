"""Testy kontroli stabilnych nazw po R8 bez Android SDK."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "scripts/verify_release_mapping.py"
spec = importlib.util.spec_from_file_location("verify_release_mapping", SOURCE)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def mapping_fixture():
    lines = ["# compiler: R8", "# compiler_version: 8.13.0"]
    for name, fields in release.REQUIRED.items():
        lines.append(f"{name} -> {name}:")
        for field in fields:
            lines.append(f"    int {field} -> {field}")
        lines.append("    1:2:void init():1:2 -> a")
        lines.append("# { 'id': 'com.android.tools.r8.mapping', 'version': '2.2' }")
    return "\n".join(lines) + "\n"


def configuration_fixture():
    return "\n".join(
        [f"-keep class {name} {{ <fields>; }}" for name in release.REQUIRED_FIELDS] +
        [f"-keep enum {name} {{ *; }}" for name in release.REQUIRED_ENUM_FIELDS]
    )


class ReleaseMappingTest(unittest.TestCase):
    def test_complete_mapping_passes(self):
        classes, fields = release.verify(mapping_fixture(), "com.centurywar.unused.Removed:\n")
        self.assertEqual(len(release.REQUIRED), classes)
        self.assertGreater(fields, 60)

    def test_class_rename_fails(self):
        text = mapping_fixture().replace(
            "com.centurywar.meta.Profile -> com.centurywar.meta.Profile:",
            "com.centurywar.meta.Profile -> a.a:",
        )
        with self.assertRaisesRegex(ValueError, "Przemianowana klasa"):
            release.verify(text)

    def test_missing_class_fails(self):
        text = mapping_fixture().replace(
            "com.centurywar.game.ReplayRecord -> com.centurywar.game.ReplayRecord:",
            "com.centurywar.game.ReplayRecordREMOVED -> com.centurywar.game.ReplayRecord:",
        )
        with self.assertRaisesRegex(ValueError, "Brak klasy"):
            release.verify(text)

    def test_field_rename_fails(self):
        text = mapping_fixture().replace("int medals -> medals", "int medals -> a")
        with self.assertRaisesRegex(ValueError, "Przemianowane pole"):
            release.verify(text)

    def test_unchanged_field_missing_from_mapping_is_normal(self):
        text = mapping_fixture().replace("    int stars -> stars\n", "")
        release.verify(text, "", configuration_fixture())

    def test_kept_fields_can_be_absent_from_mapping(self):
        text = "\n".join(line for line in mapping_fixture().splitlines()
                         if not line.startswith("    int ")) + "\n"
        release.verify(text, "", configuration_fixture())

    def test_enum_constant_rename_fails(self):
        text = mapping_fixture().replace("int SUMMON_COMMANDER -> SUMMON_COMMANDER", "int SUMMON_COMMANDER -> a")
        with self.assertRaisesRegex(ValueError, "Przemianowane pole"):
            release.verify(text)

    def test_usage_removed_field_fails(self):
        with self.assertRaisesRegex(ValueError, "Usuniete pole"):
            release.verify(mapping_fixture(), "com.centurywar.meta.Profile:\n    int medals\n")

    def test_usage_removed_class_fails(self):
        with self.assertRaisesRegex(ValueError, "Usunieta klasa"):
            release.verify(mapping_fixture(), "com.centurywar.game.Replay:\n")

    def test_mapping_method_lines_not_recognized_as_fields(self):
        text = mapping_fixture().replace("int medals -> medals", "1:4:void medals() -> medals")
        release.verify(text, "", configuration_fixture())

    def test_usage_removed_unrelated_method_does_not_fail(self):
        release.verify(mapping_fixture(), "com.centurywar.meta.Profile:\n    void internalDebugMethod()\n")

    def test_configuration_missing_keep_class_fails(self):
        cfg = configuration_fixture().replace(
            "-keep class com.centurywar.meta.Profile { <fields>; }", ""
        )
        with self.assertRaisesRegex(ValueError, "Brak zastosowanej reguly keep dla pol"):
            release.verify(mapping_fixture(), "", cfg)

    def test_configuration_missing_keep_enum_fails(self):
        cfg = configuration_fixture().replace(
            "-keep enum com.centurywar.game.Mode { *; }", ""
        )
        with self.assertRaisesRegex(ValueError, "Brak zastosowanej reguly keep dla enum"):
            release.verify(mapping_fixture(), "", cfg)

    def test_metadata_between_class_and_field_keeps_context(self):
        text = mapping_fixture().replace(
            "com.centurywar.meta.Profile -> com.centurywar.meta.Profile:\n",
            "com.centurywar.meta.Profile -> com.centurywar.meta.Profile:\n# { id: sourceFile }\n",
        )
        release.verify(text)

    def test_duplicate_class_fails(self):
        text = mapping_fixture() + "com.centurywar.meta.Profile -> com.centurywar.meta.Profile:\n"
        with self.assertRaisesRegex(ValueError, "Powtorzona klasa"):
            release.verify(text)

    def test_cli_fails_on_missing_input(self):
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as d:
            result = subprocess.run(
                [sys.executable, str(SOURCE), "--mapping", str(Path(d) / "missing"),
                 "--usage", str(Path(d) / "usage.txt"),
                 "--configuration", str(Path(d) / "configuration.txt")],
                capture_output=True, text=True, check=False,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("Brak mapping.txt", result.stderr)


if __name__ == "__main__":
    unittest.main()
