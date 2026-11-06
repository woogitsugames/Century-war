#!/usr/bin/env python3
"""Scalanie „Raportów dla testera” Century War (R13, TOP 5 poz. 4).

Wejście:
  * CSV z Formularza Google (kolumna z wklejonym raportem + kolumny formularza:
    pseudonim/kod, który raport, model telefonu, minuty dziennie, co się podobało,
    co przeszkadzało; kolumny wykrywane po nagłówkach, można je wskazać ręcznie);
  * katalog (lub pojedyncze pliki) .txt/.eml z raportami.

Wyjście: Markdown w stylu `docs/reports/test-faleN.md` (tabela per raport, mediany,
bramka G2 z R13 §1: twarde i miękkie) oraz opcjonalnie CSV „tester × dzień”.

Format raportu to dokładnie `app/.../crash/PlaytestReportText.kt`: format 1 (bez linii
`Report format`, wersje bez ankiety testera) albo format 2 (d48, wersje testowe:
`Report format: 2`, `Tester code:`, `Crashes:`, `ANRs:` w nagłówku, `Research levels:` w
`[Progress]`, `Day map (...):` w `[Play time]`). Od d57 format 2 ma też sekcję `[Final survey]`
(ankieta końcowa z 10. dnia: `Recommend (0-10):`, `Liked most:`, `Bothered most:`) zaraz po
`[Tester survey]`; sekcja jest opcjonalna (starsze raporty v2 jej nie mają). Etykiety treści są stałe, po angielsku; po lokalizacji gry zmienia się
tylko temat e-maila (`pt182_subject` w 10 językach) i kod w linii `Language:`. Temat
rozpoznajemy jako początek raportu, gdy tester wkleił e-mail razem z tematem.

Tylko biblioteka standardowa Pythona 3. Uruchomienie:
  python3 tools/playtest_merge.py odpowiedzi.csv raporty/ -o docs/reports/test-fala1.md --fala 1
"""

from __future__ import annotations

import argparse
import csv
import email
import email.policy
import io
import re
import statistics
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Progi bramki G2 (R13 §1, „G2: koniec fali testu zamkniętego”). Propozycje do
# zatwierdzenia: zmiana progu = zmiana tutaj i w R13.
# ---------------------------------------------------------------------------
G2 = {
    "active_testers_min": 12,          # 2.1
    "reports_day3_min": 10,            # 2.4
    "reports_day10_min": 10,           # 2.4
    "median_days_played_min": 6,       # 2.5
    "testers_4days_min": 10,           # 2.5 (osoby z >= 4 dniami)
    "four_days": 4,
    "median_battle_minutes_min": 90,   # 2.5
    "median_sessions_min": 8,          # 2.5
    "tutorial_done_share_min": 0.90,   # 2.6
    "median_tutorial_minutes_max": 20,  # 2.7
    "median_first_win_minutes_max": 20,  # 2.8
    "first_win_share_min": 1.00,       # 2.8 (100% n z 10. dnia)
    "median_stage_min": 8,             # 2.9
    "stage_far": 13,                   # 2.9
    "stage_far_people_min": 3,         # 2.9
    "loss_wall": 5,                    # 2.10
    "loss_wall_share_max": 0.20,       # 2.10
    "surrender_share_max": 0.10,       # 2.11
    "ratings_min": 10,                 # 2.12
    "median_rating_min": 4,            # 2.12
    "low_rating_max": 2,               # 2.12 (ocena 1–2 → pytanie zwrotne)
    "issue_share_max": 0.25,           # 2.13
    "p95_ok_ms": 25.0,                 # 2.14
    "p95_ok_share_min": 0.80,          # 2.14
    "p95_bad_ms": 33.0,                # 2.14
    "p95_bad_devices_max": 2,          # 2.14
    "thermal_hot": 3,                  # 2.14
    "thermal_cool_devices_share_min": 0.80,  # 2.14
    "dropped_bad": 60,                 # 2.14
    "dropped_bad_share_max": 0.10,     # 2.14
    "modes_share_min": 0.50,           # 2.15
}
BAD_ISSUES = ("too hard", "battles too long", "unclear rules")

# ---------------------------------------------------------------------------
# Format z gry (stałe skopiowane z kodu; test pilnuje zgodności z PlaytestReportText.kt).
# ---------------------------------------------------------------------------
HEADER = "Century War - tester report"
PROBLEM_HEADER = "Century War - raport techniczny"  # CrashReportText („Zgłoś problem”)
SECTIONS = ("[Tester survey]", "[Progress]", "[Play time]", "[Modes]", "[Performance]", "[Settings]")
LAST_LABEL = "Battle speed x1/2:"
# Lejek kampanii (d45, R13 TOP 5 poz. 2): sekcja opcjonalna, raporty sprzed d45 jej nie mają.
FUNNEL_SECTION = "[Campaign stages]"
# Ankieta końcowa z 10. dnia (d57; R13 TOP 5 poz. 5) — tylko format 2, opcjonalna.
FINAL_SECTION = "[Final survey]"
FINAL_LIKED = ("campaign", "battles and units", "eras and research", "daily challenges",
               "graphics and sound", "nothing special")
FINAL_ISSUES = ("too hard", "too easy", "battles too long", "unclear rules", "slow progress", "nothing, it's good")
MODES = ("CAMPAIGN", "SKIRMISH", "ENDLESS", "DAILY", "ORDERS")
SCREEN_NAMES = ("Codex", "Research", "Missions", "Replays", "Accessibility",
                "Skirmish", "Endless", "Daily", "Weekly")
SURVEY_ISSUES = ("too hard", "battles too long", "unclear rules", "nothing, it's good")
LANG_CODES = ("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")
# `pt182_subject` z L.kt w kolejności LANG_CODES (temat e-maila wstawiany przez grę).
SUBJECTS = {
    "pl": "Century War - raport testera",
    "en": "Century War - tester report",
    "de": "Century War - Testerbericht",
    "es": "Century War - informe de prueba",
    "fr": "Century War - rapport de test",
    "it": "Century War - rapporto tester",
    "pt": "Century War - relatório de teste",
    "uk": "Century War - звіт тестувальника",
    "nl": "Century War - testrapport",
    "tr": "Century War - test raporu",
}
# `report_subject` („Zgłoś problem”) — rozpoznajemy, żeby nie liczyć go jako raportu testera.
PROBLEM_SUBJECTS = (
    "Century War - raport błędu", "Century War - error report", "Century War - Fehlerbericht",
    "Century War - informe de error", "Century War - rapport d'erreur", "Century War - rapporto errore",
    "Century War - relatório de erro", "Century War - звіт про помилку", "Century War - foutrapport",
    "Century War - hata raporu",
)

# Pola Raportu v2 (R13 TOP 5 poz. 1; d48) — dokładnie jak w PlaytestReportText.build przy
# `testBuild`. „-” = brak wartości (pole pomijane). Każde pole niesie listę kryteriów G2,
# które odblokowuje.
FUTURE_FIELDS = {
    "report_format": (re.compile(r"^Report format:\s*(\d+)"), ("—",)),
    "tester_code": (re.compile(r"^Tester code:\s*([A-Z0-9]{3,8}|-)$"), ("2.4", "łączenie dnia 3 i 10")),
    "crashes": (re.compile(r"^Crashes:\s*(\d+)"), ("2.2",)),
    "anrs": (re.compile(r"^ANRs?:\s*(\d+)"), ("2.2",)),
    "day_map": (re.compile(r"^Day map[^:]*:\s*([01]+|-)$"), ("2.5",)),
    "research_levels": (re.compile(r"^Research levels:\s*(\d+) of \d+, medals \d+$"), ("2.10",)),
}
# Wiersz lejka z PlaytestReportText.appendStage: `S19: 4 tries, 1 win, 0 surr, 52m, cmd 3`
# (etap od 1; „try/tries”, „win/wins”; czas bitew w pełnych minutach; cmd = bitwy z dowódcą).
STAGE_LINE = re.compile(r"^S(\d+): (\d+) (?:try|tries), (\d+) wins?, (\d+) surr, (\d+)m, cmd (\d+)$")
# Etapy ponad limit 15 wierszy (niższe): `(+21 lower stages)`.
STAGE_MORE = re.compile(r"^\(\+(\d+) lower stages?\)$")

