"""Testy interfejsu narzędzia na niewielkich, syntetycznych raportach."""

import csv
import shutil
import subprocess
import sys
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "check_campaign_balance.py"
FIXTURES = ROOT / "tests" / "fixtures" / "campaign_balance"


@pytest.fixture
def reports(tmp_path):
    directory = tmp_path / "reports"
    shutil.copytree(FIXTURES, directory)
    return directory


def run_checker(directory, tmp_path):
    output = tmp_path / "wynik.md"
    result = subprocess.run(
        [sys.executable, str(SCRIPT), str(directory), "--output", str(output)],
        text=True, capture_output=True, encoding="utf-8", check=False,
    )
    return result, output


def edit_report(path, edit):
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        fields = reader.fieldnames
        rows = list(reader)
    edit(rows)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def test_complete_report_produces_markdown(reports, tmp_path):
    result, output = run_checker(reports, tmp_path)
    assert result.returncode == 0, result.stderr + result.stdout
    markdown = output.read_text(encoding="utf-8")
    rows = [line for line in markdown.splitlines() if line.startswith("| ")]
    assert len(rows) == 37  # Nagłówek i 36 etapów; separator zaczyna się od |---.
    assert "| 1 | target | 1.00 | 0-1 | 90.0% | 5.00 | 0 |" in rows
    assert "| 36 | target | 1.00 | 0-1 | 50.0% | 5.00 | 0 |" in rows
    assert "Wszystkie kryteria zaakceptowane (36 etapow, 20 seedow)." in markdown
    assert result.stdout == markdown + "\n"


def test_missing_stage_reports_number(reports, tmp_path):
    edit_report(reports / "report-1.csv", lambda rows: rows.pop(2))
    result, output = run_checker(reports, tmp_path)
    assert result.returncode != 0
    assert "Brakuje etapow: [3]" in output.read_text(encoding="utf-8")
    assert "Brakuje etapow: [3]" in result.stdout


def test_missing_seed_reports_stage_and_count(reports, tmp_path):
    edit_report(reports / "report-2.csv", lambda rows: rows[0].update(n="19"))
    result, output = run_checker(reports, tmp_path)
    assert result.returncode != 0
    assert "Etap 7: N=19, wymagane 20" in output.read_text(encoding="utf-8")
    assert "Etap 7: N=19, wymagane 20" in result.stdout


def test_empty_directory_is_rejected(tmp_path):
    directory = tmp_path / "empty"
    directory.mkdir()
    result, output = run_checker(directory, tmp_path)
    assert result.returncode != 0
    markdown = output.read_text(encoding="utf-8")
    assert "Oczekiwano 6 plikow raportu, znaleziono 0" in markdown
    assert "Brakuje etapow:" in markdown


@pytest.mark.parametrize("column,value", [("wins", "nie-liczba"), ("n", "0"), ("median_minutes", "?")])
def test_corrupt_numeric_data_is_rejected(reports, tmp_path, column, value):
    edit_report(reports / "report-1.csv", lambda rows: rows[0].update({column: value}))
    result, output = run_checker(reports, tmp_path)
    assert result.returncode != 0
    assert "Etap 1: wadliwe dane" in output.read_text(encoding="utf-8")


def test_duplicate_stage_is_rejected(reports, tmp_path):
    edit_report(reports / "report-1.csv", lambda rows: rows.append(rows[0].copy()))
    result, output = run_checker(reports, tmp_path)
    assert result.returncode != 0
    assert "Etap 1 jest w wielu raportach" in output.read_text(encoding="utf-8")
