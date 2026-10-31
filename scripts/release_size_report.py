#!/usr/bin/env python3
"""Porownanie rozmiarow APK/AAB oraz kontrola klas R8 po budowie release."""

import argparse
import re
import zipfile
from pathlib import Path

CRITICAL_CLASSES = (
    "com.centurywar.CenturyWarApp",
    "com.centurywar.MainActivity",
    "com.centurywar.engine.Game",
    "com.centurywar.engine.GameView",
    "com.centurywar.screens.MenuScreen",
    "com.centurywar.screens.BattleScreen",
    "com.centurywar.game.Battle",
    "com.centurywar.game.BattleRenderer",
    "com.centurywar.meta.Profile",
)


def size(path):
    return path.stat().st_size if path is not None and path.is_file() else None


def human(n):
    return f"{n / 1024:.1f} KiB" if n is not None else "n/d"


def delta(before, after):
    if before is None or after is None or before == 0:
        return "n/d"
    change = after - before
    return f"{change:+,} B ({change * 100 / before:+.1f}%)".replace(",", " ")


def component(name):
    name = name.removeprefix("base/")
    if re.search(r"(?:^|/)classes\d*\.dex$", name):
        return "Kod DEX"
    if name.endswith("resources.arsc") or name.endswith("resources.pb"):
        return "Tabela zasobow"
    if name.startswith("res/") or name.startswith("manifest/"):
        return "Zasoby i manifest"
    if name.startswith("assets/dexopt/") or "baseline.prof" in name:
        return "Profil ART"
    if name.startswith("assets/"):
        return "Pozostale assets"
    if name.startswith("lib/"):
        return "Biblioteki natywne"
    return "Metadane i inne"


def archive_stats(path):
    with zipfile.ZipFile(path) as f:
        entries = [e for e in f.infolist() if not e.is_dir()]
    groups = {}
    for item in entries:
        groups[component(item.filename)] = groups.get(component(item.filename), 0) + item.compress_size
    top = sorted(entries, key=lambda e: e.compress_size, reverse=True)[:10]
    return groups, top


def r8_audit(mapping, usage):
    if not mapping.is_file():
        raise ValueError(f"Brak mapping.txt: {mapping}")
    content = mapping.read_text(encoding="utf-8", errors="replace")
    names = set(re.findall(r"^([\w.$]+) -> [\w.$]+:$", content, re.MULTILINE))
    missing = [name for name in CRITICAL_CLASSES if name not in names]
    if missing:
        raise ValueError("R8: brak klas krytycznych w mapping.txt: " + ", ".join(missing))
    usage_text = usage.read_text(encoding="utf-8", errors="replace") if usage.is_file() else None
    removed = []
    if usage_text is not None:
        removed = re.findall(r"^(com\.centurywar\.[\w.$]+):$", usage_text, re.MULTILINE)
        bad = sorted(set(removed).intersection(CRITICAL_CLASSES))
        if bad:
            raise ValueError("R8 usunal krytyczne klasy: " + ", ".join(bad))
    return len(names), sorted(set(removed)), usage_text is not None


def build_report(apk, aab, before_apk, before_aab, mapping, usage):
    mapped, removed, has_usage = r8_audit(mapping, usage)
    lines = [
        "# Century War - raport release (klucz testowy)",
        "",
        "Kompilacja APK/AAB przez R8 i shrinkResources; to nie jest podpis produkcyjny.",
        "Porownanie dotyczy plikow ZIP (rozmiar na dysku), nie rozmiaru pobrania z Google Play.",
        "",
        "## Rozmiary przed i po",
        "",
        "| Format | Przed | Po | Zmiana |",
        "| --- | ---: | ---: | ---: |",
    ]
    for label, before, after in (("APK", before_apk, apk), ("AAB", before_aab, aab)):
        previous = size(before)
        current = size(after)
        if current is None:
            raise ValueError(f"Brak artefaktu {label}: {after}")
        lines.append(f"| {label} | {human(previous)} | {human(current)} | {delta(previous, current)} |")
    lines += ["", "## Skladniki paczek po zmianach (rozmiar skompresowany)", ""]
    for label, artifact in (("APK", apk), ("AAB", aab)):
        groups, top = archive_stats(artifact)
        lines += [f"### {label}", "", "| Grupa | Suma skompresowana |", "| --- | ---: |"]
        for key, value in sorted(groups.items(), key=lambda item: -item[1]):
            lines.append(f"| {key} | {human(value)} |")
        lines += ["", "Najwieksze elementy (skompresowane / przed kompresja):", "", "| Element | ZIP | Surowy |", "| --- | ---: | ---: |"]
        for item in top:
            lines.append(f"| {item.filename.replace('|', '_')} | {human(item.compress_size)} | {human(item.file_size)} |")
        lines.append("")
    lines += [
        "## Audyt R8",
        "",
        f"- mapping.txt: {mapped} zmapowanych klas; wszystkie {len(CRITICAL_CLASSES)} krytyczne klasy sa obecne.",
        f"- usage.txt: {'dostepny' if has_usage else 'BRAK - wymaga recznej kontroli'}; "
        f"{len(removed)} usunietych klas aplikacji/symulacji (moga byc nieuzywane).",
    ]
    if removed:
        lines.extend(["", "Przyklady usunietych klas (maks. 20):", ""])
        lines.extend(f"- {name}" for name in removed[:20])
    lines += [
        "",
        "Kontrola mapowania sprawdza zachowanie klas, nie gwarantuje poprawnosci kazdej sciezki gry.",
        "Smoke-test wydania na emulatorze jest osobnym krokiem CI.",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--before-apk", type=Path)
    parser.add_argument("--before-aab", type=Path)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--usage", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(args.apk, args.aab, args.before_apk, args.before_aab, args.mapping, args.usage)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