DURATION = r"(?:(\d+)h (\d+)m|(\d+)m (\d+)s)"
RE = {
    "version": re.compile(r"^Version:\s*(.+)$"),
    "device": re.compile(r"^Device:\s*(.+)$"),
    "android": re.compile(r"^Android:\s*(.+)$"),
    "language": re.compile(r"^Language:\s*(\S+)"),
    "rating": re.compile(r"^Rating:\s*([1-5])/5(?:\s*\(([^)]*)\))?"),
    "issue": re.compile(r"^Biggest issue:\s*(.+)$"),
    "skipped": re.compile(r"^Skipped(?:\s*\(([^)]*)\))?"),
    "final_nps": re.compile(r"^Recommend \(0-10\):\s*(\d{1,2})(?:\s*\(([^)]*)\))?$"),
    "final_liked": re.compile(r"^Liked most:\s*(.+)$"),
    "final_issue": re.compile(r"^Bothered most:\s*(.+)$"),
    "not_asked": re.compile(r"^Not asked yet"),
    "campaign": re.compile(r"^Campaign: stage (\d+) of (\d+), stars (\d+) of (\d+), difficulty (\w+)"),
    "tutorial": re.compile(r"^Tutorial done: (yes|no) \((.+)\)"),
    "first_win": re.compile(r"^First win: (.+)$"),
    "best_wave": re.compile(r"^Best Endless wave: (\d+)"),
    "win_streak": re.compile(r"^Win streak: (\d+) \(best (\d+)\)"),
    "loss_streak": re.compile(r"^Campaign loss streak: (\d+) \(max (\d+)\)"),
    "login_streak": re.compile(r"^Login streak: (\d+)"),
    "days": re.compile(r"^First day \(UTC\): (\S+), last day: (\S+)"),
    "days_played": re.compile(r"^Days played: (\d+), sessions: (\d+)"),
    "time_in_app": re.compile(r"^Time in app: " + DURATION),
    "battle_time": re.compile(r"^Battle time: " + DURATION),
    "battles": re.compile(r"^Battles: (\d+), wins: (\d+), surrenders: (\d+)"),
    "mode": re.compile(r"^(" + "|".join(MODES) + r"): battles (\d+), wins (\d+), time " + DURATION),
    "screens": re.compile(r"^Screens visited: (.*)$"),
    "recent": re.compile(r"^(\w+)(?: stage (\d+))?, (win|loss|surrender|\?), " + DURATION
                         + r", era (\d+), AI (\d+)%"),
    "quality": re.compile(r"^Quality: (\S+) \(level (-?\d+), reduction now (\d+)/(\d+), GPU (\d+)\)"),
    "perf": re.compile(r"^#(\d+): worst p95 ([0-9.Ee+-]+) ms, max reduction (\d+), "
                       r"dropped steps (\d+), thermal (\d+)"),
    "fps_cap": re.compile(r"^FPS cap: (\S+)"),
    "game_mode": re.compile(r"^Game mode: (\S+)"),
    "palette": re.compile(r"^Palette: (.+)$"),
    "text_size": re.compile(r"^Text size: (\d+)%"),
    "left_handed": re.compile(r"^Left-handed HUD: (yes|no)"),
    "talkback": re.compile(r"^TalkBack: (yes|no)"),
    "reduce": re.compile(r"^Reduce shake/flash: (yes|no)/(yes|no)"),
    "slow": re.compile(r"^Battle speed x1/2: (yes|no)"),
}
# Etykiety, przed którymi wstawiamy podział linii, gdy wklejony tekst stracił nowe linie.
REFLOW_LABELS = list(SECTIONS) + [FUNNEL_SECTION, FINAL_SECTION, "Recommend (0-10):", "Liked most:",
                                   "Bothered most:"] + [
    "Version:", "Device:", "Android:", "Language:", "Rating:", "Biggest issue:", "Not asked yet",
    "Campaign: stage", "Tutorial done:", "First win:", "Best Endless wave:", "Win streak:",
    "Campaign loss streak:", "Login streak:", "First day (UTC):", "Days played:", "Time in app:",
    "Battle time:", "Battles:", "Screens visited:", "Recent battles", "Quality:", "Last sessions",
    "FPS cap:", "Game mode:", "Palette:", "Text size:", "Left-handed HUD:", "TalkBack:",
    "Reduce shake/flash:", LAST_LABEL,
] + [m + ": battles" for m in MODES]


def duration_seconds(m: re.Match, start: int) -> int:
    h, mi, m2, s = (m.group(start + i) for i in range(4))
    if h is not None:
        return int(h) * 3600 + int(mi) * 60
    return int(m2) * 60 + int(s)


def milestone(text: str):
    """„after Xm Ys of battle time” → sekundy; „before this log” → 'before'; „not yet” → None."""
    text = text.strip()
    if text.startswith("before this log"):
        return "before"
    if text.startswith("not yet"):
        return None
    m = re.match(r"after " + DURATION + " of battle time", text)
    return duration_seconds(m, 1) if m else "?"


@dataclass
class Report:
    source: str
    text: str
    # Pola formularza (mogą być puste).
    form: dict = field(default_factory=dict)
    status: str = "ok"               # ok / obcięty / uszkodzony
    problems: list = field(default_factory=list)
    subject_lang: str | None = None  # język tematu e-maila, jeśli był wklejony
    format: int = 1
    f: dict = field(default_factory=dict)       # sparsowane pola
    modes: dict = field(default_factory=dict)   # MODE -> (bitwy, wygrane, sekundy)
    recent: list = field(default_factory=list)
    perf: list = field(default_factory=list)    # (p95, reduction, dropped, thermal)
    stages: dict = field(default_factory=dict)  # etap (od 1) -> (próby, wygrane, kap., minuty, dowódca)
    sections: list = field(default_factory=list)
    day: int | None = None
    day_inferred: bool = False
    rid: str = ""
    key: str = ""

    # Wygodne gettery do bramki.
    def get(self, name, default=None):
        return self.f.get(name, default)


def normalize(text: str) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ").replace("\ufeff", "")
    if text.count("\n") < 5 and "[Progress]" in text:
        # Wklejone w pole jednowierszowe: odtwarzamy podział linii przed znanymi etykietami.
        for label in sorted(set(REFLOW_LABELS), key=len, reverse=True):
            text = re.sub(r"\s+(?=" + re.escape(label) + ")", "\n", text)
        text = re.sub(r"(Recent battles \(newest first\):|30 s battle windows\):|" + re.escape(FUNNEL_SECTION)
                      + r")\s+", "\\1\n", text)
        text = re.sub(r"\s+(?=S\d+: \d+ (?:try|tries), )", "\n", text)
        text = re.sub(r"\s+(?=\(\+\d+ lower stages?\))", "\n", text)
        text = re.sub(r"\s+(?=#\d+: worst p95)", "\n", text)
        text = re.sub(r"\s+(?=(?:" + "|".join(MODES) + r")(?: stage \d+)?, (?:win|loss|surrender))", "\n", text)
    return text


