#!/usr/bin/env python3
"""Weryfikuje realne klasy DEX po R8, nie tylko wpisy w mapping.txt."""

import argparse
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path

# Komponenty uruchamiane przez Androida i kluczowe sciezki runtime.
REQUIRED_CLASSES = (
    "com.centurywar.CenturyWarApp",
    "com.centurywar.MainActivity",
    "com.centurywar.meta.ReplayShareProvider",
    "com.centurywar.notifications.DailyReminderReceiver",
    "com.centurywar.engine.GameView",
    "com.centurywar.game.Battle",
    "com.centurywar.game.BattleController",
    "com.centurywar.game.Replay",
    "com.centurywar.meta.Profile",
    "com.centurywar.meta.PlayGamesLeaderboards",
)

# Ordinale sa czescia snapshotow i replayow; nazwy metryk sa kluczami JSON profilu.
REQUIRED_ENUMS = (
    "com.centurywar.meta.AchievementMetric",
    "com.centurywar.game.AiPersonality",
    "com.centurywar.game.DailyEvent",
    "com.centurywar.game.Doctrine",
    "com.centurywar.game.EndlessCard",
    "com.centurywar.game.EndlessWaveTheme",
    "com.centurywar.game.EraArt",
    "com.centurywar.game.Mode",
    "com.centurywar.game.Modifier",
    "com.centurywar.game.Replay$Kind",
    "com.centurywar.game.Role",
    "com.centurywar.game.Weather",
)

# Komponenty manifestu Androida musza zachowac oryginalne nazwy binarne.
MANIFEST_CLASSES = (
    "com.centurywar.CenturyWarApp",
    "com.centurywar.MainActivity",
    "com.centurywar.meta.ReplayShareProvider",
    "com.centurywar.notifications.DailyReminderReceiver",
)

MAPPING_LINE = re.compile(r"^([^\s#]+) -> ([^\s:]+):\s*$", re.MULTILINE)
DESCRIPTOR_LINE = re.compile(r"Class descriptor\s*:\s*'(L[^']+;)'\s*$")
SUPERCLASS_LINE = re.compile(r"Superclass\s*:\s*'([^']+)'\s*$")
DEX_NAME = re.compile(r"classes\d*\.dex$")


def read_mapping(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"Brak mapowania R8: {path}")
    return dict(MAPPING_LINE.findall(path.read_text(encoding="utf-8", errors="replace")))


def parse_dexdump(output: str) -> dict[str, str]:
    """Zwraca deskryptor klasy -> deskryptor nadklasy z dexdump -f."""
    classes = {}
    current = None
    for line in output.splitlines():
        descriptor = DESCRIPTOR_LINE.search(line)
        if descriptor:
            current = descriptor.group(1)
            if current in classes:
                raise ValueError(f"Podwojny deskryptor DEX: {current}")
            classes[current] = ""
            continue
        superclass = SUPERCLASS_LINE.search(line)
        if superclass and current is not None:
            classes[current] = superclass.group(1)
    return classes


def dex_classes(apk: Path, dexdump: Path) -> tuple[dict[str, str], int]:
    if not apk.is_file():
        raise ValueError(f"Brak APK: {apk}")
    if not dexdump.is_file():
        raise ValueError(f"Brak dexdump (Android SDK build-tools): {dexdump}")
    combined = {}
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="cw-release-dex-") as tmp:
        entries = sorted(
            name for name in archive.namelist()
            if DEX_NAME.fullmatch(name) and not name.endswith("/")
        )
        if not entries or "classes.dex" not in entries:
            raise ValueError("APK nie zawiera classes.dex")
        for name in entries:
            target = Path(tmp) / name
            target.write_bytes(archive.read(name))
            result = subprocess.run(
                [str(dexdump), "-f", str(target)],
                capture_output=True, text=True, errors="replace", timeout=120, check=False,
            )
            if result.returncode != 0:
                raise ValueError(f"dexdump {name}: kod {result.returncode}: {result.stderr[-500:]}")
            found = parse_dexdump(result.stdout)
            if not found:
                raise ValueError(f"dexdump nie odczytal klas z {name}")
            overlap = set(found).intersection(combined)
            if overlap:
                raise ValueError(f"Powtarzajace sie klasy w DEX: {sorted(overlap)[:5]}")
            combined.update(found)
    return combined, len(entries)


def descriptor(class_name: str) -> str:
    return "L" + class_name.replace(".", "/") + ";"


def verify(mapping: dict[str, str], classes: dict[str, str]) -> None:
    errors = []
    for original in REQUIRED_CLASSES + REQUIRED_ENUMS:
        actual = mapping.get(original, original)
        if descriptor(actual) not in classes:
            errors.append(f"Brak klasy w DEX: {original} (po R8: {actual})")
    for original in MANIFEST_CLASSES:
        if mapping.get(original, original) != original:
            errors.append(f"Zmieniona nazwa komponentu manifestu: {original}")
    for original in REQUIRED_ENUMS:
        actual = mapping.get(original, original)
        desc = descriptor(actual)
        if desc in classes and classes[desc] != "Ljava/lang/Enum;":
            errors.append(f"Enum bez java.lang.Enum jako nadklasy: {original} ({classes[desc]})")
    if errors:
        raise ValueError("Kontrola DEX po R8 niezaliczona:\n - " + "\n - ".join(errors))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--dexdump", type=Path, required=True)
    args = parser.parse_args()
    mapping = read_mapping(args.mapping)
    classes, dex_files = dex_classes(args.apk, args.dexdump)
    verify(mapping, classes)
    print(
        f"DEX R8 OK: {dex_files} plik(ow) DEX, {len(classes)} klas; "
        f"{len(REQUIRED_CLASSES)} klas krytycznych, {len(REQUIRED_ENUMS)} enumow, "
        f"{len(MANIFEST_CLASSES)} nazw manifestu."
    )


if __name__ == "__main__":
    main()
