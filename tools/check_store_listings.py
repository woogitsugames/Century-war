#!/usr/bin/env python3
"""Sprawdza obecność wszystkich kart i limity tekstów Google Play."""
import argparse
from pathlib import Path
import re
import sys

DEFAULT_ROOT = Path(__file__).resolve().parents[1] / 'docs' / 'store'
LANGUAGES = ('pl', 'en', 'de', 'es', 'fr', 'it', 'pt', 'uk', 'nl', 'tr')
SECTIONS = {
    'pl': ('Tytuł', 'Krótki opis', 'Pełny opis'),
    **{lang: ('Title', 'Short description', 'Full description') for lang in LANGUAGES if lang != 'pl'},
}


def check_listings(root: Path) -> bool:
    """Wypisz liczbę jednostek UTF-16 i zwróć, czy wszystkie karty są poprawne."""
    failed = False
    for lang in LANGUAGES:
        file = root / f'listing_{lang}.md'
        if not file.is_file():
            print(f'{file}: brak pliku', file=sys.stderr)
            failed = True
            continue
        text = file.read_text(encoding='utf-8')
        for name, limit in zip(SECTIONS[lang], (30, 80, 4000)):
            # Nagłówek musi kończyć się wierszem; nie pochłaniaj pustej sekcji.
            match = re.search(r'^## ' + re.escape(name) + r'[^\S\r\n]*\r?\n(.*?)(?=^## |\Z)', text, re.M | re.S)
            value = match.group(1).strip() if match else ''
            length = len(value.encode('utf-16-le')) // 2
            print(f'{lang} {name}: {length}/{limit}')
            if not value:
                print(f'{lang} {name}: brak lub pusta sekcja', file=sys.stderr)
                failed = True
            elif length > limit:
                print(f'{lang} {name}: przekroczony limit ({length}/{limit})', file=sys.stderr)
                failed = True
    return not failed


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', nargs='?', type=Path, default=DEFAULT_ROOT,
                        help='katalog z plikami listing_*.md (domyślnie docs/store)')
    args = parser.parse_args(argv)
    return 0 if check_listings(args.directory) else 1


if __name__ == '__main__':
    sys.exit(main())