SUBJECT_PREFIX = re.compile(r"^(Subject|Temat|Betreff|Asunto|Objet|Oggetto|Assunto|Тема|Onderwerp|Konu):\s*")


def subject_text(line: str) -> str:
    """Linia bez cytowania „>” i przedrostka „Temat:” (w 10 językach)."""
    return SUBJECT_PREFIX.sub("", line.strip().lstrip(">").strip())


def split_reports(text: str):
    """Dzieli tekst na raporty testera. Zwraca (lista (temat_języka, treść), liczba zgłoszeń problemu)."""
    text = normalize(text)
    lines = text.split("\n")
    starts = []
    for i, line in enumerate(lines):
        s = subject_text(line)
        if s == HEADER:
            starts.append((i, None))
        elif s in SUBJECTS.values():
            lang = next(k for k, v in SUBJECTS.items() if v == s)
            starts.append((i, lang))
    problems = sum(1 for line in lines if line.strip() in (PROBLEM_HEADER,) + PROBLEM_SUBJECTS)
    out = []
    if not starts:
        # Bez nagłówka (obcięty początek): raport, jeśli są sekcje.
        if any(sec in text for sec in SECTIONS):
            out.append((None, text))
        return out, problems
    # Temat tuż przed nagłówkiem treści to ten sam raport.
    merged = []
    for i, lang in starts:
        if merged and lang is None and merged[-1][1] is not None and \
                all(not lines[j].strip() or lines[j].lstrip().startswith(("Subject", "Temat", "From", "To", "Date"))
                    for j in range(merged[-1][0] + 1, i)):
            merged[-1] = (merged[-1][0], merged[-1][1], i)
            continue
        merged.append((i, lang, i))
    for n, (start, lang, _) in enumerate(merged):
        end = merged[n + 1][0] if n + 1 < len(merged) else len(lines)
        body = "\n".join(lines[start:end])
        if any(sec in body for sec in SECTIONS) or "\nVersion:" in body:
            out.append((lang, body))
    return out, problems


def parse_report(body: str, source: str, subject_lang=None) -> Report:
    r = Report(source=source, text=body, subject_lang=subject_lang)
    lines = [ln.strip() for ln in body.split("\n")]
    while lines and not lines[-1]:
        lines.pop()
    has_header = any(ln == HEADER for ln in lines)
    r.sections = [s for s in SECTIONS if s in lines]
    complete = any(ln.startswith(LAST_LABEL) and RE["slow"].match(ln) for ln in lines)
    # Obcięty raport: ostatnia linia mogła zostać ucięta w połowie liczby — nie ufamy jej.
    if not complete and lines:
        lines = lines[:-1]
    section = None
    for ln in lines:
        if ln in SECTIONS or ln in (FUNNEL_SECTION, FINAL_SECTION):
            section = ln
            continue
        if not ln or ln in (HEADER, "Recent battles (newest first):", "-") or ln.startswith("Last sessions"):
            continue
        if section is None and (subject_text(ln) in SUBJECTS.values() or SUBJECT_PREFIX.match(ln)
                                or re.match(r"^(From|To|Date|Od|Do|Data):", ln)):
            continue  # temat i nagłówki e-maila wklejone przed treścią
        matched = False
        for key, (rx, _) in FUTURE_FIELDS.items():
            m = rx.match(ln)
            if m:
                r.f[key] = m.group(1)
                matched = True
        m = STAGE_LINE.match(ln)
        if m:
            r.stages[int(m.group(1))] = tuple(int(m.group(i)) for i in range(2, 7))
            continue
        m = STAGE_MORE.match(ln)
        if m:
            r.f["stages_hidden"] = int(m.group(1))
            continue
        if matched:
            continue
        for key, rx in RE.items():
            m = rx.match(ln)
            if not m:
                continue
            matched = True
            if key in ("version", "device", "android", "palette", "issue", "fps_cap", "game_mode"):
                r.f[key] = m.group(1).strip()
            elif key == "language":
                r.f["language"] = m.group(1)
            elif key == "rating":
                r.f["survey"] = "answered"
                r.f["rating"] = int(m.group(1))
                r.f["survey_day"] = m.group(2)
            elif key == "skipped" and section == "[Tester survey]":
                r.f["survey"] = "skipped"
                r.f["survey_day"] = m.group(1)
            elif key == "skipped" and section == FINAL_SECTION:
                r.f["final"] = "skipped"
                r.f["final_day"] = m.group(1)
            elif key == "not_asked" and section == FINAL_SECTION:
                r.f["final"] = "not asked"
            elif key == "not_asked":
                r.f["survey"] = "not asked"
            elif key == "final_nps":
                v = int(m.group(1))
                if v > 10:
                    r.problems.append("polecenie spoza 0–10: " + ln)
                    continue
                r.f["final"] = "answered"
                r.f["final_nps"] = v
                r.f["final_day"] = m.group(2)
            elif key in ("final_liked", "final_issue"):
                r.f[key] = m.group(1).strip()
            elif key == "campaign":
                r.f["stage"], r.f["stage_count"], r.f["stars"], r.f["stars_max"] = (int(m.group(i)) for i in range(1, 5))
                r.f["difficulty"] = m.group(5)
            elif key == "tutorial":
                r.f["tutorial_done"] = m.group(1) == "yes"
                r.f["tutorial_s"] = milestone(m.group(2))
            elif key == "first_win":
                r.f["first_win_s"] = milestone(m.group(1))
            elif key == "best_wave":
                r.f["best_wave"] = int(m.group(1))
            elif key == "win_streak":
                r.f["win_streak"], r.f["best_win_streak"] = int(m.group(1)), int(m.group(2))
            elif key == "loss_streak":
                r.f["loss_streak"], r.f["max_loss_streak"] = int(m.group(1)), int(m.group(2))
            elif key == "login_streak":
                r.f["login_streak"] = int(m.group(1))
            elif key == "days":
                r.f["first_day"], r.f["last_day"] = m.group(1), m.group(2)
            elif key == "days_played":
                r.f["days_played"], r.f["sessions"] = int(m.group(1)), int(m.group(2))
            elif key == "time_in_app":
                r.f["app_s"] = duration_seconds(m, 1)
            elif key == "battle_time":
                r.f["battle_s"] = duration_seconds(m, 1)
            elif key == "battles":
                r.f["battles"], r.f["wins"], r.f["surrenders"] = (int(m.group(i)) for i in range(1, 4))
            elif key == "mode":
                r.modes[m.group(1)] = (int(m.group(2)), int(m.group(3)), duration_seconds(m, 4))
            elif key == "screens":
                v = m.group(1).strip()
                r.f["screens"] = [] if v == "-" else [s.strip() for s in v.split(",") if s.strip()]
            elif key == "recent":
                r.recent.append((m.group(1), int(m.group(2)) if m.group(2) else None, m.group(3),
                                 duration_seconds(m, 4), int(m.group(8)), int(m.group(9))))
            elif key == "quality":
                r.f["quality"] = m.group(1)
                r.f["quality_level"] = int(m.group(2))
            elif key == "perf":
                try:
                    p95 = float(m.group(2))
                except ValueError:
                    r.problems.append("nieczytelne p95: " + ln)
                    continue
                r.perf.append((p95, int(m.group(3)), int(m.group(4)), int(m.group(5))))
            elif key == "text_size":
                r.f["text_size"] = int(m.group(1))
            elif key in ("left_handed", "talkback", "slow"):
                r.f[key] = m.group(1) == "yes"
            elif key == "reduce":
                r.f["reduce_shake"], r.f["reduce_flash"] = m.group(1) == "yes", m.group(2) == "yes"
            break
        if not matched:
            r.problems.append("nieznana linia: " + ln[:80])
    for key in ("tester_code", "day_map"):
        if r.f.get(key) == "-":
            del r.f[key]
    if "report_format" in r.f:
        r.format = int(r.f["report_format"])
    # Status.
    missing = [s for s in SECTIONS if s not in r.sections]
    if not complete:
        r.status = "obcięty"
        r.problems.insert(0, "brak końca raportu (" + LAST_LABEL + ")"
                          + ("; brak sekcji: " + ", ".join(missing) if missing else ""))
    if not has_header:
        r.problems.insert(0, "brak nagłówka „" + HEADER + "”")
    core = ("days_played", "battle_s", "battles", "stage")
    if complete and (missing or any(k not in r.f for k in core)):
        r.status = "uszkodzony"
        lost = [k for k in core if k not in r.f]
        r.problems.insert(0, "brak sekcji/pól: " + ", ".join(missing + lost))
    elif complete and r.problems:
        r.status = "uszkodzony"
    if not has_header and r.status == "ok":
        r.status = "uszkodzony"
    return r


