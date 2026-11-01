#!/usr/bin/env python3
"""Kontrola eksportu Google Play; tylko biblioteka standardowa Pythona."""

from __future__ import annotations

import argparse
from pathlib import Path
import struct

LANGUAGES = ("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")
SCENES = (
    "menu", "campaign", "endless-boss", "night-barrage", "era-variants",
    "pause-summary", "weekly-siege", "custom-skirmish",
)
DEVICES = {
    "phone": (1920, 1080),
    "tablet7": (1920, 1080),
    "tablet10": (2560, 1440),
}
FOLD_PREVIEWS = {
    "preview_foldLandscape_hud.png": (2184, 1968),
    "preview_foldPortrait_hud.png": (1968, 2184),
}
SIGNATURE = bytes((137, 80, 78, 71, 13, 10, 26, 10))


def png_info(path: Path) -> tuple[int, int, int, int, int]:
    data = path.read_bytes()
    if len(data) <= 1024 or data[:8] != SIGNATURE:
        raise ValueError(f"Niepoprawny lub pusty PNG: {path}")
    if data[12:16] != b"IHDR" or struct.unpack(">I", data[8:12])[0] != 13:
        raise ValueError(f"Brak nagłówka IHDR: {path}")
    width, height, depth, kind = struct.unpack(">IIBB", data[16:26])
    return width, height, depth, kind, len(data)


def check_png(path: Path, size: tuple[int, int], rgba: bool = False) -> None:
    width, height, depth, kind, byte_size = png_info(path)
    if (width, height, depth, kind) != (*size, 8, 6 if rgba else 2):
        raise ValueError(f"Złe parametry {path}: {width}x{height}, głębia {depth}, typ {kind}")
    if path.name == "icon_512.png" and byte_size > 1024 * 1024:
        raise ValueError(f"Ikona przekracza 1024 KB: {path}")


def validate(root: Path, languages: tuple[str, ...] = LANGUAGES) -> int:
    count = 0
    for language in languages:
        directory = root / language
        expected: dict[str, tuple[int, int]] = {}
        for device, size in DEVICES.items():
            if min(size) < 320 or max(size) > 3840 or max(size) > 2 * min(size):
                raise ValueError(f"Wymiary poza limitami Play: {device}: {size}")
            if size[0] * 9 != size[1] * 16:
                raise ValueError(f"Tablet/telefon poza zalecanym 16:9: {device}")
            for index, scene in enumerate(SCENES, 1):
                path = f"{device}/{index:02d}-{scene}.png"
                expected[path] = size
        expected["feature_1024x500.png"] = (1024, 500)
        expected["icon_512.png"] = (512, 512)

        manifest = directory / "manifest.tsv"
        rows = manifest.read_text(encoding="utf-8").splitlines()
        if not rows or rows[0] != "file\twidth\theight\talt":
            raise ValueError(f"Niepoprawny manifest: {manifest}")
        found: set[str] = set()
        for row in rows[1:]:
            parts = row.split("\t")
            if len(parts) != 4:
                raise ValueError(f"Niepoprawny rekord manifestu: {row}")
            relative, width, height, alt = parts
            if relative in found or relative not in expected:
                raise ValueError(f"Nieoczekiwany lub powtórzony plik: {relative}")
            if not alt.strip() or len(alt) > 140:
                raise ValueError(f"Niepoprawny tekst alternatywny: {relative}")
            size = expected[relative]
            if (width, height) != (str(size[0]), str(size[1])):
                raise ValueError(f"Manifest i PNG mają różne wymiary: {relative}")
            check_png(directory / relative, size, rgba=(relative == "icon_512.png"))
            found.add(relative)
            count += 1
        if found != set(expected):
            raise ValueError(f"Niekompletny zestaw {language}: {sorted(set(expected) - found)}")

        # Kontrola ujęć Folda i planszy QA: nie wysyłać ich do Play Console.
        for filename, size in FOLD_PREVIEWS.items():
            check_png(directory / filename, size)
            count += 1
        check_png(directory / "preview_contact.png", (1600, 960))
        count += 1

    print(f"OK: {len(languages)} języków, {26 * len(languages)} plików Play, "
          f"{count - 26 * len(languages)} podglądów QA, razem {count} PNG")
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", type=Path, default=Path("app/build/store"))
    args = parser.parse_args()
    validate(args.root)
