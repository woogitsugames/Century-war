"""Sprawdzenie rzeczywistego interfejsu skryptu na odizolowanych kopiach kart."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys

import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'tools' / 'check_store_listings.py'
FIXTURES = ROOT / 'tests' / 'fixtures' / 'store_listings'


@pytest.fixture
def cards(tmp_path):
    directory = tmp_path / 'karty sklepu'
    shutil.copytree(FIXTURES, directory)
    return directory


def run(directory):
    env = dict(os.environ, PYTHONIOENCODING='utf-8')
    return subprocess.run([sys.executable, str(SCRIPT), str(directory)],
                          text=True, encoding='utf-8', capture_output=True, env=env)


def replace_section(directory, name, value):
    path = directory / 'listing_en.md'
    text = path.read_text(encoding='utf-8')
    pattern = r'(^## ' + re.escape(name) + r'[^\S\r\n]*\r?\n).*?(?=^## |\Z)'
    text, count = re.subn(pattern, lambda m: m.group(1) + value + '\n\n', text,
                          count=1, flags=re.M | re.S)
    assert count == 1
    path.write_text(text, encoding='utf-8')


def test_complete_copies_pass(cards):
    result = run(cards)
    assert result.returncode == 0, result.stderr
    assert len(result.stdout.splitlines()) == 30
    assert result.stderr == ''


def test_missing_language_is_error(cards):
    (cards / 'listing_de.md').unlink()
    result = run(cards)
    assert result.returncode != 0
    assert 'listing_de.md: brak pliku' in result.stderr


@pytest.mark.parametrize('value', [None, '', '   '])
def test_missing_or_empty_section_is_error(cards, value):
    if value is None:
        path = cards / 'listing_en.md'
        text = path.read_text(encoding='utf-8').replace('## Short description', '## Other description')
        path.write_text(text, encoding='utf-8')
    else:
        replace_section(cards, 'Short description', value)
    result = run(cards)
    assert result.returncode != 0
    assert 'en Short description: brak lub pusta sekcja' in result.stderr
    assert 'en Full description:' in result.stdout


@pytest.mark.parametrize(('name', 'limit'), [
    ('Title', 30), ('Short description', 80), ('Full description', 4000),
])
def test_exact_limit_and_one_over(cards, name, limit):
    replace_section(cards, name, 'x' * limit)
    result = run(cards)
    assert result.returncode == 0, result.stderr
    assert f'en {name}: {limit}/{limit}' in result.stdout
    replace_section(cards, name, 'x' * (limit + 1))
    result = run(cards)
    assert result.returncode != 0
    assert f'en {name}: przekroczony limit ({limit + 1}/{limit})' in result.stderr


def test_multibyte_unicode_uses_utf16_units_not_utf8_bytes(cards):
    replace_section(cards, 'Title', 'ż' * 30)
    result = run(cards)
    assert result.returncode == 0, result.stderr
    assert 'en Title: 30/30' in result.stdout
    # Znak spoza BMP zajmuje dwie jednostki UTF-16, a nie jedną ani cztery bajty UTF-8.
    replace_section(cards, 'Title', 'ż' * 28 + '😀')
    result = run(cards)
    assert result.returncode == 0, result.stderr
    assert 'en Title: 30/30' in result.stdout
    replace_section(cards, 'Title', 'ż' * 29 + '😀')
    result = run(cards)
    assert result.returncode != 0
    assert 'en Title: przekroczony limit (31/30)' in result.stderr


def test_polish_headings_are_checked(cards):
    path = cards / 'listing_pl.md'
    text = path.read_text(encoding='utf-8').replace('## Tytuł', '## Inny nagłówek')
    path.write_text(text, encoding='utf-8')
    result = run(cards)
    assert result.returncode != 0
    assert 'pl Tytuł: brak lub pusta sekcja' in result.stderr