# ---------------------------------------------------------------------------
# Wejście: CSV i pliki.
# ---------------------------------------------------------------------------
COLUMN_HINTS = {
    "timestamp": ("sygnatura czasowa", "timestamp", "znacznik czasu"),
    "tester": ("pseudonim", "kod testera", "nick", "tester code", "pseudonym", "kod"),
    "which": ("który raport", "ktory raport", "which report", "dzień testu", "test day"),
    "model": ("model telefonu", "model", "phone", "telefon"),
    "minutes": ("minut", "minutes"),
    "liked": ("podobało", "podobalo", "liked"),
    "disliked": ("przeszkadza", "nie działało", "disliked", "problem"),
    "report": ("wklej", "raport dla testera", "paste", "report"),
}


def detect_columns(header, rows, overrides):
    cols = {}
    low = [h.strip().lower() for h in header]
    for role, hints in COLUMN_HINTS.items():
        if role in overrides:
            continue
        for hint in hints:
            idx = next((i for i, h in enumerate(low) if hint in h and i not in cols.values()), None)
            if idx is not None:
                cols[role] = idx
                break
    # Kolumna raportu: ta, w której najczęściej są sekcje raportu (pewniejsze niż nagłówek).
    best, best_hits = None, 0
    for i in range(len(header)):
        hits = sum(1 for row in rows if i < len(row) and ("[Progress]" in row[i] or HEADER in row[i]))
        if hits > best_hits:
            best, best_hits = i, hits
    if best is not None:
        cols["report"] = best
        for role in list(cols):
            if role != "report" and cols[role] == best:
                del cols[role]
    for role, name in overrides.items():
        if name in header:
            cols[role] = header.index(name)
        elif name.isdigit():
            cols[role] = int(name)
        else:
            raise SystemExit(f"Nie ma kolumny {name!r} w CSV (nagłówki: {header})")
    return cols


def read_csv(path: Path, overrides):
    raw = path.read_bytes().decode("utf-8-sig", errors="replace")
    rows = list(csv.reader(io.StringIO(raw)))
    if not rows:
        return [], 0, []
    header, rows = rows[0], rows[1:]
    cols = detect_columns(header, rows, overrides)
    reports, problems, notes = [], 0, []
    if "report" not in cols:
        notes.append(f"{path.name}: nie znaleziono kolumny z raportem")
        return reports, problems, notes
    for n, row in enumerate(rows, start=2):
        form = {role: row[i].strip() for role, i in cols.items() if i < len(row) and role != "report"}
        cell = row[cols["report"]] if cols["report"] < len(row) else ""
        found, prob = split_reports(cell)
        problems += prob
        if not found:
            if cell.strip() and not prob:
                notes.append(f"{path.name}:{n}: w kolumnie raportu nie ma „Raportu dla testera”")
            continue
        for k, (lang, body) in enumerate(found):
            r = parse_report(body, f"{path.name}:{n}" + (f"#{k + 1}" if len(found) > 1 else ""), lang)
            r.form = form
            reports.append(r)
    return reports, problems, notes


def read_text_file(path: Path) -> str:
    data = path.read_bytes()
    if path.suffix.lower() == ".eml":
        msg = email.message_from_bytes(data, policy=email.policy.default)
        parts = []
        subject = str(msg.get("Subject", "") or "")
        if subject:
            parts.append(subject)
        body = msg.get_body(preferencelist=("plain",))
        if body is not None:
            parts.append(body.get_content())
        return "\n".join(parts)
    return data.decode("utf-8-sig", errors="replace")


def read_inputs(paths, overrides):
    reports, problems, notes = [], 0, []
    for p in paths:
        p = Path(p)
        files = sorted(x for x in p.rglob("*") if x.is_file()) if p.is_dir() else [p]
        for f in files:
            suffix = f.suffix.lower()
            if suffix == ".csv":
                r, prob, n = read_csv(f, overrides)
                reports += r; problems += prob; notes += n
            elif suffix in (".txt", ".eml", ".md", ""):
                found, prob = split_reports(read_text_file(f))
                problems += prob
                for k, (lang, body) in enumerate(found):
                    reports.append(parse_report(body, f.name + (f"#{k + 1}" if len(found) > 1 else ""), lang))
                if not found and not prob:
                    notes.append(f"{f.name}: brak „Raportu dla testera”")
    return reports, problems, notes


# ---------------------------------------------------------------------------
# Dzień raportu i łączenie osób.
# ---------------------------------------------------------------------------
def day_from_dates(r: Report):
    a, b = r.get("first_day"), r.get("last_day")
    try:
        import datetime
        d1, d2 = datetime.date.fromisoformat(a), datetime.date.fromisoformat(b)
        return (d2 - d1).days + 1
    except (TypeError, ValueError):
        return None


def assign_day(r: Report, day_split: int):
    which = (r.form.get("which") or "").lower()
    m = re.search(r"\d+", which)
    if m:
        r.day = int(m.group())
        return
    if which and any(w in which for w in ("problem", "inny", "other")):
        r.day = None
        return
    m = DAY_TOKEN.search(Path(r.source.split(":")[0].split("#")[0]).stem.lower())
    if m:
        r.day = int(re.search(r"\d+", m.group()).group())
        return
    span = day_from_dates(r)
    if span is not None:
        r.day = 3 if span < day_split else 10
        r.day_inferred = True


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").strip().lower())


DAY_TOKEN = re.compile(r"(?:^|(?<=[^a-ząćęłńóśźż]))(?:dzien|dzień|day|d)[-_ ]?\d+(?!\d)")


def file_identity(r: Report) -> str:
    """Pseudonim z nazwy pliku: „kasia-dzien3.txt” → „kasia” (tylko dla plików, nie CSV)."""
    if ":" in r.source:
        return ""
    stem = Path(r.source.split("#")[0]).stem.lower()
    return re.sub(r"[-_ ]+$|^[-_ ]+", "", DAY_TOKEN.sub("", stem))


