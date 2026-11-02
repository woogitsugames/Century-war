#!/usr/bin/env python3
"""Sprawdza 30 PNG regresji UI przed opublikowaniem artefaktu CI."""

from __future__ import annotations

import argparse
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
DEVICES = ("phone720", "foldOpen")
SCENES = (
    "menu", "campaign", "campaign_info", "research", "achievements",
    "mode_endless", "mode_skirmish", "skirmish_setup", "rankings", "settings",
    "accessibility", "about", "codex", "battle_hud", "battle_pause",
)


def verify(directory: Path) -> int:
    missing: list[str] = []
    invalid: list[str] = []
    for device in DEVICES:
        for scene in SCENES:
            name = f"{device}_reg100_{scene}.png"
            file = directory / name
            if not file.is_file():
                missing.append(name)
            elif file.stat().st_size <= len(PNG_SIGNATURE) or file.open("rb").read(8) != PNG_SIGNATURE:
                invalid.append(name)
    if missing or invalid:
        raise ValueError(
            f"Niekompletne PNG regresji: brak {len(missing)}, niepoprawne {len(invalid)}. "
            f"Brakujące: {', '.join(missing)}. Niepoprawne: {', '.join(invalid)}."
        )
    return len(DEVICES) * len(SCENES)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="Katalog app/build/screenshots")
    args = parser.parse_args()
    try:
        count = verify(args.directory)
    except ValueError as exc:
        parser.error(str(exc))
    print(f"Potwierdzono {count} plików PNG regresji UI w {args.directory}.")


if __name__ == "__main__":
    main()
