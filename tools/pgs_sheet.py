#!/usr/bin/env python3
"""Arkusz Play Games i pliki importu osiągnięć z kodu gry.

Źródła: kolejność, progi, nagrody i ukrycie z meta/Achievements.kt; nazwy i opisy
z engine/L.kt (ach_<id>, ach_<id>_d, board_*), w tym cztery osiągnięcia R6-01 (part185).

Użycie (z katalogu głównego repo):
    python3 tools/pgs_sheet.py            # zapisuje pliki
    python3 tools/pgs_sheet.py --check    # tylko sprawdza limity i sumę XP

Wyniki:
    docs/store/play_games_sheet.csv
    docs/store/pgs_import/AchievementsMetadata.csv        (język domyślny: polski)
    docs/store/pgs_import/AchievementsLocalizations.csv   (9 pozostałych języków)
"""
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ACH = ROOT / "app/src/main/java/com/centurywar/meta/Achievements.kt"
LKT = ROOT / "app/src/main/java/com/centurywar/engine/L.kt"
SHEET = ROOT / "docs/store/play_games_sheet.csv"
IMPORT_DIR = ROOT / "docs/store/pgs_import"

LANGS = ["pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr"]
# Kody języków Play Console (support.google.com/googleplay/android-developer/table/4419860).
LOCALES = {"en": "en-US", "de": "de-DE", "es": "es-ES", "fr": "fr-FR", "it": "it-IT",
           "pt": "pt-PT", "uk": "uk", "nl": "nl-NL", "tr": "tr-TR"}

# Limity Play Games (developer.android.com/games/pgs/achievements, .../integrate-achievements).
NAME_MAX, DESC_MAX = 100, 500
POINTS_TOTAL_MAX, POINTS_ONE_MAX = 2000, 200
# Proponowany budżet: zostawiamy zapas na przyszłe osiągnięcia.
# 4.11 (po D6): 1500 → 1600, bo „Wygrany zakład” (20 pkt) daje sumę 1515; limit Google to 2000.
POINTS_BUDGET = 1600

LEADERBOARDS = [  # klucz w properties, klucz nazwy w L.kt, uwaga
    ("leaderboard_endless", "board_endless", "punkty Oblężenia (Endless)"),
    ("leaderboard_campaign", "board_campaign", "suma gwiazdek 0–108"),
    ("leaderboard_skirmish", "board_skirmish", "punkty potyczki"),
    ("leaderboard_daily", "board_daily", "punkty wyzwania dnia; gra pokazuje okno dzienne"),
    # Dzień 62: Rozkazy sztabu — suma najlepszych punktów 9 rozkazów (gwiazdki × 1000 + zdrowie bazy 0–500).
    ("leaderboard_orders", "board_orders", "punkty sztabu 0–31500: suma za rozkazy (gwiazdki × 1000 + zdrowie bazy 0–500)"),
]

# Wartości zastępujące wyrażenia progów z Achievements.kt (stan 3.11.2026).
EXPR = {
    "Campaign.stages.size * 3L": 108,
    "Campaign.stages.size.toLong()": 36,
    "com.centurywar.game.Doctrine.values().size.toLong()": 13,
}


def l_table():
    src = LKT.read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r'"([A-Za-z0-9_]+)" to t\((.*?)\),\n', src):
        vals = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))
        if len(vals) == 10:
            out[m.group(1)] = [v.replace('\\"', '"') for v in vals]
    return out


def achievements():
    src = ACH.read_text(encoding="utf-8")
    body = src[src.index("val all: List<AchievementDef> = listOf("):]
    body = body[:body.index("\n    )\n")]
    rows = []
    for m in re.finditer(r'def\(\s*"([a-z0-9_]+)",\s*AchievementMetric\.([A-Z0-9_]+),\s*'
                         r'(.+?),\s*(\d+),\s*AchievementCategory\.([A-Z]+)(,\s*true)?\)', body, re.S):
        thr = m.group(3).strip()
        thr = EXPR.get(thr, None) if not re.fullmatch(r"\d+L?", thr) else int(thr.rstrip("L"))
        if thr is None:
            raise SystemExit("Nieznane wyrażenie progu dla " + m.group(1))
        rows.append(dict(id=m.group(1), threshold=thr, medals=int(m.group(4)),
                         category=m.group(5), hidden=bool(m.group(6))))
    return rows


