import importlib.util
import os
from pathlib import Path
import subprocess
import sys

import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'tools' / 'l10n_stats.py'
FIXTURE = ROOT / 'tests' / 'fixtures' / 'l10n' / 'L.kt'
specification = importlib.util.spec_from_file_location('l10n_stats', SCRIPT)
stats = importlib.util.module_from_spec(specification)
specification.loader.exec_module(stats)


@pytest.fixture
def parsed():
    return stats.parse(FIXTURE.read_text(encoding='utf-8-sig'))


def test_all_parts_and_multiline_entries(parsed):
    counts, entries = parsed
    assert counts == {'part0': 7, 'part12': 1}
    assert len(entries) == 8
    assert next(values['en'] for _, key, values in entries if key == 'parentheses') == 'Point (test)'


def test_comment_and_string_with_comment_marker_are_not_confused():
    raw = FIXTURE.read_text(encoding='utf-8-sig')
    raw = raw.replace('object L {', 'object L {\n // "fake" to t("pl", "en")\n')
    raw = raw.replace('Point (test)', 'Point (test) // not a comment')
    _, entries = stats.parse(raw)
    assert len(entries) == 8
    assert next(v['en'] for _, k, v in entries if k == 'parentheses').endswith('not a comment')


def test_identical_brand_exception_and_untranslated(parsed):
    _, entries = parsed
    values = {key: val for _, key, val in entries}
    assert 'identyczne z EN' in stats.issues('hello', values['hello'], 'de')
    assert 'identyczne z EN' not in stats.issues('brand', values['brand'], 'de')


def test_placeholders_plurals_and_percent_signs(parsed):
    _, entries = parsed
    values = {key: val for _, key, val in entries}
    assert 'placeholdery' in stats.issues('count', values['count'], 'de')
    assert 'placeholdery' not in stats.issues('plural', values['plural'], 'pl')
    assert 'placeholdery' not in stats.issues('escaped', values['escaped'], 'de')
    assert stats.spec('25% HP and %1$s, %.1f%%') == stats.spec('25% HP %1$s %.1f%%')
    assert stats.spec('x %d %%') != stats.spec('x %d')
    assert stats.spec('%s %d') != stats.spec('%d %s')


def test_language_sensitive_typography_and_length(parsed):
    _, entries = parsed
    values = {key: val for _, key, val in entries}
    assert 'podwójna spacja' in stats.issues('punct', values['punct'], 'de')
    assert 'spacja przed interpunkcją' in stats.issues('punct', values['punct'], 'de')
    assert 'spacja przed interpunkcją' not in stats.issues('punct', values['punct'], 'fr')
    assert any(x.startswith('długość') for x in stats.issues('long', values['long'], 'de'))


def test_incomplete_or_duplicate_entries_are_errors():
    raw = FIXTURE.read_text(encoding='utf-8-sig')
    with pytest.raises(ValueError, match='oczekiwano 10'):
        stats.parse(raw.replace(', "Merhaba"', ''))
    with pytest.raises(ValueError, match='Powtórzony klucz'):
        stats.parse(raw.replace('"escaped"', '"hello"'))


def test_cli_markdown_lang_and_limit():
    result = subprocess.run([sys.executable, str(SCRIPT), str(FIXTURE), '--markdown', '--lang', 'de', '--top', '3'],
                            text=True, encoding='utf8', capture_output=True,
                            env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    assert result.returncode == 0, result.stderr
    assert 'Klucze: 8; części: 2' in result.stdout
    assert '## de — TOP 3' in result.stdout
    assert '## fr —' not in result.stdout
    assert '`count`' in result.stdout


def test_real_table_is_parsed_completely():
    source = stats.DEFAULT.read_text(encoding='utf-8')
    counts, entries = stats.parse(source)
    # Sumy odczytanych części muszą obejmować wszystkie rozpoznane wpisy.
    assert sum(counts.values()) == len(entries)
    assert len(entries) > 1000
    assert counts['part0'] > 0