def assign_keys(reports):
    """Klucz osoby: kod testera (Raport v2) > pseudonim (formularz albo nazwa pliku) + model >
    brak tożsamości (model + pierwszy dzień — tylko do łączenia, nigdy do odrzucania duplikatów)."""
    for r in reports:
        model = norm(r.get("device") or r.form.get("model", ""))
        who = norm(r.form.get("tester", "")) or file_identity(r)
        if r.get("tester_code"):
            r.key = "kod:" + r.get("tester_code")
        elif who:
            r.key = "pseud:" + who + "|" + model
        else:
            r.key = "model:" + model + "|" + norm(r.get("first_day", ""))


def dedupe(reports):
    """Ten sam tester i dzień: zostaje raport z największym czasem bitew (najnowszy stan).
    Bez kodu i pseudonimu odrzucamy tylko identyczny tekst — dwie osoby z tym samym
    telefonem i dniem startu to częsty przypadek."""
    best, dropped = {}, []
    for r in reports:
        k = (r.key, r.day) if not r.key.startswith("model:") else ("text", r.text.strip())
        r._dedupe_key = k
        cur = best.get(k)
        if cur is None:
            best[k] = r
        elif (r.get("battle_s") or 0, r.get("battles") or 0) > (cur.get("battle_s") or 0, cur.get("battles") or 0):
            dropped.append(cur); best[k] = r
        else:
            dropped.append(r)
    kept = [r for r in reports if best.get(r._dedupe_key) is r]
    return kept, dropped


# ---------------------------------------------------------------------------
# Bramka G2.
# ---------------------------------------------------------------------------
OK, FAIL, NA = "OK", "NIE", "nie do policzenia"


@dataclass
class Check:
    num: str
    name: str
    hard: bool
    value: str
    threshold: str
    verdict: str
    note: str = ""


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def fmt_num(v, digits=1):
    if v is None:
        return "—"
    if isinstance(v, float) and not v.is_integer():
        return f"{v:.{digits}f}"
    return str(int(v))


def pct(a, b):
    return "—" if not b else f"{a}/{b} ({100.0 * a / b:.0f}%)"


def verdict(cond):
    return NA if cond is None else (OK if cond else FAIL)


def funnel_note(reports, top=3):
    """2.10: etapy z największą liczbą nieudanych prób (suma lejków) i dowódca na 19 (d37)."""
    tot = {}
    for r in reports:
        for st, (tries, wins, surr, mins, cmd) in r.stages.items():
            a = tot.setdefault(st, [0, 0, 0, 0, 0, 0])
            a[0] += tries; a[1] += wins; a[2] += surr; a[3] += mins; a[4] += cmd; a[5] += 1
    walls = sorted(tot.items(), key=lambda kv: (-(kv[1][0] - kv[1][1]), -kv[0]))[:top]
    parts = [f"S{st}: {a[0] - a[1]} nieudanych z {a[0]} prób ({a[5]} os.)" for st, a in walls]
    s19 = tot.get(19)
    if s19:
        parts.append(f"dowódca na S19: {s19[4]}/{s19[0]} bitew")
    hidden = sum(1 for r in reports if r.get("stages_hidden"))
    if hidden:
        parts.append(f"{hidden} raport(ów) z niższymi etapami poza limitem 15 wierszy")
    return "lejek: " + "; ".join(parts)