def points(medals):
    """Punkty Play Games z nagrody w medalach (wielokrotność 5, 5–40)."""
    for limit, pts in ((30, 5), (60, 10), (150, 15), (200, 20), (300, 25), (500, 30)):
        if medals <= limit:
            return pts
    return 40


def import_text(s):
    """Plik importu nie dopuszcza przecinków w nazwie i opisie."""
    return s.replace(",", " –")


def main():
    check_only = "--check" in sys.argv
    L = l_table()
    rows = achievements()
    # Kolejność wyświetlania: od najłatwiejszych (nagroda w medalach), remis wg kolejności w kodzie.
    order = sorted(range(len(rows)), key=lambda i: (rows[i]["medals"], i))
    for pos, i in enumerate(order, 1):
        rows[i]["list_order"] = pos

    problems = []
    total = 0
    for r in rows:
        r["names"] = L["ach_" + r["id"]]
        r["descs"] = L["ach_" + r["id"] + "_d"]
        r["source"] = "L.kt"
        r["points"] = points(r["medals"])
        total += r["points"]
        for lang, n, d in zip(LANGS, r["names"], r["descs"]):
            if len(n) > NAME_MAX:
                problems.append(f"{r['id']} {lang}: nazwa {len(n)} > {NAME_MAX}")
            if len(d) > DESC_MAX:
                problems.append(f"{r['id']} {lang}: opis {len(d)} > {DESC_MAX}")
    for lang_i, lang in enumerate(LANGS):
        names = [r["names"][lang_i] for r in rows if r["names"][lang_i]]
        if len(names) != len(set(names)):
            problems.append(f"{lang}: nazwy osiągnięć nie są unikalne")
    if total > POINTS_BUDGET or total > POINTS_TOTAL_MAX:
        problems.append(f"suma punktów {total} > budżet {POINTS_BUDGET}")
    if any(r["points"] % 5 or not 5 <= r["points"] <= POINTS_ONE_MAX for r in rows):
        problems.append("punkty poza zakresem 5–200 albo nie są wielokrotnością 5")

    print(f"osiągnięcia: {len(rows)}, rankingi: {len(LEADERBOARDS)}, "
          f"ID razem z projektem: {len(rows) + len(LEADERBOARDS) + 1}")
    print(f"suma punktów: {total} (XP graczy: {total * 100}); limit {POINTS_TOTAL_MAX}")
    for p in problems:
        print("PROBLEM:", p)
    if check_only:
        return 1 if problems else 0

    header = ["kolejnosc", "typ", "klucz_w_grze", "klucz_w_properties", "nazwa_pl", "opis_pl",
              "nazwa_en", "opis_en", "punkty_pgs", "xp_gracza", "ukryte", "przyrostowe",
              "kroki", "prog_w_grze", "medale_w_grze", "zrodlo_tekstu", "id_z_konsoli"]
    with SHEET.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        for pos, (key, lkey, note) in enumerate(LEADERBOARDS, 1):
            n = L[lkey]
            w.writerow([pos, "ranking", key.replace("leaderboard_", ""), key, n[0], note,
                        n[1], "", "", "", "", "", "", "", "", "L.kt", ""])
        for r in sorted(rows, key=lambda r: r["list_order"]):
            w.writerow([r["list_order"], "osiągnięcie", "cw_" + r["id"], "achievement_" + r["id"],
                        r["names"][0], r["descs"][0], r["names"][1], r["descs"][1],
                        r["points"], r["points"] * 100, "tak" if r["hidden"] else "nie",
                        "nie", "", r["threshold"], r["medals"], r["source"], ""])

    IMPORT_DIR.mkdir(parents=True, exist_ok=True)
    # Pliki importu bez nagłówka; siedem pól: Name,Description,Incremental,Steps,State,Points,Order.
    with (IMPORT_DIR / "AchievementsMetadata.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_NONE, escapechar=None)
        for r in sorted(rows, key=lambda r: r["list_order"]):
            w.writerow([import_text(r["names"][0]), import_text(r["descs"][0]), "False", "",
                        "Hidden" if r["hidden"] else "Revealed", r["points"], r["list_order"]])
    with (IMPORT_DIR / "AchievementsLocalizations.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_NONE, escapechar=None)
        for r in sorted(rows, key=lambda r: r["list_order"]):
            for lang_i, lang in enumerate(LANGS[1:], 1):
                if not r["names"][lang_i]:
                    continue
                w.writerow([import_text(r["names"][0]), import_text(r["names"][lang_i]),
                            import_text(r["descs"][lang_i]), LOCALES[lang]])
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
