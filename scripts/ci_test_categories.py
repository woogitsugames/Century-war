#!/usr/bin/env python3
"""Weryfikuje rozłączny podział istniejących testów na fast/screenshots/balance."""

from __future__ import annotations

import argparse
from collections import Counter
from fnmatch import fnmatchcase
from pathlib import Path

from ci_test_shards import discover


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "gradle/test-categories.properties"
SOURCES = {
    "app": ROOT / "app/src/test/java",
    "sim": ROOT / "sim/src/test/kotlin",
    "server": ROOT / "server/src/test/kotlin",
}


def patterns(path: Path = CONFIG) -> dict[str, tuple[str, ...]]:
    result: dict[str, tuple[str, ...]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Niepoprawny wpis kategorii: {line}")
        name, raw = line.split("=", 1)
        name = name.strip()
        if name in result:
            raise ValueError(f"Powtórzona kategoria: {name}")
        values = tuple(pattern.strip() for pattern in raw.split(","))
        if not values or any(not pattern for pattern in values):
            raise ValueError(f"Pusta lista lub wzorzec: {name}")
        result[name] = values
    if set(result) != {"screenshots", "balance"}:
        raise ValueError(f"Oczekiwano screenshots i balance, otrzymano: {sorted(result)}")
    return result


def category(name: str, rules: dict[str, tuple[str, ...]]) -> str:
    matches = [group for group in ("screenshots", "balance")
               if any(fnmatchcase(name, rule) for rule in rules[group])]
    if len(matches) > 1:
        raise ValueError(f"Test pasuje do wielu kategorii: {name} -> {matches}")
    return matches[0] if matches else "fast"


def verify() -> dict[str, Counter[str]]:
    rules = patterns()
    counts: dict[str, Counter[str]] = {}
    for module, root in SOURCES.items():
        counter: Counter[str] = Counter()
        for source in discover(root):
            for method in source.methods:
                group = category(f"{source.name}.{method}", rules)
                if module != "app" and group == "screenshots":
                    raise ValueError(f"Zrzuty spoza :app: {source.name}.{method}")
                counter[group] += 1
        if not counter["fast"]:
            raise ValueError(f"Brak testów fast w module {module}")
        if module == "app" and not counter["screenshots"]:
            raise ValueError("Brak testów screenshots w :app")
        if not counter["balance"]:
            raise ValueError(f"Brak testów balance w module {module}")
        counts[module] = counter
    return counts


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("verify",))
    parser.parse_args()
    counts = verify()
    for module, counter in counts.items():
        print(f"{module}: fast={counter['fast']}, screenshots={counter['screenshots']}, "
              f"balance={counter['balance']}, razem={sum(counter.values())}")
    print("Kategorie pokrywają testy bez pominięć i bez nakładania się wzorców.")


if __name__ == "__main__":
    main()
