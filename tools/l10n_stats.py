#!/usr/bin/env python3
"""Statystyki statycznej tabeli L.kt; wskazówki wymagają przeglądu człowieka."""
import argparse
from pathlib import Path
import re
import sys

LANGS = ('pl', 'en', 'de', 'es', 'fr', 'it', 'pt', 'uk', 'nl', 'tr')
ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / 'app/src/main/java/com/centurywar/engine/L.kt'
# Dokładne napisy własne, jednostki i symbole nie wymagają przekładu.
EXCEPTIONS = frozenset({'Android', 'Century War', 'CENTURY WAR', 'Google Play', 'Google Play Games',
                        'Play Games', 'TalkBack', 'Galaxy Fold', 'XP', 'HP', 'FPS', 'DPS',
                        'OK', 'UTC', 'AI', 'ID', 'HUD', 'API', 'PNG', 'JSON', 'HTTPS', 'URL'})
PART = re.compile(r'\bprivate\s+fun\s+part(\d+)\s*\(')
STRING = r'"(?:\\.|[^"\\])*"'
ENTRY = re.compile(r'(' + STRING + r')\s+to\s+t\s*\(\s*((?:' + STRING + r'\s*,?\s*)+)\)', re.S)
LITERAL = re.compile(STRING)
# Kotlin dopuszcza zarówno \$ jak i ${'$'} wewnątrz literału.
PLACEHOLDER = re.compile(r'%(?:\d+\$)?(?:\.\d+)?[sdf]')


def unquote(literal):
    s = literal[1:-1].replace("${'$'}", '$')
    def sub(m):
        c = m.group(1)
        if c.startswith('u'):
            return chr(int(c[1:], 16))
        return {'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', '"': '"', "'": "'", '\\': '\\', '$': '$'}.get(c, '\\' + c)
    return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', sub, s)


def parse(source):
    """Zwróć części i wpisy; nierozpoznanego wywołania nie pomijaj po cichu."""
    # Usuwaj komentarze wy??cznie poza litera?ami, aby przyk?ad w komentarzu nie by? wpisem.
    token = re.compile(STRING + r'|//[^\n]*|/\*.*?\*/', re.S)
    source = token.sub(lambda m: m.group() if m.group().startswith(chr(34)) else '', source)
    parts = list(PART.finditer(source))
    if not parts:
        raise ValueError('Nie znaleziono funkcji partNNN')
    counts, entries, seen = {}, [], set()
    for i, part in enumerate(parts):
        name = 'part' + part.group(1)
        if name in counts:
            raise ValueError(f'Powtórzona część: {name}')
        chunk = source[part.end():parts[i + 1].start() if i + 1 < len(parts) else len(source)]
        matches = list(ENTRY.finditer(chunk))
        if len(matches) != len(re.findall(r'\bto\s+t\s*\(', chunk)):
            raise ValueError(f'Nierozpoznany wpis w {name}; wymagane literały Kotlin')
        counts[name] = len(matches)
        for m in matches:
            key = unquote(m.group(1))
            if key in seen:
                raise ValueError(f'Powtórzony klucz: {key}')
            seen.add(key)
            vals = [unquote(x.group()) for x in LITERAL.finditer(m.group(2))]
            if len(vals) != len(LANGS):
                raise ValueError(f'{key}: oczekiwano 10 języków, znaleziono {len(vals)}')
            entries.append((name, key, dict(zip(LANGS, vals))))
    return counts, entries


def spec(text):
    return (PLACEHOLDER.findall(text.replace('%%', '')), text.count('%%'))


def issues(key, values, lang, ratio=1.5):
    text, en, pl = values[lang], values['en'], values['pl']
    found = []
    en_forms = en.split('|')
    if any(spec(form) != spec(en_forms[min(i, len(en_forms) - 1)])
           for i, form in enumerate(text.split('|'))):
        found.append('placeholdery')
    if '  ' in text:
        found.append('podwójna spacja')
    # Typografia francuska celowo używa spacji przed dwukropkiem i innymi znakami.
    if lang != 'fr' and re.search(r'[ \u00a0]+[:;!?]', text):
        found.append('spacja przed interpunkcją')
    if lang != 'en' and text == en and text.strip() and text.strip() not in EXCEPTIONS:
        found.append('identyczne z EN')
    # Krótkie etykiety zawyżają stosunek bez dowodu ucięcia; rozpatrz oba punkty odniesienia.
    references = [(code, ref) for code, ref in (('PL', pl), ('EN', en)) if code.lower() != lang and len(ref) >= 8]
    ratios = [(len(text) / len(ref), code) for code, ref in references] if text else []
    if ratios:
        worst, code = max(ratios)
        if worst > ratio:
            found.append(f'długość {worst:.2f}×{code}')
    return found


def summarize(counts, entries, languages, threshold, top):
    output = ['# Statystyki lokalizacji', '', f'Klucze: {len(entries)}; części: {len(counts)}.',
              'Wyjątki identyczności: ' + ', '.join(sorted(EXCEPTIONS)) + '.',
              f'Próg długości: > {threshold:g}× PL lub EN, odniesienie min. 8 znaków. Wskazówki nie dowodzą błędu ani ucięcia.',
              '', '## Liczba kluczy na część', '']
    for part, count in sorted(counts.items(), key=lambda item: int(item[0][4:])):
        output.append(f'- {part}: {count}')
    for lang in languages:
        candidates = []
        for part, key, values in entries:
            flags = issues(key, values, lang, threshold)
            if flags:
                rank = min((0 if x == 'placeholdery' else 1 if 'spacja' in x else
                            2 if x == 'identyczne z EN' else 3) for x in flags)
                candidates.append((rank, key, part, flags, values[lang]))
        candidates.sort(key=lambda row: (row[0], row[1]))
        output += ['', f'## {lang} — TOP {top} z {len(candidates)} podejrzeń', '']
        if not candidates:
            output.append('- Brak podejrzeń.')
        for _, key, part, flags, value in candidates[:top]:
            display = value.replace('\n', ' ↵ ').replace('|', ' / ')
            if len(display) > 130:
                display = display[:127] + '…'
            output.append(f'- `{key}` ({part}): {", ".join(flags)} — {display}')
    return '\n'.join(output) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', nargs='?', type=Path, default=DEFAULT, help='ścieżka do L.kt')
    parser.add_argument('--markdown', action='store_true', help='wynik w formacie Markdown')
    parser.add_argument('--lang', choices=LANGS, help='ogranicz szczegółowe wyniki do jednego języka')
    parser.add_argument('--threshold', type=float, default=1.5, help='próg stosunku długości (domyślnie 1,5)')
    parser.add_argument('--top', type=int, default=30, help='maksimum wpisów na język (domyślnie 30)')
    args = parser.parse_args(argv)
    if args.threshold <= 0 or args.top < 1:
        parser.error('próg i liczba wyników muszą być dodatnie')
    try:
        counts, entries = parse(args.path.read_text(encoding='utf-8-sig'))
    except (OSError, ValueError) as e:
        print(f'Błąd: {e}', file=sys.stderr)
        return 2
    report = summarize(counts, entries, (args.lang,) if args.lang else LANGS, args.threshold, args.top)
    if not args.markdown:
        report = re.sub(r'^#{1,2} ', '', report, flags=re.M).replace('`', '')
    try:
        print(report, end='')
    except BrokenPipeError:
        return 0
    return 0


if __name__ == '__main__':
    sys.exit(main())
