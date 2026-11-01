#!/usr/bin/env python3
"""Tymczasowo zbiera teksty z oddzielnych gałęzi do testów lokalizacji 116.

Skrypt działa wyłącznie w katalogu roboczym CI i nie tworzy commitów.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

SOURCE = "app/src/main/java/com/centurywar/engine/L.kt"
BRANCHES = {
    92: "gpt/92-play-games-config",
    93: "gpt/93-privacy-link",
    104: "gpt/104-battle-summary",
    105: "gpt/105-stats-charts",
    106: "gpt/106-whats-new",
    107: "gpt/107-profile-backup",
    108: "gpt/108-endless-cards-2",
    109: "gpt/109-daily-rules-2",
    110: "gpt/110-left-handed-hud",
}


def run(*args: str) -> str:
    result = subprocess.run(
        list(args), check=True, capture_output=True, text=True, encoding="utf-8"
    )
    return result.stdout.strip()


def keys(source: str) -> set[str]:
    return set(re.findall(r'(?m)^\s*"(\w+)"\s+to\s+t\(', source))


def extract_part(source: str, part: int) -> str | None:
    pattern = re.compile(
        rf"(?ms)^    private fun part{part}\(\): "
        r"Array<Pair<String, Array<out String>>> = arrayOf\("
        r"\n.*?^    \)"
    )
    match = pattern.search(source)
    return match.group() if match else None


def main() -> None:
    path = Path(SOURCE)
    base = path.read_text(encoding="utf-8")
    seen = keys(base)
    sections: list[str] = []
    calls: list[str] = []
    log: list[str] = []

    for part, branch in BRANCHES.items():
        if re.search(rf"\bprivate fun part{part}\s*\(", base):
            log.append(f"{part}: w bazie (bez pobierania)")
            continue

        run("git", "fetch", "--quiet", "--no-tags", "--depth=1", "origin", branch)
        commit = run("git", "rev-parse", "FETCH_HEAD")
        upstream = run("git", "show", "FETCH_HEAD:" + SOURCE)
        snippet = extract_part(upstream, part)
        if not snippet:
            log.append(f"{part}: brak nowych tekstów ({branch}, {commit[:10]})")
            continue
        part_keys = keys(snippet)
        duplicates = sorted(seen & part_keys)
        if duplicates:
            raise RuntimeError(f"Klucze powtórzone w part{part}: {duplicates}")

        seen.update(part_keys)
        sections.append(snippet)
        calls.append(f"        putAll(part{part}())")
        log.append(f"{part}: {len(part_keys)} kluczy ({branch}, {commit[:10]})")

    if not sections:
        print("Brak części do tymczasowego dodania")
        return

    # Dołącz na końcu inicjalizatora, po wywołaniach części bazowych.
    insertion = re.search(r"(?m)^        putAll\(part87\(\)\)$", base)
    if not insertion:
        raise RuntimeError("Nie znaleziono końca bazowej listy part* w L.kt")
    position = insertion.end()
    base = base[:position] + "\n" + "\n".join(calls) + base[position:]

    # Plik składa się z jednego obiektu L; dopisz funkcje przed jego końcem.
    closing = base.rfind("\n}")
    if closing < 0:
        raise RuntimeError("Nie znaleziono zakończenia obiektu L")
    base = base[:closing] + "\n\n" + "\n\n".join(sections) + "\n" + base[closing:]
    path.write_text(base, encoding="utf-8")

    report = "\n".join(log) + "\n"
    dest = Path("app/build/screenshots/l10n116/collected-branches.txt")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(report, encoding="utf-8")
    print(report)
    print("Razem kluczy w źródle po dodaniu:", len(keys(base)))
    print("Zmiana istnieje tylko w workspace CI i nie będzie commitowana.")


if __name__ == "__main__":
    main()
