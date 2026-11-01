#!/usr/bin/env python3
"""Podstawia syntetyczne ID Play Games w pliku properties tylko na potrzeby CI.

Smoke-test release (R8/manifest) wymaga technicznie pełnej konfiguracji, a generator
w release odrzuca wartości REPLACE_ME. ID nie są produkcyjne i nie trafiają do repozytorium.
Ten sam skrypt jest używany dla bieżącego commita i worktree bazy PR, więc obie
kompilacje dostają identyczny zestaw (porównanie rozmiaru jest uczciwe).
"""
from __future__ import annotations

import sys
from pathlib import Path

SYNTHETIC_APP_ID = '123456789012'


def substitute(text: str) -> str:
    lines = []
    next_id = 0
    for line in text.splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            if key == 'app_id':
                value = SYNTHETIC_APP_ID
            elif value.strip().startswith('REPLACE_ME'):
                next_id += 1
                value = 'CgkI' + str(next_id).zfill(12) + 'EAIQAg'
            line = key + '=' + value
        lines.append(line)
    return '\n'.join(lines) + '\n'


def main(argv: list[str]) -> int:
    paths = argv[1:] or ['docs/store/play_games_ids.properties']
    for raw in paths:
        path = Path(raw)
        if not path.is_file():
            # Starsza baza może nie mieć pliku ID; wtedy nie ma czego podstawiać.
            print(f'Pominięto (brak pliku): {path}')
            continue
        path.write_text(substitute(path.read_text(encoding='utf-8')), encoding='utf-8')
        print(f'Podstawiono syntetyczne ID: {path}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
