#!/usr/bin/env python3
"""Kontrola zrzutów z zadania 153 bez zewnętrznych zależności."""
from __future__ import annotations

import csv
import struct
import sys
from pathlib import Path

LANGUAGES = ("pl", "en", "de")
DEVICES = {
    "phone16x9": (1920, 1080, True),
    "phone20x9_qa": (2400, 1080, False),
    "tablet10": (2560, 1440, True),
    "fold": (2184, 1968, True),
}
SCENES = (
    "01-commander-aura.png",
    "02-turret-tiers.png",
    "03-campaign-map.png",
    "04-endless-cards.png",
    "05-battle-chart.png",
)


def inspect_png(file: Path, width: int, height: int) -> None:
    if not file.is_file() or file.stat().st_size < 2048:
        raise ValueError(f"Nieistniejący lub pusty obraz: {file}")
    with file.open("rb") as stream:
        header = stream.read(26)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError(f"Błędny nagłówek PNG: {file}")
    w, h, depth, colour = struct.unpack(">IIBB", header[16:26])
    if (w, h, depth, colour) != (width, height, 8, 2):
        raise ValueError(
            f"{file}: oczekiwano {(width, height, 8, 2)}, otrzymano {(w, h, depth, colour)}"
        )


def validate(root: Path) -> None:
    for language in LANGUAGES:
        directory = root / language
        expected = {}
        for device, (w, h, playable) in DEVICES.items():
            if playable and max(w, h) > 2 * min(w, h):
                raise ValueError(f"Proporcje niedopuszczalne w Play: {device}")
            for scene in SCENES:
                expected[f"{device}/{scene}"] = (w, h, str(playable).lower())
        expected["feature_1024x500.png"] = (1024, 500, "true")

        manifest = directory / "manifest.tsv"
        with manifest.open(newline="", encoding="utf-8") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            if reader.fieldnames != ["file", "width", "height", "alt", "play_upload"]:
                raise ValueError(f"Błędny nagłówek manifestu: {manifest}")
            found = set()
            for row in reader:
                name = row["file"]
                if name in found or name not in expected:
                    raise ValueError(f"Duplikat lub nieznany plik: {name}")
                found.add(name)
                width, height, playable = expected[name]
                if (row["width"], row["height"], row["play_upload"]) != (
                    str(width), str(height), playable
                ):
                    raise ValueError(f"Niezgodny wpis manifestu: {name}")
                alt = row["alt"]
                if not 1 <= len(alt) <= 140:
                    raise ValueError(f"Nieprawidłowy tekst alternatywny: {name}")
                inspect_png(directory / name, width, height)
            if found != set(expected):
                raise ValueError(f"Brak wpisów w {manifest}: {sorted(set(expected) - found)}")
        inspect_png(directory / "preview_contact.png", 1600, 420)
        print(f"{language}: 20 zrzutów + feature + stykówka, OK")

    print("Zgodność PNG RGB24 i manifestów zadania 153: OK")


if __name__ == "__main__":
    validate(Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/store153"))
