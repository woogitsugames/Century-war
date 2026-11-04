"""Testy narzędzia scalania raportów testerów (tools/playtest_merge.py), bez Android SDK.

Przykładowe raporty buduje `build_report` — kopia logiki `PlaytestReportText.build`
(app/src/main/java/com/centurywar/crash/PlaytestReportText.kt). Test `test_constants_match_game_code`
pilnuje, żeby etykiety narzędzia i gry się nie rozjechały.
"""

import csv
import datetime
import importlib.util
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/playtest_merge.py"
spec = importlib.util.spec_from_file_location("playtest_merge", SOURCE)
pm = importlib.util.module_from_spec(spec)
sys.modules["playtest_merge"] = pm  # dataclasses potrzebują modułu w sys.modules
spec.loader.exec_module(pm)

KOTLIN = ROOT / "app/src/main/java/com/centurywar/crash/PlaytestReportText.kt"
LOG_KT = ROOT / "app/src/main/java/com/centurywar/meta/PlaytestLog.kt"
L_KT = ROOT / "app/src/main/java/com/centurywar/engine/L.kt"
MODES_KT = ROOT / "sim/src/main/kotlin/com/centurywar/game/Modes.kt"
FIXTURE = ROOT / "tests/fixtures/playtest_report_v1.txt"

QUALITY = ("auto", "low", "medium", "high")
PALETTES = ("standard", "deutan/protan", "tritan", "high contrast")
RESULTS = ("win", "loss", "surrender")
EPOCH = datetime.date(1970, 1, 1)


def _day(d):
    return "-" if d < 0 else (EPOCH + datetime.timedelta(days=d)).isoformat()


def _duration(s):
    s = max(s, 0)
    h, m = s // 3600, (s % 3600) // 60
    return f"{h}h {m}m" if h > 0 else f"{m}m {s % 60}s"


def _milestone(s):
    if s == -2:
        return "before this log"
    if s < 0:
        return "not yet"
    return "after " + _duration(s) + " of battle time"


def _yes(b):
    return "yes" if b else "no"


def _float(v):
    """Float.toString z Kotlina dla wartości z raportu (dziesiąte części)."""
    v = int(v * 10) / 10
    return repr(float(v)) if abs(v) < 1e7 else f"{v:.1E}".replace("E+0", "E").replace("E+", "E")


