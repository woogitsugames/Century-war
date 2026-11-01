#!/usr/bin/env python3
"""Raport najwolniejszych testów JUnit z XML Gradle (również z wielu shardów)."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree


def seconds(value: float) -> str:
    return f"{value:.2f} s"


def collect(paths: list[Path]) -> tuple[dict[str, float], dict[str, tuple[float, int]], int]:
    classes: dict[str, float] = defaultdict(float)
    methods: dict[str, tuple[float, int]] = {}
    count = 0
    for base in paths:
        xml_files = sorted(base.rglob("TEST-*.xml")) if base.is_dir() else (
            [base] if base.is_file() and base.suffix == ".xml" else []
        )
        for path in xml_files:
            root = ElementTree.parse(path).getroot()
            for case in root.iter("testcase"):
                name = case.get("classname") or root.get("name") or "<unknown>"
                method = case.get("name") or "<unknown>"
                duration = float(case.get("time", "0"))
                if duration < 0:
                    raise ValueError(f"Ujemny czas testu: {path}")
                classes[name] += duration
                key = f"{name}.{method}"
                previous, runs = methods.get(key, (0.0, 0))
                methods[key] = (previous + duration, runs + 1)
                count += 1
    return classes, methods, count


def markdown(classes: dict[str, float], methods: dict[str, tuple[float, int]],
             count: int, top: int) -> str:
    lines = [
        "# Czasy testów CI",
        "",
        f"Zmierzono {count} uruchomień metod w {len(classes)} klasach.",
        "Czas jest sumą czasu testowego z XML (nie czasem ściennym równoległych jobów).",
        "",
        f"## Top {top} klas",
        "",
        "| Klasa | Czas |",
        "|---|---:|",
    ]
    for name, duration in sorted(classes.items(), key=lambda x: (-x[1], x[0]))[:top]:
        lines.append(f"| `{name}` | {seconds(duration)} |")
    lines += ["", f"## Top {top} metod", "",
              "| Metoda | Czas łączny | Uruchomienia |", "|---|---:|---:|"]
    for name, (duration, runs) in sorted(methods.items(), key=lambda x: (-x[1][0], x[0]))[:top]:
        lines.append(f"| `{name}` | {seconds(duration)} | {runs} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path, help="Katalogi z TEST-*.xml")
    parser.add_argument("--top", type=int, default=20)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    classes, methods, count = collect(args.paths)
    if not count:
        parser.error("Brak raportów TEST-*.xml")
    report = markdown(classes, methods, count, max(1, args.top))
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