def evaluate(day3, day10, base, external):
    """day3/day10: raporty z dni 3 i 10; base: zbiór do kryteriów „n” (dzień 10, inaczej wszystkie)."""
    g = G2
    checks = []
    n = len(base)

    at = external.get("active_testers")
    checks.append(Check("2.1", "Testerzy z nieprzerwanym opt-in", True,
                        "—" if at is None else str(at), f"≥ {g['active_testers_min']} przez 14 dni",
                        verdict(None if at is None else at >= g["active_testers_min"]),
                        "Play Console; podaj `--active-testers`" if at is None else "z Play Console (`--active-testers`)"))
    crashes = [int(r.get("crashes")) + int(r.get("anrs") or 0) for r in base if r.get("crashes") is not None]
    cg = external.get("crash_groups")
    if cg is not None:
        checks.append(Check("2.2", "Powtarzalne awarie i ANR", True, str(cg), "0 otwartych grup",
                            verdict(cg == 0), "z Play Console (`--crash-groups`)"
                            + (f"; licznik w raportach: {sum(crashes)}" if crashes else "")))
    else:
        checks.append(Check("2.2", "Powtarzalne awarie i ANR", True,
                            f"suma w raportach: {sum(crashes)}" if crashes else "—", "0 otwartych grup", NA,
                            "raporty formatu 1 nie liczą awarii/ANR (format 2: `Crashes:`/`ANRs:`); grupy z PC: `--crash-groups`"))
    pl = external.get("prelaunch_crashes")
    checks.append(Check("2.3", "Raport przedpremierowy ostatniej wersji", True, "—" if pl is None else str(pl),
                        "0 awarii i ANR", verdict(None if pl is None else pl == 0),
                        "Play Console; podaj `--prelaunch-crashes`" if pl is None else "z Play Console"))
    has_code = any(r.get("tester_code") for r in day3 + day10)
    n3, n10 = len(day3), len(day10)
    checks.append(Check("2.4", "Zwrot raportów", True, f"dzień 3: {n3}; dzień 10: {n10}",
                        f"dzień 3 ≥ {g['reports_day3_min']}; dzień 10 ≥ {g['reports_day10_min']}",
                        verdict(n3 >= g["reports_day3_min"] and n10 >= g["reports_day10_min"]),
                        "" if has_code else "bez kodu testera liczone są raporty po odrzuceniu duplikatów "
                        "(pseudonim z formularza/nazwy pliku + model), nie pewne osoby"))

    # 2.5 — z 10. dnia.
    src = day10
    dp = [r.get("days_played") for r in src]
    bt = [r.get("battle_s") / 60.0 if r.get("battle_s") is not None else None for r in src]
    se = [r.get("sessions") for r in src]
    m_dp, m_bt, m_se = median(dp), median(bt), median(se)
    four = sum(1 for v in dp if v is not None and v >= g["four_days"])
    if not src:
        checks.append(Check("2.5", "Zaangażowanie (raport z 10. dnia)", True, "brak raportów z 10. dnia",
                            "mediana dni ≥ 6; ≥ 10 osób z ≥ 4 dniami; mediana bitew ≥ 90 min; mediana sesji ≥ 8", NA))
    else:
        ok = (m_dp is not None and m_dp >= g["median_days_played_min"]
              and four >= g["testers_4days_min"]
              and m_bt is not None and m_bt >= g["median_battle_minutes_min"]
              and m_se is not None and m_se >= g["median_sessions_min"])
        checks.append(Check("2.5", "Zaangażowanie (raport z 10. dnia)", True,
                            f"mediana dni {fmt_num(m_dp)}; osób z ≥ 4 dniami {four}; mediana bitew "
                            f"{fmt_num(m_bt)} min; mediana sesji {fmt_num(m_se)}",
                            "mediana dni ≥ 6; ≥ 10 osób z ≥ 4 dniami; mediana bitew ≥ 90 min; mediana sesji ≥ 8",
                            verdict(ok), "ciągłość dni (mapa dni) dopiero w Raporcie v2"))

    # Miękkie.
    td = [r.get("tutorial_done") for r in base if r.get("tutorial_done") is not None]
    yes = sum(1 for v in td if v)
    checks.append(Check("2.6", "Samouczek ukończony", False, pct(yes, len(td)), "≥ 90% n",
                        verdict(None if not td else yes / len(td) >= g["tutorial_done_share_min"])))
    ts = [r.get("tutorial_s") / 60.0 for r in base if isinstance(r.get("tutorial_s"), int)]
    before = sum(1 for r in base if r.get("tutorial_s") == "before")
    m_ts = median(ts)
    checks.append(Check("2.7", "Czas do końca samouczka", False, f"mediana {fmt_num(m_ts)} min (z {len(ts)})",
                        "mediana ≤ 20 min czasu bitew",
                        verdict(None if m_ts is None else m_ts <= g["median_tutorial_minutes_max"]),
                        f"pominięto „before this log”: {before}" if before else ""))
    fw = [r.get("first_win_s") / 60.0 for r in day10 if isinstance(r.get("first_win_s"), int)]
    have = sum(1 for r in day10 if r.get("first_win_s") is not None and r.get("first_win_s") != "?")
    m_fw = median(fw)
    checks.append(Check("2.8", "Pierwsza wygrana (10. dzień)", False,
                        f"mediana {fmt_num(m_fw)} min; wygrana u {pct(have, len(day10))}",
                        "mediana ≤ 20 min; 100% n z 10. dnia",
                        verdict(None if not day10 or m_fw is None else
                                m_fw <= g["median_first_win_minutes_max"] and have >= g["first_win_share_min"] * len(day10))))
    st = [r.get("stage") for r in day10 if r.get("stage") is not None]
    m_st = median(st)
    far = sum(1 for v in st if v >= g["stage_far"])
    checks.append(Check("2.9", "Postęp kampanii (10. dzień)", False,
                        f"mediana etapu {fmt_num(m_st)}; osób z etapem ≥ 13: {far}",
                        "mediana ≥ 8; ≥ 3 osoby z etapem ≥ 13",
                        verdict(None if m_st is None else m_st >= g["median_stage_min"] and far >= g["stage_far_people_min"]),
                        "„stage X” to najwyższy odblokowany etap"))
    ls = [r.get("max_loss_streak") for r in base if r.get("max_loss_streak") is not None]
    wall = sum(1 for v in ls if v >= g["loss_wall"])
    stages_known = any(r.stages for r in base)
    checks.append(Check("2.10", "Ściany trudności", False, f"max seria ≥ 5: {pct(wall, len(ls))}",
                        "≤ 20% n", verdict(None if not ls else wall <= g["loss_wall_share_max"] * len(ls)),
                        funnel_note(base) if stages_known
                        else "przy których etapach — nie do policzenia (lejek etapów, TOP 5 poz. 2)"))
    sb = [(r.get("surrenders"), r.get("battles")) for r in base if r.get("battles") is not None]
    s_sum, b_sum = sum(s for s, _ in sb), sum(b for _, b in sb)
    checks.append(Check("2.11", "Kapitulacje", False, pct(s_sum, b_sum) + " bitew", "≤ 10% bitew (suma)",
                        verdict(None if not b_sum else s_sum <= g["surrender_share_max"] * b_sum)))
    ratings = [r.get("rating") for r in base if r.get("rating") is not None]
    low = [r.rid for r in base if r.get("rating") is not None and r.get("rating") <= g["low_rating_max"]]
    m_r = median(ratings)
    checks.append(Check("2.12", "Ankieta: ocena", False, f"odpowiedzi {len(ratings)}; mediana {fmt_num(m_r)}",
                        "≥ 10 odpowiedzi; mediana ≥ 4",
                        verdict(None if not ratings else len(ratings) >= g["ratings_min"] and m_r >= g["median_rating_min"]),
                        ("pytanie zwrotne (ocena 1–2): " + ", ".join(low)) if low else ""))
    issues = [r.get("issue") for r in base if r.get("issue") and r.get("issue") != "skipped"]
    parts, ok = [], True
    for name in BAD_ISSUES:
        c = sum(1 for v in issues if v == name)
        parts.append(f"{name}: {pct(c, len(issues))}")
        ok = ok and c <= g["issue_share_max"] * len(issues)
    checks.append(Check("2.13", "Ankieta: co przeszkadza", False, "; ".join(parts), "każda ≤ 25% odpowiedzi",
                        verdict(None if not issues else ok)))

    # 2.14 — sesje z pomiarem bitwy (p95 > 0); sesje bez bitwy mają p95 0.0.
    sessions = [(r.get("device") or r.form.get("model") or r.key, p) for r in base for p in r.perf if p[0] > 0]
    devices = sorted({d for d, _ in sessions})
    if not sessions:
        checks.append(Check("2.14", "Wydajność telefonów testerów", False, "brak sesji z bitwą",
                            "p95 ≤ 25 ms w ≥ 80% sesji; > 33 ms na ≤ 2 urządzeniach; thermal ≥ 3 w 0 sesji "
                            "na ≥ 80% urządzeń; dropped > 60 w ≤ 10% sesji", NA))
    else:
        good = sum(1 for _, p in sessions if p[0] <= g["p95_ok_ms"])
        bad_dev = sorted({d for d, p in sessions if p[0] > g["p95_bad_ms"]})
        hot_dev = {d for d, p in sessions if p[3] >= g["thermal_hot"]}
        cool = len(devices) - len(hot_dev)
        drop = sum(1 for _, p in sessions if p[2] > g["dropped_bad"])
        ok = (good >= g["p95_ok_share_min"] * len(sessions) and len(bad_dev) <= g["p95_bad_devices_max"]
              and cool >= g["thermal_cool_devices_share_min"] * len(devices)
              and drop <= g["dropped_bad_share_max"] * len(sessions))
        checks.append(Check("2.14", "Wydajność telefonów testerów", False,
                            f"p95 ≤ 25: {pct(good, len(sessions))} sesji; > 33 ms: {len(bad_dev)} urz.; "
                            f"bez thermal ≥ 3: {pct(cool, len(devices))} urz.; dropped > 60: {pct(drop, len(sessions))} sesji",
                            "p95 ≤ 25 ms w ≥ 80% sesji; > 33 ms na ≤ 2 urządzeniach; thermal ≥ 3 w 0 sesji "
                            "na ≥ 80% urządzeń; dropped > 60 w ≤ 10% sesji", verdict(ok),
                            "liczone sesje z bitwą (p95 > 0)" + ("; > 33 ms: " + ", ".join(bad_dev) if bad_dev else "")))
    with_modes = [r for r in base if r.modes]
    endless = sum(1 for r in with_modes if r.modes.get("ENDLESS", (0,))[0] > 0)
    daily = sum(1 for r in with_modes if r.modes.get("DAILY", (0,))[0] > 0)
    checks.append(Check("2.15", "Tryby", False, f"Endless {pct(endless, len(with_modes))}; wyzwanie dnia {pct(daily, len(with_modes))}",
                        "≥ 50% n zagrało Endless i wyzwanie dnia",
                        verdict(None if not with_modes else endless >= g["modes_share_min"] * len(with_modes)
                                and daily >= g["modes_share_min"] * len(with_modes)),
                        "wyzwanie tygodnia tylko jako ekran („Screens visited: Weekly”)"))
    return checks


def nps_score(values):
    """NPS z ocen 0–10: % polecających (9–10) minus % krytyków (0–6), zaokrąglone; None bez danych."""
    values = [v for v in values if v is not None]
    if not values:
        return None
    return round(100.0 * (sum(1 for v in values if v >= 9) - sum(1 for v in values if v <= 6)) / len(values))


def gate_summary(checks):
    hard = [c for c in checks if c.hard]
    if any(c.verdict == FAIL for c in hard):
        return "NIE PRZECHODZI (twarde kryterium niespełnione)"
    if any(c.verdict == NA for c in hard):
        return "NIEROZSTRZYGNIĘTE (twarde kryteria spoza raportu: uzupełnij dane z Play Console)"
    return "PRZECHODZI (twarde kryteria spełnione)"