def build_report(version="0.1.0 (1)", model="Acme Phone", android="15 (API 35)", *, language="pl",
                 survey=1, rating=4, issue=3, survey_day=20_430, stage=8, stage_count=36, stars=20,
                 difficulty="NORMAL", tutorial_done=True, tutorial_s=12 * 60, first_win_s=15 * 60,
                 best_wave=12, win_streak=1, best_win_streak=3, loss_streak=0, max_loss_streak=2, login_streak=3,
                 first_day=20_429, last_day=20_438, days_played=7, sessions=10, foreground_s=3 * 3600,
                 play_s=2 * 3600, battles=20, wins=14, surrenders=1,
                 mode_battles=(12, 3, 3, 2), mode_wins=(9, 2, 2, 1), mode_s=(3600, 1200, 1200, 1200),
                 screens=0b11, recent=(), perf=((18.4, 2, 5, 1),), quality_setting=0, quality_level=2,
                 reduction=0, max_steps=4, gpu=0, fps_cap=0, game_mode="standard", palette=0, text_scale=100,
                 left=False, talkback=False, shake=False, flash=False, slow=False, max_chars=6000):
    o = ["Century War - tester report\n", f"Version: {version}\n", f"Device: {model}\n",
         f"Android: {android}\n", f"Language: {language}\n\n", "[Tester survey]\n"]
    if survey == 1:
        o.append(f"Rating: {rating}/5 ({_day(survey_day)})\n")
        o.append("Biggest issue: " + (pm.SURVEY_ISSUES[issue] if 0 <= issue < 4 else "skipped") + "\n")
    elif survey == 2:
        o.append(f"Skipped ({_day(survey_day)})\n")
    else:
        o.append("Not asked yet\n")
    o.append("\n[Progress]\n")
    o.append(f"Campaign: stage {stage} of {stage_count}, stars {stars} of {stage_count * 3}, difficulty {difficulty}\n")
    o.append(f"Tutorial done: {_yes(tutorial_done)} ({_milestone(tutorial_s)})\n")
    o.append(f"First win: {_milestone(first_win_s)}\n")
    o.append(f"Best Endless wave: {best_wave}\n")
    o.append(f"Win streak: {win_streak} (best {best_win_streak})\n")
    o.append(f"Campaign loss streak: {loss_streak} (max {max_loss_streak})\n")
    o.append(f"Login streak: {login_streak}\n\n")
    o.append("[Play time]\n")
    o.append(f"First day (UTC): {_day(first_day)}, last day: {_day(last_day)}\n")
    o.append(f"Days played: {days_played}, sessions: {sessions}\n")
    o.append(f"Time in app: {_duration(foreground_s)}\n")
    o.append(f"Battle time: {_duration(play_s)}\n")
    o.append(f"Battles: {battles}, wins: {wins}, surrenders: {surrenders}\n\n")
    o.append("[Modes]\n")
    for i, m in enumerate(pm.MODES):
        o.append(f"{m}: battles {mode_battles[i]}, wins {mode_wins[i]}, time {_duration(mode_s[i])}\n")
    names = [pm.SCREEN_NAMES[i] for i in range(len(pm.SCREEN_NAMES)) if screens & (1 << i)]
    o.append("Screens visited: " + (", ".join(names) if names else "-") + "\n")
    o.append("Recent battles (newest first):\n")
    if not recent:
        o.append("  -\n")
    for mode, st, res, sec, era, ai in recent:
        o.append("  " + pm.MODES[mode] + (f" stage {st + 1}" if st >= 0 else "")
                 + f", {RESULTS[res]}, {_duration(sec)}, era {era + 1}, AI {ai}%\n")
    o.append("\n[Performance]\n")
    o.append(f"Quality: {QUALITY[quality_setting]} (level {quality_level}, reduction now {reduction}/{max_steps}, GPU {gpu})\n")
    o.append("Last sessions (newest first, 30 s battle windows):\n")
    if not perf:
        o.append("  -\n")
    for i, (p95, red, drop, th) in enumerate(perf):
        o.append(f"  #{i + 1}: worst p95 {_float(p95)} ms, max reduction {red}, dropped steps {drop}, thermal {th}\n")
    o.append("\n[Settings]\n")
    o.append("FPS cap: " + ("display" if fps_cap == 0 else str(fps_cap)) + "\n")
    o.append(f"Game mode: {game_mode}\n")
    o.append(f"Palette: {PALETTES[palette]}\n")
    o.append(f"Text size: {text_scale}%\n")
    o.append(f"Left-handed HUD: {_yes(left)}\n")
    o.append(f"TalkBack: {_yes(talkback)}\n")
    o.append(f"Reduce shake/flash: {_yes(shake)}/{_yes(flash)}\n")
    o.append(f"Battle speed x1/2: {_yes(slow)}\n")
    return "".join(o)[:max_chars]


def tester(i, day):
    """Raport testera i w dniu 3 albo 10 (wartości spełniające G2 dla dnia 10)."""
    late = day == 10
    return build_report(
        model=f"Phone {i % 6}", first_day=20_429, last_day=20_429 + (9 if late else 2),
        days_played=(7 if late else 3), sessions=(10 if late else 4), play_s=(2 * 3600 if late else 40 * 60),
        stage=(9 + (i % 7) if late else 4), rating=(5 if i % 2 else 4), issue=3 if i % 4 else 0,
        perf=((18.0 + i % 3, 1, 0, 1), (0.0, 0, 0, 0)))


