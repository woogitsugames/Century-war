#!/usr/bin/env python3
"""Sprawdza lokalne linki Markdown i wybrane ścieżki repozytorium, bez sieci."""
import argparse
from collections import defaultdict
from fnmatch import fnmatchcase
import html
import os
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

DEFAULT_ROOT = Path(__file__).resolve().parents[1]
# Linki inline, także obrazy. Wsparcie opcjonalnego tytułu i ujęcia adresu w <>.
LINK = re.compile(r'!?\[[^\]\n]*\]\(\s*(<[^>\n]+>|(?:\\.|[^\s()]|\([^()]*\))+)(?:\s+["\'][^\n]*?["\'])?\s*\)')
REFERENCE_DEF = re.compile(r'^ {0,3}\[([^]\n]+)\]:\s*(<[^>\n]+>|\S+)')
REFERENCE_USE = re.compile(r'!?\[([^]\n]+)\]\[([^]\n]*)\]')
CODE = re.compile(r'(?<!`)`([^`\n]+)`(?!`)')
ROOT_PATH = re.compile(r'^(?:docs/|tools/|app/src/)[^\s]+$')
HEADING = re.compile(r'^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$')
FENCE = re.compile(r'^ {0,3}(`{3,}|~{3,})')


def content_lines(text):
    """Nie interpretuj przykładów z bloków kodu ani komentarzy HTML jako linków."""
    fence = None
    in_comment = False
    for number, line in enumerate(text.splitlines(), 1):
        match = FENCE.match(line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
            continue
        if fence:
            continue
        clean = ''
        while line:
            if in_comment:
                _, sep, line = line.partition('-->')
                if not sep:
                    break
                in_comment = False
            else:
                before, sep, rest = line.partition('<!--')
                clean += before
                if not sep:
                    break
                in_comment = True
                line = rest
        yield number, clean


def slug(text):
    text = html.unescape(re.sub(r'<[^>]*>', '', text)).strip().lower()
    text = re.sub(r'\[([^]]+)\]\([^)]*\)', r'\1', text)
    text = ''.join(c for c in text if c.isalnum() or c in '-_ ')
    return text.replace(' ', '-')


def anchors(text):
    seen = defaultdict(int)
    result = set()
    previous = ''
    for _, line in content_lines(text):
        match = HEADING.match(line)
        heading = match.group(1) if match else (previous.strip() if re.match(r'^ {0,3}(?:=+|-+)\s*$', line) else '')
        if heading:
            base = slug(heading)
            index = seen[base]
            seen[base] += 1
            result.add(base if index == 0 else f'{base}-{index}')
        previous = line
    return result


def documents(path):
    if path.is_file():
        return [path] if path.suffix.lower() == '.md' else []
    files = []
    for base, dirs, names in os.walk(path):
        dirs[:] = sorted(d for d in dirs if d not in {'.git', 'build', '.pytest_cache', '__pycache__', '.gradle'})
        files.extend(Path(base) / n for n in names if n.lower().endswith('.md'))
    return sorted(files)


def inspect_file(file, root):
    text = file.read_text(encoding='utf-8-sig')
    try:
        source = file.relative_to(root).as_posix()
    except ValueError:
        source = str(file)
    missing, external = [], []
    lines = list(content_lines(text))
    references = {}
    for _, line in lines:
        definition = REFERENCE_DEF.match(line)
        if definition:
            references.setdefault(' '.join(definition.group(1).lower().split()), definition.group(2))
    for number, line in lines:
        if REFERENCE_DEF.match(line):
            continue
        inline_code = [m.span() for m in CODE.finditer(line)]
        links = [(m.start(), m.group(1)) for m in LINK.finditer(line)]
        for m in REFERENCE_USE.finditer(line):
            ref = ' '.join((m.group(2) or m.group(1)).lower().split())
            if ref in references:
                links.append((m.start(), references[ref]))
            elif not any(a <= m.start() < b for a, b in inline_code):
                missing.append((source, number, ref, 'brak definicji odwołania'))
        for position, destination in sorted(links):
            if any(a <= position < b for a, b in inline_code):
                continue
            raw = destination.strip('<>')
            raw = re.sub(r'\\([() ])', r'\1', raw)
            parsed = urlsplit(raw)
            if parsed.scheme.lower() in ('http', 'https'):
                external.append((source, number, raw))
                continue
            if parsed.scheme or raw.startswith('//'):
                continue
            path = unquote(parsed.path)
            target = (root / path.lstrip('/')) if path.startswith('/') else (file.parent / path if path else file)
            if not target.exists():
                missing.append((source, number, raw, 'brak celu'))
            elif parsed.fragment:
                fragment = unquote(parsed.fragment)
                if not target.is_file() or target.suffix.lower() != '.md':
                    continue  # Kotwic innych formatów nie da się sprawdzić statycznie.
                if fragment not in anchors(target.read_text(encoding='utf-8-sig')):
                    missing.append((source, number, raw, 'brak kotwicy'))
        for m in CODE.finditer(line):
            raw = m.group(1)
            if not ROOT_PATH.fullmatch(raw):
                continue
            # Wzorce i szablony nie są konkretnymi ścieżkami.
            if any(char in raw for char in '*?{}$<>') or '...' in raw or '…' in raw:
                continue
            path = re.sub(r':\d+(?:[-–,]\d+)*$', '', raw.rstrip('.,;:'))
            if not (root / path).exists():
                missing.append((source, number, raw, 'brak ścieżki'))
    return missing, external


def allowed(source, target, patterns):
    return any(fnmatchcase(source, p) or fnmatchcase(source + ':' + target, p) for p in patterns)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', nargs='?', default='.', help='plik lub katalog do sprawdzenia (domyślnie repozytorium)')
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT, help='katalog główny repozytorium')
    parser.add_argument('--allow-list', type=Path, help='plik UTF-8: wzorzec źródła lub źródło:cel w każdym wierszu')
    parser.add_argument('--markdown', action='store_true', help='wynik Markdown')
    args = parser.parse_args(argv)
    root = args.root.resolve()
    scan = Path(args.path)
    scan = (root / scan).resolve() if not scan.is_absolute() else scan.resolve()
    if not scan.exists():
        print(f'Brak ścieżki wejściowej: {scan}', file=sys.stderr)
        return 2
    try:
        patterns = [] if args.allow_list is None else [s.strip() for s in args.allow_list.read_text(encoding='utf-8-sig').splitlines() if s.strip() and not s.lstrip().startswith('#')]
        errors, external, ignored = [], [], []
        files = documents(scan)
        for file in files:
            bad, urls = inspect_file(file, root)
            external.extend(urls)
            for row in bad:
                (ignored if allowed(row[0], row[2], patterns) else errors).append(row)
    except (OSError, UnicodeError, ValueError) as e:
        print(f'Błąd odczytu: {e}', file=sys.stderr)
        return 2
    if args.markdown:
        print('# Sprawdzenie odnośników\n')
        print(f'Plików: {len(files)}; błędów: {len(errors)}; wyjątków: {len(ignored)}; zewnętrznych: {len(external)}.\n')
        print('## Brakujące cele i kotwice')
        last = None
        for source, number, target, reason in errors:
            if source != last:
                print(f'\n### {source}')
                last = source
            print(f'- Linia {number}: `{target}` — {reason}')
        if not errors:
            print('\nBrak błędów.')
        print('\n## Linki zewnętrzne (bez sprawdzania sieci)\n')
        for source, number, url in external:
            print(f'- {source}:{number}: {url}')
    else:
        for source, number, target, reason in errors:
            print(f'{source}:{number}: {target}: {reason}')
        for source, number, url in external:
            print(f'{source}:{number}: zewnętrzny {url}')
        print(f'Plików: {len(files)}; błędów: {len(errors)}; wyjątków: {len(ignored)}; zewnętrznych: {len(external)}.')
    return 1 if errors else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except BrokenPipeError:
        # Zamknięcie potoku przez head nie oznacza poprawności sprawdzanych linków.
        sys.exit(1)
