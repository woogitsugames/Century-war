#!/usr/bin/env python3
"""Wyznacza rozłączne zestawy testów CI na podstawie bieżących źródeł Kotlin."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


TEST_ROOT = Path(__file__).resolve().parents[1] / "app/src/test/java"
SCREENSHOTS = "com.centurywar.screens.ScreenshotTest"
HEAVY_SCREEN_METHODS = {"screens", "tutorial"}
VISUAL_CLASSES = {
    "ArtGalleryTest",
    "BackdropsTest",
    "RendererStressTest",
    "StoreGraphicsTest",
    "LowEndScreensPerformanceTest",
}
GROUPS = ("jvm", "robolectric", "visual", "screen-device")


@dataclass(frozen=True)
class Source:
    name: str
    methods: tuple[str, ...]
    robolectric: bool


TEST_ANNOTATION = r"@(?:org\.junit\.)?Test"
CLASS_PATTERN = re.compile(
    r"(?m)^(?:(?:internal|public)\s+)?(?:open\s+)?class\s+([A-Za-z_]\w*)\b"
)
METHOD_PATTERN = re.compile(
    TEST_ANNOTATION
    + r"(?:\([^)]*\))?\s*(?:@[^\n]*\n\s*)*(?:(?:internal|public)\s+)?fun\s+([A-Za-z_]\w*)\s*\("
)
ANNOTATION_PATTERN = re.compile(r"(?m)^\s*" + TEST_ANNOTATION + r"\b")
ROBOLECTRIC_PATTERN = re.compile(r"@(?:[\w.]*\.)?RunWith\(\s*[\w.]*RobolectricTestRunner\b")


def discover(root: Path = TEST_ROOT) -> list[Source]:
    result: list[Source] = []
    for path in sorted(root.rglob("*.kt")):
        code = path.read_text(encoding="utf-8")
        annotations = [m.start() for m in ANNOTATION_PATTERN.finditer(code)]
        if not annotations:
            continue
        package = re.search(r"(?m)^package\s+([\w.]+)\s*$", code)
        classes = list(CLASS_PATTERN.finditer(code))
        methods = list(METHOD_PATTERN.finditer(code))
        if package is None or not classes or len(methods) != len(annotations):
            raise ValueError(f"Nie można odczytać klasy/metod @Test: {path}")
        # Plik może mieć kilka klas: metoda należy do najbliższej poprzedzającej klasy.
        owned: dict[int, list[str]] = {}
        for method in methods:
            owners = [i for i, c in enumerate(classes) if c.start() < method.start()]
            if not owners:
                raise ValueError(f"Metoda @Test przed deklaracją klasy: {path}")
            owned.setdefault(owners[-1], []).append(method.group(1))
        for index, declaration in enumerate(classes):
            if index not in owned:
                continue
            name = f"{package.group(1)}.{declaration.group(1)}"
            if any(source.name == name for source in result):
                raise ValueError(f"Powtórzona klasa testowa: {name}")
            if len(classes) == 1:
                robolectric = "RobolectricTestRunner" in code
            else:
                # Adnotacje klasy leżą między końcem poprzedniej klasy ("}" w kolumnie 0)
                # a jej deklaracją.
                header_start = code.rfind("\n}", 0, declaration.start())
                header = code[max(header_start, 0):declaration.start()]
                robolectric = ROBOLECTRIC_PATTERN.search(header) is not None
            result.append(Source(name, tuple(owned[index]), robolectric))
    if not result:
        raise ValueError(f"Brak testów w {root}")
    return result


def selectors(sources: list[Source]) -> dict[str, list[str]]:
    groups: dict[str, list[str]] = {group: [] for group in GROUPS}
    for source in sources:
        short = source.name.rsplit(".", 1)[-1]
        if source.name == SCREENSHOTS:
            missing = HEAVY_SCREEN_METHODS.difference(source.methods)
            if missing:
                raise ValueError(f"Brak testów do podziału: {sorted(missing)}")
            groups["screen-device"].extend(
                f"{source.name}.{method}" for method in sorted(HEAVY_SCREEN_METHODS)
            )
            groups["visual"].extend(
                f"{source.name}.{method}"
                for method in source.methods
                if method not in HEAVY_SCREEN_METHODS
            )
        elif source.robolectric and (
            "Screenshot" in short or short in VISUAL_CLASSES
        ):
            groups["visual"].append(source.name)
        elif source.robolectric:
            groups["robolectric"].append(source.name)
        else:
            groups["jvm"].append(source.name)
    verify_coverage(sources, groups)
    return groups


def verify_coverage(sources: list[Source], groups: dict[str, list[str]]) -> None:
    expected = {f"{s.name}.{method}" for s in sources for method in s.methods}
    covered: set[str] = set()
    for group in GROUPS:
        for selector in groups[group]:
            matches = {
                method for method in expected
                if method == selector or method.startswith(selector + ".")
            }
            if not matches:
                raise ValueError(f"Niepasujący selektor {group}: {selector}")
            if covered.intersection(matches):
                raise ValueError(f"Test przypisany do więcej niż jednego joba: {selector}")
            covered.update(matches)
    if expected != covered:
        raise ValueError(f"Testy pominięte przez CI: {sorted(expected - covered)}")
    if not all(groups.values()):
        raise ValueError("Jeden z zestawów testów jest pusty")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("verify", "args"))
    parser.add_argument("group", nargs="?", choices=GROUPS)
    args = parser.parse_args()
    sources = discover()
    groups = selectors(sources)
    if args.command == "verify":
        print(f"Pokrycie: {len(sources)} klas, {sum(len(s.methods) for s in sources)} testów")
        for group in GROUPS:
            print(f"  {group}: {len(groups[group])} selektorów")
    else:
        if args.group is None:
            parser.error("Polecenie args wymaga nazwy grupy")
        for selector in groups[args.group]:
            print("--tests")
            print(selector)


if __name__ == "__main__":
    main()