# ---------------------------------------------------------------------------
# Wyjście.
# ---------------------------------------------------------------------------
def minutes(s):
    if s is None:
        return "—"
    if s == "before":
        return "przed dziennikiem"
    if s == "?":
        return "?"
    return f"{s / 60.0:.0f}"


def cell(v):
    return str(v).replace("|", "\\|").replace("\n", " ") if v not in (None, "") else "—"


def build_markdown(reports, dropped, problem_count, notes, checks, wave, inputs, base_label):
    out = []
    title = f"Fala {wave} testu zamkniętego — scalone raporty testerów" if wave else "Scalone raporty testerów"
    out.append(f"# {title}\n")
    out.append("Wygenerowano `tools/playtest_merge.py` z: " + ", ".join(f"`{Path(p).name}`" for p in inputs) + ".")
    out.append("Progi: R13 §1, bramka G2 (propozycje do zatwierdzenia). Format raportu: "
               "`PlaytestReportText.kt` (format "
               + ", ".join(sorted({str(r.format) for r in reports}) or ["1"]) + ").\n")
    out.append(f"**Bramka G2: {gate_summary(checks)}.**\n")
    out.append(f"Raporty: {len(reports)} (po odrzuceniu {len(dropped)} duplikatów); "
               f"obcięte {sum(1 for r in reports if r.status == 'obcięty')}, "
               f"uszkodzone {sum(1 for r in reports if r.status == 'uszkodzony')}; "
               f"wklejone „Zgłoś problem” (nie liczone, sprawdzić jako P0): {problem_count}. "
               f"Zbiór n dla kryteriów: {base_label}.\n")

    out.append("## Bramka G2\n")
    for hard in (True, False):
        out.append("### " + ("Twarde" if hard else "Miękkie") + "\n")
        out.append("| # | Kryterium | Wartość | Próg | Wynik | Uwagi |")
        out.append("|---|---|---|---|---|---|")
        for c in checks:
            if c.hard == hard:
                out.append(f"| {c.num} | {c.name} | {cell(c.value)} | {cell(c.threshold)} | **{c.verdict}** | {cell(c.note)} |")
        out.append("")

    out.append("### Czego nie da się policzyć z raportów formatu 1\n")
    out.append("- **Łączenie dnia 3 i 10 tej samej osoby**: format 1 nie ma kodu testera. Narzędzie łączy wtedy po "
               "pseudonimie z formularza albo nazwy pliku + modelu (`Device:`); bez pseudonimu raporty "
               "zostają osobno. Format 2 (wersje testowe od d48) łączy po `Tester code:`; po reinstalacji "
               "gry kod jest nowy, więc tester podaje pseudonim w formularzu.")
    out.append("- **2.1, 2.3** — tylko Play Console (`--active-testers`, `--prelaunch-crashes`).")
    out.append("- **2.2** — format 1 nie liczy awarii/ANR; format 2 ma `Crashes:`/`ANRs:` (lokalny dziennik awarii), "
               "grupy z PC: `--crash-groups`.")
    out.append("- **2.10 „przy których etapach”** — tylko z raportów z sekcją `[Campaign stages]` (gra od d45); "
               "starsze raporty nie mają lejka ani dowódcy na etapie 19 (d37).")
    out.append("- **2.5 ciągłość** — w formacie 1 tylko `Days played`; format 2 ma mapę 28 dni (`day_map` w CSV).\n")

    out.append("## Mediany (wszystkie raporty)\n")
    out.append("| Dzień | n | Dni gry | Sesje | Czas bitew (min) | Czas w aplikacji (min) | Etap | Bitwy | Ocena |")
    out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for label, group in (("3", [r for r in reports if r.day == 3]), ("10", [r for r in reports if r.day == 10]),
                         ("inne/nieznany", [r for r in reports if r.day not in (3, 10)]), ("wszystkie", reports)):
        if not group:
            continue
        med = lambda k, d=1.0: median([r.get(k) / d if r.get(k) is not None else None for r in group])
        out.append(f"| {label} | {len(group)} | {fmt_num(med('days_played'))} | {fmt_num(med('sessions'))} | "
                   f"{fmt_num(med('battle_s', 60.0))} | {fmt_num(med('app_s', 60.0))} | {fmt_num(med('stage'))} | "
                   f"{fmt_num(med('battles'))} | {fmt_num(med('rating'))} |")
    out.append("")

    out.append("## Raporty\n")
    out.append("| ID | Źródło | Tester | Dzień | Stan | Wersja | Telefon | Język | Dni | Sesje | Bitwy min | "
               "Etap | Gwiazdki | Samouczek (min) | 1. wygrana (min) | Bitwy/wygr./kap. | Max seria porażek | "
               "Ocena | Kłopot | Najgorsze p95 | Thermal max |")
    out.append("|" + "---|" * 21)
    for r in reports:
        day = "—" if r.day is None else (f"{r.day}*" if r.day_inferred else str(r.day))
        p95 = max((p[0] for p in r.perf), default=None)
        th = max((p[3] for p in r.perf), default=None)
        lang = r.get("language") or "—"
        if r.subject_lang and r.subject_lang != lang:
            lang += f" (temat: {r.subject_lang})"
        tester = r.get("tester_code") or r.form.get("tester") or "—"
        bws = "/".join("—" if r.get(k) is None else str(r.get(k)) for k in ("battles", "wins", "surrenders"))
        tut = minutes(r.get("tutorial_s")) if r.get("tutorial_done") else ("nie" if r.get("tutorial_done") is False else "—")
        survey = r.get("rating") if r.get("rating") is not None else r.get("survey")
        out.append("| " + " | ".join(cell(v) for v in (
            r.rid, r.source, tester, day, r.status, r.get("version"), r.get("device") or r.form.get("model"), lang,
            r.get("days_played"), r.get("sessions"), minutes(r.get("battle_s")),
            r.get("stage"), None if r.get("stars") is None else f"{r.get('stars')}/{r.get('stars_max')}",
            tut, minutes(r.get("first_win_s")), bws, r.get("max_loss_streak"), survey, r.get("issue"),
            None if p95 is None else f"{p95:g}", th)) + " |")
    out.append("\n`*` dzień wyliczony z dat w raporcie (brak pola formularza): rozpiętość < 7 dni → 3, inaczej 10.\n")

    out.append("## Ankieta\n")
    ratings = [r.get("rating") for r in reports if r.get("rating") is not None]
    out.append("Rozkład ocen: " + ", ".join(f"{k}: {ratings.count(k)}" for k in range(1, 6))
               + f"; pominięte: {sum(1 for r in reports if r.get('survey') == 'skipped')}; "
               f"nie pytano: {sum(1 for r in reports if r.get('survey') == 'not asked')}.\n")
    out.append("Biggest issue: " + ", ".join(
        f"„{name}”: {sum(1 for r in reports if r.get('issue') == name)}" for name in SURVEY_ISSUES + ("skipped",)) + ".\n")

    finals = [r for r in reports if r.get("final")]
    if finals:
        out.append("## Ankieta końcowa (10. dzień)\n")
        nps = [r.get("final_nps") for r in finals if r.get("final_nps") is not None]
        out.append("Polecenie 0–10: " + ", ".join(f"{k}: {nps.count(k)}" for k in range(11))
                   + f"; pominięte: {sum(1 for r in finals if r.get('final') == 'skipped')}; "
                   f"nie pytano: {sum(1 for r in finals if r.get('final') == 'not asked')}.\n")
        score = nps_score(nps)
        if score is not None:
            out.append(f"Mediana {fmt_num(median(nps))}; NPS (9–10 minus 0–6, % odpowiedzi): {score:+d} "
                       f"przy {len(nps)} odpowiedziach — przy kilkunastu osobach to tylko orientacja.\n")
        out.append("Liked most: " + ", ".join(
            f"„{name}”: {sum(1 for r in finals if r.get('final_liked') == name)}" for name in FINAL_LIKED + ("skipped",)) + ".\n")
        out.append("Bothered most: " + ", ".join(
            f"„{name}”: {sum(1 for r in finals if r.get('final_issue') == name)}" for name in FINAL_ISSUES + ("skipped",)) + ".\n")

    out.append("## Wydajność według telefonu\n")
    out.append("| Telefon | Raporty | Sesje z bitwą | Najgorsze p95 (ms) | Mediana p95 | Max redukcja | Max dropped | Max thermal |")
    out.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    by_dev = {}
    for r in reports:
        by_dev.setdefault(r.get("device") or r.form.get("model") or "?", []).append(r)
    for dev, group in sorted(by_dev.items()):
        ses = [p for r in group for p in r.perf if p[0] > 0]
        out.append(f"| {cell(dev)} | {len(group)} | {len(ses)} | "
                   + (" | ".join([f"{max(p[0] for p in ses):g}", f"{statistics.median(p[0] for p in ses):g}",
                                  str(max(p[1] for p in ses)), str(max(p[2] for p in ses)), str(max(p[3] for p in ses))])
                      if ses else "— | — | — | — | —") + " |")
    out.append("")

    texts = [(r.rid, r.form.get(k), label) for r in reports
             for k, label in (("liked", "podobało się"), ("disliked", "przeszkadzało")) if r.form.get(k)]
    if texts:
        out.append("## Wolne teksty z formularza (do pogrupowania w tematy ręcznie)\n")
        for label in ("przeszkadzało", "podobało się"):
            items = [(rid, t) for rid, t, lab in texts if lab == label]
            if items:
                out.append(f"**{label.capitalize()}:**\n")
                out += [f"- {rid}: {cell(t)}" for rid, t in items]
                out.append("")

    issues = [r for r in reports if r.problems]
    if issues or notes or dropped:
        out.append("## Uwagi parsera\n")
        for r in issues:
            out.append(f"- {r.rid} ({r.source}, {r.status}): " + "; ".join(r.problems[:5]))
        for r in dropped:
            out.append(f"- duplikat pominięty: {r.source} (ten sam tester i dzień, mniejszy czas bitew)")
        out += [f"- {n}" for n in notes]
        out.append("")
    return "\n".join(out)


