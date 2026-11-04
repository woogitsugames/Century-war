#!/usr/bin/env python3
"""Kontrola premierowego zestawu zrzutów D7 bez zewnętrznych zależności.

Użycie: python3 scripts/validate_store_d7.py [katalog] [--languages pl,en]
Domyślnie sprawdza app/build/storeD7 we wszystkich 10 językach gry.
"""
from __future__ import annotations

import csv
import struct
import sys
from pathlib import Path

LANGUAGES = ("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")
# urządzenie: (szerokość, wysokość, play_upload)
DEVICES = {
    "phone": (1920, 1080, True),
    "tablet7": (1920, 1080, True),
    "tablet10": (2560, 1440, True),
    "fold": (2184, 1968, False),
    "phone20x9_qa": (2400, 1080, False),
}
SCENES = (
    "01-commander.png",
    "02-turret-tiers.png",
    "03-campaign.png",
    "04-endless-cards.png",
    "05-results.png",
    "06-missions.png",
    "07-accessibility.png",
    "08-weekly-siege.png",
)
FOLD_OUTER_SHEET = "preview_foldOuter_qa.png"


def png_header(file: Path) -> tuple[int, int, int, int]:
    if not file.is_file() or file.stat().st_size < 2048:
        raise ValueError(f"Nieistniejący lub pusty obraz: {file}")
    with file.open("rb") as stream:
        header = stream.read(26)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError(f"Błędny nagłówek PNG: {file}")
    return struct.unpack(">IIBB", header[16:26])


def inspect_png(file: Path, width: int, height: int, play: bool = True) -> None:
    w, h, depth, colour = png_header(file)
    # Kolor 2 = RGB bez kanału alfa (Play odrzuca przezroczyste zrzuty).
    if (w, h, depth, colour) != (width, height, 8, 2):
        raise ValueError(
            f"{file}: oczekiwano {(width, height, 8, 2)}, otrzymano {(w, h, depth, colour)}"
        )
    if play and max(w, h) > 2 * min(w, h):
        raise ValueError(f"{file}: proporcje powyżej 2:1")


def validate_language(directory: Path) -> int:
    expected: dict[str, tuple[int, int, str]] = {}
    for device, (w, h, playable) in DEVICES.items():
        if playable and max(w, h) > 2 * min(w, h):
            raise ValueError(f"Proporcje niedopuszczalne w Play: {device}")
        for scene in SCENES:
            expected[f"{device}/{scene}"] = (w, h, str(playable).lower())
    expected["feature_1024x500.png"] = (1024, 500, "true")

    manifest = directory / "manifest.tsv"
    with manifest.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)
        if reader.fieldnames != ["file", "width", "height", "alt", "play_upload"]:
            raise ValueError(f"Błędny nagłówek manifestu: {manifest}")
        found = set()
        play = 0
        for row in reader:
            name = row["file"]
            if name in found or name not in expected:
                raise ValueError(f"{manifest}: duplikat lub nieznany plik {name}")
            found.add(name)
            width, height, playable = expected[name]
            if (row["width"], row["height"], row["play_upload"]) != (
                str(width), str(height), playable
            ):
                raise ValueError(f"{manifest}: niezgodny wpis {name}")
            alt = row["alt"] or ""
            if not 1 <= len(alt) <= 140:
                raise ValueError(f"{manifest}: tekst alternatywny {name} ma {len(alt)} znaków")
            inspect_png(directory / name, width, height,
                        playable == "true" and name != "feature_1024x500.png")
            if playable == "true" and name != "feature_1024x500.png":
                play += 1
        if found != set(expected):
            raise ValueError(f"Brak wpisów w {manifest}: {sorted(set(expected) - found)}")
    if play != 8 * sum(1 for _, _, p in DEVICES.values() if p):
        raise ValueError(f"{directory}: {play} zrzutów do Play zamiast 24")
    inspect_png(directory / "preview_contact.png", 1600, 960)
    # Pion zewnętrznego Folda: wyłącznie stykówka QA (szeroka, więc bez kontroli 2:1).
    w, h, depth, colour = png_header(directory / FOLD_OUTER_SHEET)
    if (depth, colour) != (8, 2) or w <= h:
        raise ValueError(f"{directory / FOLD_OUTER_SHEET}: zła stykówka pionu")
    return play


def main(argv: list[str]) -> None:
    root = Path("app/build/storeD7")
    languages = LANGUAGES
    args = list(argv)
    while args:
        arg = args.pop(0)
        if arg == "--languages":
            languages = tuple(code.strip() for code in args.pop(0).split(",") if code.strip())
            unknown = set(languages) - set(LANGUAGES)
            if unknown:
                raise SystemExit(f"Nieznane języki: {sorted(unknown)}")
        else:
            root = Path(arg)
    total = 0
    for language in languages:
        play = validate_language(root / language)
        total += play
        print(f"{language}: {play} zrzutów do Play + Fold + QA + grafika + stykówki, OK")
    print(f"Zestaw D7: {total} zrzutów do Play w {len(languages)} językach, OK")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (ValueError, OSError) as error:
        raise SystemExit(f"BŁĄD: {error}")