class PlaytestMergeTest(unittest.TestCase):
    def test_constants_match_game_code(self):
        kt = KOTLIN.read_text(encoding="utf-8")
        log = LOG_KT.read_text(encoding="utf-8")
        lkt = L_KT.read_text(encoding="utf-8")
        for s in pm.SECTIONS:
            self.assertIn(f'"{s}"', kt)
        self.assertIn('"' + pm.HEADER + '\\n"', kt)
        for issue in pm.SURVEY_ISSUES:
            self.assertIn(f'"{issue}"', log)
        for name in pm.SCREEN_NAMES:
            self.assertIn(f'"{name}"', log)
        modes = re.search(r"enum class Mode \{([^}]*)\}", MODES_KT.read_text(encoding="utf-8")).group(1)
        self.assertEqual(tuple(m.strip() for m in modes.split(",")), pm.MODES)
        codes = re.search(r"val CODES = arrayOf\(([^)]*)\)", lkt).group(1)
        self.assertEqual(tuple(re.findall(r'"(\w+)"', codes)), pm.LANG_CODES)
        subj = re.search(r'"pt182_subject" to t\(([^)]*)\)', lkt).group(1)
        self.assertEqual(tuple(re.findall(r'"([^"]+)"', subj)), tuple(pm.SUBJECTS[c] for c in pm.LANG_CODES))
        prob = re.search(r'"report_subject" to t\(([^)]*)\)', lkt).group(1)
        self.assertEqual(tuple(re.findall(r'"([^"]+)"', prob)), pm.PROBLEM_SUBJECTS)
        self.assertIn(pm.PROBLEM_HEADER, (ROOT / "app/src/main/java/com/centurywar/crash/CrashReportText.kt")
                      .read_text(encoding="utf-8"))
        # Każda etykieta, którą parsuje narzędzie, jest w kodzie raportu.
        for label in ("Version: ", "Device: ", "Android: ", "Language: ", "Rating: ", "/5 (", "Biggest issue: ",
                      "Skipped (", "Not asked yet", "Campaign: stage ", " of ", ", stars ", ", difficulty ",
                      "Tutorial done: ", "before this log", "not yet", "after ", " of battle time", "First win: ",
                      "Best Endless wave: ", "Win streak: ", " (best ", "Campaign loss streak: ", " (max ",
                      "Login streak: ", "First day (UTC): ", ", last day: ", "Days played: ", ", sessions: ",
                      "Time in app: ", "Battle time: ", "Battles: ", ", wins: ", ", surrenders: ", ": battles ",
                      ", wins ", ", time ", "Screens visited: ", "Recent battles (newest first):", " stage ",
                      ", era ", ", AI ", "Quality: ", " (level ", ", reduction now ", ", GPU ",
                      "Last sessions (newest first, 30 s battle windows):", ": worst p95 ", " ms, max reduction ",
                      ", dropped steps ", ", thermal ", "FPS cap: ", "Game mode: ", "Palette: ", "Text size: ",
                      "Left-handed HUD: ", "TalkBack: ", "Reduce shake/flash: ", pm.LAST_LABEL):
            self.assertIn(label.strip() if label.strip() else label, kt, label)
        self.assertIn("MAX_CHARS = 6_000", kt)

    def test_fixture_matches_builder_and_parses(self):
        text = FIXTURE.read_text(encoding="utf-8")
        recent = ((0, 7, 0, 303, 1, 100), (2, -1, 1, 845, 3, 110), (0, 7, 2, 120, 1, 100))
        self.assertEqual(text, build_report(screens=(1 << 0) | (1 << 1) | (1 << 7), recent=recent,
                                            perf=((18.4, 2, 5, 1), (25.0, 0, 0, 0))))
        found, problems = pm.split_reports(text)
        self.assertEqual((len(found), problems), (1, 0))
        r = pm.parse_report(found[0][1], "fixture")
        self.assertEqual(r.status, "ok", r.problems)
        self.assertEqual(r.problems, [])
        self.assertEqual(r.get("version"), "0.1.0 (1)")
        self.assertEqual(r.get("device"), "Acme Phone")
        self.assertEqual(r.get("language"), "pl")
        self.assertEqual((r.get("rating"), r.get("issue")), (4, "nothing, it's good"))
        self.assertEqual((r.get("stage"), r.get("stage_count"), r.get("stars"), r.get("difficulty")), (8, 36, 20, "NORMAL"))
        self.assertEqual((r.get("tutorial_done"), r.get("tutorial_s"), r.get("first_win_s")), (True, 720, 900))
        self.assertEqual((r.get("days_played"), r.get("sessions")), (7, 10))
        self.assertEqual((r.get("battle_s"), r.get("app_s")), (7200, 10800))
        self.assertEqual((r.get("battles"), r.get("wins"), r.get("surrenders")), (20, 14, 1))
        self.assertEqual(r.get("max_loss_streak"), 2)
        self.assertEqual(r.modes["DAILY"], (2, 1, 1200))
        self.assertEqual(r.get("screens"), ["Codex", "Research", "Daily"])
        self.assertEqual(len(r.recent), 3)
        self.assertEqual(r.recent[0], ("CAMPAIGN", 8, "win", 303, 2, 100))
        self.assertEqual(r.recent[1][1], None)
        self.assertEqual(r.perf, [(18.4, 2, 5, 1), (25.0, 0, 0, 0)])
        self.assertEqual(r.get("game_mode"), "standard")
        self.assertIs(r.get("slow"), False)

    def test_survey_variants_and_milestones(self):
        r = pm.parse_report(build_report(survey=2, tutorial_s=-2, first_win_s=-1, tutorial_done=True), "x")
        self.assertEqual(r.get("survey"), "skipped")
        self.assertIsNone(r.get("rating"))
        self.assertEqual((r.get("tutorial_s"), r.get("first_win_s")), ("before", None))
        r = pm.parse_report(build_report(survey=0, issue=-1, recent=(), perf=(), screens=0), "y")
        self.assertEqual(r.get("survey"), "not asked")
        self.assertEqual((r.get("screens"), r.perf, r.status), ([], [], "ok"))
        r = pm.parse_report(build_report(rating=2, issue=-1, play_s=26 * 3600 + 5 * 60, perf=((99999.0, 9, 10**15, 9),)), "z")
        self.assertEqual((r.get("rating"), r.get("issue"), r.get("battle_s")), (2, "skipped", 26 * 3600 + 300))
        self.assertEqual(r.perf[0][2], 10**15)
        r = pm.parse_report(build_report(perf=((1e8, 1, 0, 0),)), "big")
        self.assertEqual(r.perf[0][0], 1e8)

    def test_truncated_report(self):
        recent = tuple((2, -1, 1, 400, 3, 100) for _ in range(10))
        full = build_report(recent=recent, perf=tuple((18.0, 1, 0, 0) for _ in range(5)))
        cut = full[: full.index("[Performance]") + 40]  # obcięte w środku sesji wydajności
        r = pm.parse_report(cut, "cut")
        self.assertEqual(r.status, "obcięty")
        self.assertIn("[Settings]", r.problems[0])
        self.assertEqual(r.get("rating"), 4, "ankieta na początku przetrwa obcięcie")
        self.assertEqual(r.get("days_played"), 7)
        self.assertEqual(r.perf, [], "ucięta ostatnia linia nie jest liczona")
        # Ucięta liczba w ostatniej linii nie trafia do danych.
        cut = full[: full.index("Days played: 7, sessions: 10") + len("Days played: 7, sessions: 1")]
        r = pm.parse_report(cut, "cut2")
        self.assertIsNone(r.get("sessions"))
        self.assertEqual(r.get("stage"), 8)
        # Obcięcie przez grę (MAX_CHARS) daje ten sam stan.
        long = build_report(model="X" * 5990)
        self.assertEqual(len(long), 6000)
        self.assertEqual(pm.parse_report(long, "max").status, "obcięty")

    def test_damaged_and_headless(self):
        text = build_report().replace("Days played: 7, sessions: 10\n", "Days plyed: 7 sessions 10\n")
        r = pm.parse_report(text, "bad")
        self.assertEqual(r.status, "uszkodzony")
        self.assertTrue(any("days_played" in p for p in r.problems))
        found, _ = pm.split_reports("cześć, oto raport:\n" + build_report().split("\n", 2)[2])
        self.assertEqual(len(found), 1)
        r = pm.parse_report(found[0][1], "nohead")
        self.assertEqual(r.status, "uszkodzony")
        self.assertEqual(r.get("days_played"), 7)
        self.assertEqual(pm.split_reports("zupełnie inny tekst"), ([], 0))

    def test_localized_subject_all_languages(self):
        for lang, subject in pm.SUBJECTS.items():
            text = f"Subject: {subject}\n\n" + build_report(language=lang)
            found, _ = pm.split_reports(text)
            self.assertEqual(len(found), 1, lang)
            r = pm.parse_report(found[0][1], lang, found[0][0])
            self.assertEqual(r.get("language"), lang)
            self.assertEqual(r.status, "ok", (lang, r.problems))
            if lang != "en":
                self.assertEqual(found[0][0], lang)
        # Sam temat bez nagłówka treści (np. wycięty) też otwiera raport.
        body = build_report(language="uk").split("\n", 1)[1]
        found, _ = pm.split_reports(pm.SUBJECTS["uk"] + "\n" + body)
        self.assertEqual(found[0][0], "uk")
        self.assertEqual(pm.parse_report(found[0][1], "uk", "uk").get("days_played"), 7)

    def test_eml_problem_report_and_reflow(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d / "a.eml").write_text(
                "From: t@example.com\nSubject: =?utf-8?q?Century_War_-_Testerbericht?=\n"
                "Content-Type: text/plain; charset=utf-8\n\n" + build_report(language="de"), encoding="utf-8")
            (d / "b.txt").write_text("Century War - raport techniczny\nWersja: 0.1.0\n", encoding="utf-8")
            (d / "c.txt").write_text(build_report(model="Flat Phone").replace("\n", " "), encoding="utf-8")
            reports, problems, notes = pm.read_inputs([d], {})
            self.assertEqual(problems, 1)
            self.assertEqual(len(reports), 2)
            de = next(r for r in reports if r.source == "a.eml")
            self.assertEqual((de.subject_lang, de.get("language"), de.status), ("de", "de", "ok"))
            flat = next(r for r in reports if r.source == "c.txt")
            self.assertEqual(flat.status, "ok", flat.problems)
            self.assertEqual((flat.get("device"), flat.get("sessions"), len(flat.perf)), ("Flat Phone", 10, 1))
            self.assertEqual(flat.modes["ENDLESS"][0], 3)

    def _form_csv(self, path, rows):
        with path.open("w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["Sygnatura czasowa", "Pseudonim albo kod testera", "Który raport", "Model telefonu",
                        "Ile minut dziennie grasz", "Co się podobało", "Co przeszkadzało albo nie działało",
                        "Wklej „Raport dla testera” albo „Zgłoś problem”"])
            for row in rows:
                w.writerow(row)

    def test_csv_full_wave_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "odpowiedzi.csv"
            rows = []
            for i in range(12):
                for day in (3, 10):
                    rows.append([f"2026-12-{9 + day}", f"Tester{i}", f"dzień {day}", f"Phone {i % 6}", "30",
                                 "ładne epoki" if i == 0 else "", "za długie bitwy" if i == 1 else "",
                                 "﻿" + tester(i, day).replace("\n", "\r\n")])
            # Duplikat (tester wysłał dwa razy) i zgłoszenie problemu.
            rows.append(["2026-12-20", "tester0 ", "Dzień 10", "Phone 0", "", "", "",
                         tester(0, 10).replace("Battle time: 2h 0m", "Battle time: 1h 0m")])
            rows.append(["2026-12-20", "Tester3", "problem", "", "", "", "", "Century War - raport techniczny\nx"])
            self._form_csv(path, rows)
            reports, dropped, problems, notes, checks, label = pm.merge([path])
            self.assertEqual(len(reports), 24)
            self.assertEqual(len(dropped), 1)
            self.assertEqual(problems, 1)
            self.assertIn("10. dnia (12)", label)
            by = {c.num: c for c in checks}
            self.assertEqual(by["2.4"].verdict, pm.OK)
            self.assertEqual(by["2.5"].verdict, pm.OK, by["2.5"].value)
            self.assertIn("mediana dni 7", by["2.5"].value)
            self.assertIn("mediana bitew 120 min", by["2.5"].value)
            for num in ("2.1", "2.2", "2.3"):
                self.assertEqual(by[num].verdict, pm.NA)
            self.assertEqual(by["2.12"].verdict, pm.OK)
            self.assertEqual(by["2.13"].verdict, pm.OK, by["2.13"].value)  # too hard: 3/12 = 25%
            self.assertEqual(by["2.14"].verdict, pm.OK)
            self.assertEqual(by["2.9"].verdict, pm.OK, by["2.9"].value)
            self.assertEqual(by["2.15"].verdict, pm.OK)
            self.assertEqual(by["2.11"].verdict, pm.OK)
            self.assertTrue(pm.gate_summary(checks).startswith("NIEROZSTRZYGNIĘTE"))
            # Dane z Play Console rozstrzygają twarde kryteria.
            *_, checks2, _ = pm.merge([path], external={"active_testers": 13, "crash_groups": 0, "prelaunch_crashes": 0})
            self.assertTrue(pm.gate_summary(checks2).startswith("PRZECHODZI"))
            *_, checks3, _ = pm.merge([path], external={"active_testers": 11, "crash_groups": 0, "prelaunch_crashes": 0})
            self.assertTrue(pm.gate_summary(checks3).startswith("NIE PRZECHODZI"))
            r0 = next(r for r in reports if r.form.get("tester") == "Tester0" and r.day == 10)
            self.assertEqual(r0.get("battle_s"), 7200, "zostaje raport z większym czasem bitew")

    def test_failing_soft_criteria(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            for i in range(4):
                (d / f"t{i}-dzien10.txt").write_text(build_report(
                    model=f"P{i}", stage=3, rating=1 if i == 0 else 3, issue=0, max_loss_streak=6, surrenders=5,
                    battles=20, tutorial_done=i != 0, first_win_s=-1 if i == 0 else 30 * 60,
                    perf=((40.0, 3, 100, 3),), mode_battles=(20, 0, 0, 0)), encoding="utf-8")
            reports, dropped, problems, notes, checks, label = pm.merge([d])
            by = {c.num: c for c in checks}
            self.assertEqual([r.day for r in reports], [10] * 4)
            for num in ("2.4", "2.6", "2.8", "2.9", "2.10", "2.11", "2.12", "2.13", "2.14", "2.15"):
                self.assertEqual(by[num].verdict, pm.FAIL, num)
            self.assertIn("R01", by["2.12"].note)
            self.assertIn("lejek etapów", by["2.10"].note)

    def test_day_inference_and_pairing_without_form(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d / "a.txt").write_text(build_report(model="Pixel 8", last_day=20_431), encoding="utf-8")
            (d / "b.txt").write_text(build_report(model="Pixel 8", last_day=20_438), encoding="utf-8")
            (d / "android15.txt").write_text(build_report(model="Moto", last_day=20_439), encoding="utf-8")
            reports, dropped, *_ = pm.merge([d])
            days = {r.source: (r.day, r.day_inferred) for r in reports}
            self.assertEqual(days["a.txt"], (3, True))
            self.assertEqual(days["b.txt"], (10, True))
            self.assertEqual(days["android15.txt"], (10, True))
            a, b = (next(r for r in reports if r.source == s) for s in ("a.txt", "b.txt"))
            self.assertNotEqual(a.key, b.key, "inne nazwy plików = inne osoby")
            # Pseudonim z nazwy pliku łączy dzień 3 i 10.
            (d / "kasia-dzien3.txt").write_text(build_report(model="Pixel 8", last_day=20_438), encoding="utf-8")
            (d / "kasia_d10.txt").write_text(build_report(model="Pixel 8", last_day=20_431), encoding="utf-8")
            reports, dropped, *_ = pm.merge([d])
            k3, k10 = (next(r for r in reports if r.source == s) for s in ("kasia-dzien3.txt", "kasia_d10.txt"))
            self.assertEqual((k3.day, k10.day, k3.day_inferred), (3, 10, False), "dzień z nazwy pliku ma pierwszeństwo")
            self.assertEqual(k3.key, k10.key)
            self.assertEqual(k3.key, "pseud:kasia|pixel 8")

    def test_no_identity_is_not_deduplicated(self):
        """CSV bez pseudonimu: dwie osoby z tym samym telefonem i dniem startu zostają osobno."""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "f.csv"
            with path.open("w", encoding="utf-8", newline="") as fh:
                w = csv.writer(fh)
                w.writerow(["Timestamp", "Paste report"])
                w.writerow(["1", build_report(sessions=10)])
                w.writerow(["2", build_report(sessions=11)])
                w.writerow(["3", build_report(sessions=11)])  # ten sam tekst wklejony drugi raz
            reports, dropped, *_ = pm.merge([path])
            self.assertEqual((len(reports), len(dropped)), (2, 1))
            self.assertTrue(all(r.key.startswith("model:") for r in reports))

    def test_future_v2_fields(self):
        v2 = build_report().replace("Language: pl\n", "Language: pl\nReport format: 2\nTester code: K7QM\n"
                                    "Crashes: 1\nANRs: 0\n")
        v2 = v2.replace("Screens visited:", "S19: 4 tries, 1 win, 0 surr, 52m, cmd 3\nScreens visited:")
        r = pm.parse_report(v2, "v2")
        self.assertEqual((r.format, r.get("tester_code"), r.get("crashes")), (2, "K7QM", "1"))
        self.assertEqual(r.stages[19], (4, 1, 0, 3))
        self.assertEqual(r.status, "ok", r.problems)
        pm.assign_keys([r])
        self.assertEqual(r.key, "kod:K7QM")
        r.day = 10
        checks = pm.evaluate([], [r], [r], {})
        self.assertIn("suma w raportach: 1", next(c for c in checks if c.num == "2.2").value)

    def test_cli_writes_markdown_and_csv(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d / "in").mkdir()
            (d / "in/r-d3.txt").write_text(tester(1, 3), encoding="utf-8")
            (d / "in/r-d10.txt").write_text(tester(1, 10)[:900], encoding="utf-8")
            out, out_csv = d / "out/test-fala1.md", d / "out/fala1.csv"
            self.assertEqual(pm.main([str(d / "in"), "-o", str(out), "--csv-out", str(out_csv), "--fala", "1"]), 0)
            md = out.read_text(encoding="utf-8")
            self.assertIn("# Fala 1 testu zamkniętego", md)
            self.assertIn("## Bramka G2", md)
            self.assertIn("| 2.5 |", md)
            self.assertIn("| 2.15 |", md)
            self.assertIn("nie do policzenia", md)
            self.assertIn("obcięty", md)
            self.assertIn("Czego nie da się policzyć", md)
            with out_csv.open(encoding="utf-8") as fh:
                rows = list(csv.DictReader(fh))
            self.assertEqual(sorted(r["day"] for r in rows), ["10", "3"])


if __name__ == "__main__":
    unittest.main()