CSV_COLUMNS = ("id", "source", "tester", "key", "day", "day_inferred", "status", "format", "version", "device",
               "android", "language", "days_played", "sessions", "battle_min", "app_min", "battles", "wins",
               "surrenders", "stage", "stars", "difficulty", "tutorial_done", "tutorial_min", "first_win_min",
               "max_loss_streak", "rating", "issue", "worst_p95_ms", "max_thermal", "endless_battles",
               "daily_battles", "tester_code", "crashes", "anrs", "day_map", "research_levels",
               "final_nps", "final_liked", "final_issue")


def write_csv(path: Path, reports):
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(CSV_COLUMNS)
        for r in reports:
            mins = lambda v: "" if not isinstance(v, int) or isinstance(v, bool) else f"{v / 60.0:.1f}"
            w.writerow([r.rid, r.source, r.form.get("tester", ""), r.key, r.day or "", int(r.day_inferred), r.status,
                        r.format, r.get("version", ""), r.get("device", ""), r.get("android", ""), r.get("language", ""),
                        r.get("days_played", ""), r.get("sessions", ""), mins(r.get("battle_s")), mins(r.get("app_s")),
                        r.get("battles", ""), r.get("wins", ""), r.get("surrenders", ""), r.get("stage", ""),
                        r.get("stars", ""), r.get("difficulty", ""), r.get("tutorial_done", ""),
                        mins(r.get("tutorial_s")), mins(r.get("first_win_s")), r.get("max_loss_streak", ""),
                        r.get("rating", ""), r.get("issue", ""), max((p[0] for p in r.perf), default=""),
                        max((p[3] for p in r.perf), default=""), r.modes.get("ENDLESS", ("",))[0],
                        r.modes.get("DAILY", ("",))[0], r.get("tester_code", ""), r.get("crashes", ""), r.get("anrs", ""),
                        r.get("day_map", ""), r.get("research_levels", ""),
                        r.get("final_nps", ""), r.get("final_liked", ""), r.get("final_issue", "")])


def merge(inputs, overrides=None, external=None, day_split=7):
    reports, problems, notes = read_inputs(inputs, overrides or {})
    for r in reports:
        assign_day(r, day_split)
    assign_keys(reports)
    reports, dropped = dedupe(reports)
    for i, r in enumerate(reports, start=1):
        r.rid = f"R{i:02d}"
    day3 = [r for r in reports if r.day == 3]
    day10 = [r for r in reports if r.day == 10]
    if day10:
        base, label = day10, f"raporty z 10. dnia ({len(day10)})"
    else:
        base, label = reports, f"wszystkie raporty ({len(reports)}) — brak raportów z 10. dnia, ocena wstępna"
    checks = evaluate(day3, day10, base, external or {})
    return reports, dropped, problems, notes, checks, label


def main(argv=None):
    ap = argparse.ArgumentParser(description="Scala „Raporty dla testera” Century War i ocenia bramkę G2 (R13).")
    ap.add_argument("inputs", nargs="+", help="CSV z Formularza Google, pliki .txt/.eml albo katalogi")
    ap.add_argument("-o", "--output", required=True, help="ścieżka wyjściowego Markdown (np. docs/reports/test-fala1.md)")
    ap.add_argument("--csv-out", help="opcjonalny CSV „tester × dzień”")
    ap.add_argument("--fala", type=int, help="numer fali (do tytułu)")
    ap.add_argument("--column", action="append", default=[], metavar="ROLA=NAGŁÓWEK",
                    help="ręczne wskazanie kolumny CSV: report, tester, which, model, minutes, liked, disliked, timestamp")
    ap.add_argument("--active-testers", type=int, help="2.1: aktywni testerzy z Play Console")
    ap.add_argument("--crash-groups", type=int, help="2.2: otwarte powtarzalne grupy awarii/ANR z Play Console")
    ap.add_argument("--prelaunch-crashes", type=int, help="2.3: awarie i ANR w raporcie przedpremierowym")
    ap.add_argument("--day-split", type=int, default=7,
                    help="bez pola „który raport”: rozpiętość dni < N → dzień 3, inaczej 10 (domyślnie 7)")
    args = ap.parse_args(argv)
    overrides = {}
    for item in args.column:
        role, _, name = item.partition("=")
        if role not in COLUMN_HINTS or not name:
            ap.error(f"zła wartość --column: {item!r}")
        overrides[role] = name
    external = {"active_testers": args.active_testers, "crash_groups": args.crash_groups,
                "prelaunch_crashes": args.prelaunch_crashes}
    reports, dropped, problems, notes, checks, label = merge(args.inputs, overrides, external, args.day_split)
    text = build_markdown(reports, dropped, problems, notes, checks, args.fala, args.inputs, label)
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text + "\n", encoding="utf-8")
    if args.csv_out:
        write_csv(Path(args.csv_out), reports)
    print(f"Raporty: {len(reports)}, bramka G2: {gate_summary(checks)} → {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
