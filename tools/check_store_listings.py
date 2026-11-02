#!/usr/bin/env python3
"""Sprawdza obecność wszystkich kart i limity tekstów Google Play."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / 'docs' / 'store'
languages = ('pl', 'en', 'de', 'es', 'fr', 'it', 'pt', 'uk', 'nl', 'tr')
sections = {
    'pl': ('Tytuł', 'Krótki opis', 'Pełny opis'),
    **{lang: ('Title', 'Short description', 'Full description') for lang in languages if lang != 'pl'},
}
failed = False
for lang in languages:
    file = root / f'listing_{lang}.md'
    if not file.is_file():
        print(f'{file}: brak pliku', file=sys.stderr)
        failed = True
        continue
    text = file.read_text(encoding='utf-8')
    for name, limit in zip(sections[lang], (30, 80, 4000)):
        match = re.search(r'^## ' + re.escape(name) + r'\s*\n(.*?)(?=^## |\Z)', text, re.M | re.S)
        value = match.group(1).strip() if match else ''
        # Google Play limits are UTF-16 code units; this also handles emoji safely.
        length = len(value.encode('utf-16-le')) // 2
        print(f'{lang} {name}: {length}/{limit}')
        if not value or length > limit:
            failed = True
if failed:
    sys.exit(1)
