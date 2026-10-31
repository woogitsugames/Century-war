#!/usr/bin/env python3
"""Sprawdza komplet 36 etapow raportu balansu na 20 seedach."""
import argparse
import csv
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path, help="Katalog pobranych raportow CSV")
    parser.add_argument("--output", type=Path, default=Path("campaign-20-seeds.md"))
    args = parser.parse_args()

    rows = {}
    problems = []
    files = sorted(args.directory.rglob("report-*.csv"))
    if len(files) != 6:
        problems.append(f"Oczekiwano 6 plikow raportu, znaleziono {len(files)}")
    for path in files:
        with path.open(newline="", encoding="utf-8") as stream:
            for row in csv.DictReader(stream):
                stage = int(row["stage"])
                if stage in rows:
                    problems.append(f"Etap {stage} jest w wielu raportach")
                rows[stage] = row

    missing = sorted(set(range(1, 37)) - set(rows))
    extra = sorted(set(rows) - set(range(1, 37)))
    if missing:
        problems.append(f"Brakuje etapow: {missing}")
    if extra:
        problems.append(f"Nieznane etapy: {extra}")

    doc = [
        "# Pomiar balansu kampanii (profil docelowy, 20 seedow)",
        "",
        "| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout |",
        "|---:|---|---:|---|---:|---:|---:|",
    ]
    rates = {}
    for stage, row in sorted(rows.items()):
        try:
            runs = int(row["n"])
            wins = int(row["wins"])
            timeouts = int(row["timeouts"])
            median = float(row["median_minutes"])
            rate = 100.0 * wins / runs
            rates[stage] = rate
            if runs != 20:
                problems.append(f"Etap {stage}: N={runs}, wymagane 20")
            lower, upper = (
                (85, 100) if stage <= 6 else
                (45, 85) if stage <= 27 else
                (40, 75) if stage <= 33 else
                (25, 55)
            )
            if not lower <= rate <= upper:
                problems.append(f"Etap {stage}: wygrane {rate:.1f}% poza {lower}-{upper}%")
            if timeouts:
                problems.append(f"Etap {stage}: {timeouts} patow")
            if stage >= 7 and median < 4.0:
                problems.append(f"Etap {stage}: mediana {median:.2f} min < 4 min")
            doc.append(
                f"| {stage} | {row['profile']} | {row['ai_power']} | "
                f"{row['start_era']}-{row['max_era']} | {rate:.1f}% | "
                f"{median:.2f} | {timeouts} |"
            )
        except (KeyError, ValueError, ZeroDivisionError) as exc:
            problems.append(f"Etap {stage}: wadliwe dane {exc}")
    for stage in range(2, 36):
        if not all(index in rates for index in (stage - 1, stage, stage + 1)):
            continue
        reference = (rates[stage - 1] + rates[stage + 1]) / 2.0
        delta = abs(rates[stage] - reference)
        if delta > 25.000001:
            problems.append(
                f"Etap {stage}: odchylenie {delta:.1f} pp od sredniej sasiadow"
            )

    doc += ["", "## Walidacja", ""]
    doc.extend(f"- {problem}" for problem in problems)
    if not problems:
        doc.append("- Wszystkie kryteria zaakceptowane (36 etapow, 20 seedow).")
    args.output.write_text("\n".join(doc) + "\n", encoding="utf-8")
    print(args.output.read_text(encoding="utf-8"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
