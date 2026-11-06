from pathlib import Path
import os
import shutil
import subprocess
import sys

import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'tools' / 'check_doc_links.py'
FIXTURE = ROOT / 'tests' / 'fixtures' / 'doc_links'


@pytest.fixture
def repo(tmp_path):
    path = tmp_path / 'repo ze spacją'
    shutil.copytree(FIXTURE, path)
    return path


def run(repo, *args):
    return subprocess.run([sys.executable, str(SCRIPT), '--root', str(repo), *args],
                          capture_output=True, text=True, encoding='utf8',
                          env=dict(os.environ, PYTHONIOENCODING='utf-8'))


def append(repo, line):
    with (repo / 'README.md').open('a', encoding='utf-8') as out:
        out.write('\n' + line + '\n')


def test_valid_links_unicode_duplicate_anchor_backticks_and_no_network(repo):
    result = run(repo, '--markdown')
    assert result.returncode == 0, result.stdout + result.stderr
    assert 'błędów: 0' in result.stdout
    assert 'https://example.org/path' in result.stdout
    assert 'missing-in-fence' not in result.stdout


def test_missing_file_anchor_and_repo_path(repo):
    append(repo, '[brak](docs/missing.md) [kotwica](docs/guide.md#brak) `tools/no.py`')
    result = run(repo)
    assert result.returncode == 1
    assert 'docs/missing.md: brak celu' in result.stdout
    assert 'docs/guide.md#brak: brak kotwicy' in result.stdout
    assert 'tools/no.py: brak ścieżki' in result.stdout
    assert 'README.md:' in result.stdout


def test_allow_list_exact_source_and_target(repo, tmp_path):
    append(repo, '[A](docs/no.md) [B](docs/other.md)')
    allowed = tmp_path / 'exceptions.txt'
    allowed.write_text('# komentarz\nREADME.md:docs/no.md\n', encoding='utf8')
    result = run(repo, '--allow-list', str(allowed))
    assert result.returncode == 1
    assert 'docs/no.md: brak celu' not in result.stdout
    assert 'docs/other.md: brak celu' in result.stdout
    allowed.write_text('README.md\n', encoding='utf8')
    result = run(repo, '--allow-list', str(allowed))
    assert result.returncode == 0
    assert 'wyjątków: 2' in result.stdout


def test_exclude_build_git_and_fenced_examples(repo):
    for name in ('build', '.git', 'docs/build', '.pytest_cache'):
        target = repo / name
        target.mkdir(parents=True, exist_ok=True)
        (target / 'bad.md').write_text('[x](no.md)', encoding='utf8')
    assert run(repo).returncode == 0


def test_fragment_only_encoded_space_and_reference_style(repo):
    (repo / 'docs' / 'plik z nawiasem (1).md').write_text('# Łączność\n', encoding='utf8')
    append(repo, '[lokalnie](#strona-główna) [plik](docs/plik%20z%20nawiasem%20(1).md#łączność) [odnośnik][src]\n\n[src]: docs/guide.md#zażółć-więc')
    result = run(repo)
    assert result.returncode == 0, result.stdout + result.stderr
    append(repo, '[brak definicji][nigdzie]')
    assert 'brak definicji odwołania' in run(repo).stdout


def test_filter_to_subdirectory(repo):
    append(repo, '[brak](missing.md)')
    assert run(repo).returncode == 1
    assert run(repo, 'docs').returncode == 0


def test_missing_input_and_allow_list_report_errors(repo):
    assert run(repo, 'brak').returncode == 2
    assert run(repo, '--allow-list', str(repo / 'missing-list.txt')).returncode == 2


def test_backtick_line_references_and_ellipses(repo):
    append(repo, '`docs/guide.md:2-5` `docs/guide.md:2,4` `app/src/.../Example.kt`')
    result = run(repo)
    assert result.returncode == 0, result.stdout
    append(repo, '`tools/missing.py:20`')
    result = run(repo)
    assert result.returncode == 1
    assert 'tools/missing.py:20: brak ścieżki' in result.stdout
