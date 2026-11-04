#!/usr/bin/env bash
# Pomiar klifu etapu 18 -> 19 (R12, sekcja 2: M1, M2, opcjonalnie M3).
# Opis, oczekiwania i kryterium "ściany": docs/reports/d33-pomiar-19.md.
#
# Każde uruchomienie Gradle idzie kolejno przez flock (wspólna blokada kontenera),
# z --rerun (zmienne BALANCE_* nie są wejściem zadania testowego). Wyniki CSV/MD
# zapisuje BalanceReport w app/build/balance/, a ten skrypt składa je w jedno
# podsumowanie Markdown.
#
# Użycie (z katalogu repozytorium albo skądkolwiek):
#   tools/measure_stage19.sh                 # M1 + M2, N=100 (ok. 1-1,5 h)
#   tools/measure_stage19.sh --m3            # M1 + M2 + M3 (skale 0.94 0.90 0.86)
#   tools/measure_stage19.sh --only M3 --m3  # tylko M3
#   tools/measure_stage19.sh --summary-only  # tylko podsumowanie z istniejących CSV
#   tools/measure_stage19.sh --dry-run --m3  # wypisz polecenia, nic nie uruchamiaj
#
# Opcje:
#   --runs N            seedów na etap (domyślnie 100)
#   --m3                włącz M3 (warianty korekty etapu 19 przez BALANCE_POWER_SCALE)
#   --m3-scales "S..."  mnożniki dla M3 (domyślnie "0.94 0.90 0.86")
#   --only LISTA        tylko wybrane pomiary, np. M1,M2 albo M3 (M3 wymaga też --m3)
#   --lock PLIK         plik blokady (domyślnie /tmp/claude-0/gradle.lock)
#   --out PLIK          podsumowanie (domyślnie app/build/balance/d33-stage19-summary.md)
#   --dir KATALOG       katalog z raportami CSV (domyślnie app/build/balance; tylko dla
#                       --summary-only, bo Gradle zawsze pisze do app/build/balance)
#   --summary-only      bez Gradle, tylko podsumowanie
#   --dry-run           tylko wypisz polecenia Gradle
#   --keep-going        nie przerywaj po nieudanym uruchomieniu (domyślnie: przerwij)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RUNS=100
M3=false
M3_SCALES="0.94 0.90 0.86"
ONLY="M1,M2,M3"
LOCK=/tmp/claude-0/gradle.lock
BALANCE_DIR="$ROOT/app/build/balance"
OUT=""
SUMMARY_ONLY=false
DRY_RUN=false
KEEP_GOING=false

while [ $# -gt 0 ]; do
  case "$1" in
    --runs) RUNS="$2"; shift 2 ;;
    --m3) M3=true; shift ;;
    --m3-scales) M3_SCALES="$2"; shift 2 ;;
    --only) ONLY="$(echo "$2" | tr '[:lower:]' '[:upper:]')"; shift 2 ;;
    --lock) LOCK="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --dir) BALANCE_DIR="$2"; shift 2 ;;
    --summary-only) SUMMARY_ONLY=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    --keep-going) KEEP_GOING=true; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "Nieznana opcja: $1 (zob. --help)" >&2; exit 2 ;;
  esac
done

case "$RUNS" in ''|*[!0-9]*) echo "--runs: liczba całkowita, jest: $RUNS" >&2; exit 2 ;; esac
for S in $M3_SCALES; do
  case "$S" in ''|*[!0-9.]*) echo "--m3-scales: liczby z kropką, jest: $S" >&2; exit 2 ;; esac
done

[ -n "$OUT" ] || OUT="$BALANCE_DIR/d33-stage19-summary.md"
if ! $SUMMARY_ONLY && [ "$BALANCE_DIR" != "$ROOT/app/build/balance" ]; then
  echo "--dir działa tylko z --summary-only (Gradle pisze do app/build/balance)" >&2; exit 2
fi

wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

# Wszystkie zmienne, którymi steruje BalanceReport.campaignReport; każde uruchomienie
# zaczyna od czystego stanu, żeby nic nie przeciekło z powłoki ani z poprzedniego kroku.
BALANCE_VARS=(BALANCE_RUN BALANCE_RUNS BALANCE_PROFILE BALANCE_STAGE_FROM BALANCE_STAGE_TO
  BALANCE_STAGES BALANCE_TAG BALANCE_COMMANDER BALANCE_POWER_SCALE BALANCE_POWER_SCALE_STAGES
  BALANCE_PACE BALANCE_DAILY_MEDALS BALANCE_MINUTES_PER_DAY BALANCE_BREAK_MINUTES
  BALANCE_STRICT BALANCE_OUT)

FAILED=()
LOG_DIR="$BALANCE_DIR/d33-logs"

# run_one TAG FROM TO PROFILE [ZMIENNA=WARTOŚĆ ...]
run_one() {
  local tag="$1" from="$2" to="$3" profile="$4"; shift 4
  local unset_args=()
  local v
  for v in "${BALANCE_VARS[@]}"; do unset_args+=(-u "$v"); done
  # PERF_REPORT=true: Gradle pokazuje stdout testu (linie "BALANCE stage=..." w logu).
  local visible=(BALANCE_RUN=true BALANCE_RUNS="$RUNS" BALANCE_PROFILE="$profile"
    BALANCE_STAGE_FROM="$from" BALANCE_STAGE_TO="$to" BALANCE_TAG="$tag" "$@" PERF_REPORT=true
    flock "$LOCK" ./gradlew :app:testDebugUnitTest --rerun
    --tests '*BalanceReport.campaignReport*' --console=plain --no-configuration-cache)
  local cmd=(env "${unset_args[@]}" "${visible[@]}")
  local csv="$BALANCE_DIR/report-$from-$to-$tag.csv"
  echo "== $tag: etapy $from-$to, profil $profile ${*:-}"
  if $DRY_RUN; then
    printf '   env -u BALANCE_*'; printf ' %q' "${visible[@]}"; echo
    echo "   -> $csv"
    return 0
  fi
  mkdir -p "$LOG_DIR" "$(dirname "$LOCK")"
  rm -f "$csv"
  local start=$SECONDS
  if "${cmd[@]}" >"$LOG_DIR/$tag.log" 2>&1 && [ -s "$csv" ]; then
    echo "   OK ($(( (SECONDS - start) / 60 )) min), log: $LOG_DIR/$tag.log"
    grep '^BALANCE stage=' "$LOG_DIR/$tag.log" | sed 's/^/   /' || true
  else
    echo "   BŁĄD ($(( (SECONDS - start) / 60 )) min), log: $LOG_DIR/$tag.log" >&2
    tail -n 30 "$LOG_DIR/$tag.log" >&2 || true
    FAILED+=("$tag")
    $KEEP_GOING || { echo "Przerwano (użyj --keep-going, żeby kontynuować)." >&2; exit 1; }
  fi
}

if ! $SUMMARY_ONLY; then
  if ! $DRY_RUN; then
    command -v flock >/dev/null || { echo "Brak flock" >&2; exit 1; }
    [ -x ./gradlew ] || { echo "Brak ./gradlew w $ROOT" >&2; exit 1; }
  fi
  # M1: klif 18 -> 19 na obecnych profilach (bez zmian w kodzie gry), etapy 16-24.
  if wants M1; then
    run_one d33-m1-medale 16 24 medale
    run_one d33-m1-target 16 24 target
    run_one d33-m1-target-cmd 16 24 target BALANCE_COMMANDER=on
  fi
  # M2: medale z czasu gry, tempo przed skróceniem (0.8.4) i teraz (0.8.8), etapy 16-30.
  if wants M2; then
    run_one d33-m2-before 16 30 medale-czas BALANCE_PACE=before
    run_one d33-m2-now 16 30 medale-czas BALANCE_PACE=now
  fi
  # M3: korekta etapu 19 (mnożnik siły AI tylko na etapie 19), etapy 18-20.
  if $M3 && wants M3; then
    for S in $M3_SCALES; do
      run_one "d33-m3-medale-$S" 18 20 medale BALANCE_POWER_SCALE="$S" BALANCE_POWER_SCALE_STAGES=19
      run_one "d33-m3-target-$S" 18 20 target BALANCE_POWER_SCALE="$S" BALANCE_POWER_SCALE_STAGES=19
      run_one "d33-m3-target-cmd-$S" 18 20 target BALANCE_POWER_SCALE="$S" BALANCE_POWER_SCALE_STAGES=19 BALANCE_COMMANDER=on
      run_one "d33-m3-czas-now-$S" 18 20 medale-czas BALANCE_PACE=now BALANCE_POWER_SCALE="$S" BALANCE_POWER_SCALE_STAGES=19
    done
  fi
fi

$DRY_RUN && exit 0

mkdir -p "$(dirname "$OUT")"
python3 - "$BALANCE_DIR" "$OUT" "$M3_SCALES" "$RUNS" <<'PY'
import csv, math, sys
from pathlib import Path

balance_dir, out, scales, runs_arg = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3].split(), sys.argv[4]

def load(name):
    """report-<from>-<to>-<tag>.csv -> {etap: wiersz}."""
    path = balance_dir / name
    if not path.exists():
        return None
    rows = {}
    with path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            rows[int(row["stage"])] = row
    return rows

def num(row, key):
    try:
        v = float(row.get(key, "nan"))
    except (TypeError, ValueError):
        return math.nan
    return v

def f1(v, suffix=""):
    if v is None or math.isnan(v):
        return "—"
    if math.isinf(v):
        return "∞"
    return f"{v:.1f}{suffix}".replace(".", ",")

def f2(v):
    return "—" if v is None or math.isnan(v) else f"{v:.2f}".replace(".", ",")

def cell(row):
    if row is None:
        return "—"
    return (f"{f1(num(row, 'win_rate_pct'), '%')} / {f1(num(row, 'median_minutes'))} / "
            f"{f1(num(row, 'expected_minutes'))} / {f2(num(row, 'mean_win_stars'))}")

def ci(row):
    """95% przedział dla odsetka wygranych (przybliżenie normalne) w pp."""
    n = num(row, "n"); p = num(row, "win_rate_pct") / 100.0
    if not n or math.isnan(p):
        return math.nan
    return 196.0 * math.sqrt(max(p * (1 - p), 0.0) / n)

STAGES = range(16, 25)
M1 = [("medale", "report-16-24-d33-m1-medale.csv"),
      ("docelowy", "report-16-24-d33-m1-target.csv"),
      ("docelowy + dowódca", "report-16-24-d33-m1-target-cmd.csv")]
M2 = [("medale-czas, przed (0.8.4)", "report-16-30-d33-m2-before.csv"),
      ("medale-czas, teraz (0.8.8)", "report-16-30-d33-m2-now.csv")]

doc = ["# Pomiar klifu etapu 18 → 19 (d33)", "",
       f"Źródło: `{balance_dir.name}/report-*-d33-*.csv` (BalanceReport.campaignReport, N={runs_arg} "
       "seedów na etap, poziom Normalny). Oczekiwany czas = średnia wygrana + (1 − p)/p średnich "
       "porażek. Kryterium „ściany”: `docs/reports/d33-pomiar-19.md`.", ""]

profiles = [(label, load(name)) for label, name in M1 + M2]
present = [(l, r) for l, r in profiles if r]
missing = [name for (l, name), (_, r) in zip(M1 + M2, profiles) if not r]

doc += ["## Etapy 16–24 per profil", "",
        "Komórka: wygrane / mediana [min] / oczekiwany czas [min] / ★ wygranych.", ""]
if present:
    doc.append("| Etap | " + " | ".join(l for l, _ in present) + " |")
    doc.append("|---:|" + "---:|" * len(present))
    for s in STAGES:
        cells = [cell(rows.get(s)) for _, rows in present]
        doc.append(f"| {s} | " + " | ".join(cells) + " |")
    doc.append("")
    lv = [(l, r) for l, r in present if any(x.get("research_levels") for x in r.values())]
    if lv:
        doc += ["Poziomy badań (ze 110) w profilach medalowych:", "",
                "| Etap | " + " | ".join(l for l, _ in lv) + " |", "|---:|" + "---:|" * len(lv)]
        for s in STAGES:
            doc.append(f"| {s} | " + " | ".join((r.get(s) or {}).get("research_levels", "—") or "—" for _, r in lv) + " |")
        doc.append("")
if missing:
    doc += ["Brak plików: " + ", ".join(f"`{m}`" for m in missing), ""]

# Ocena klifu 18 -> 19 dla każdego profilu.
def verdict(rows):
    a, b = rows.get(18), rows.get(19)
    if not a or not b:
        return None
    e18, e19 = num(a, "expected_minutes"), num(b, "expected_minutes")
    ratio = e19 / e18 if e18 and not math.isnan(e18) and e18 > 0 else math.nan
    drop = num(a, "win_rate_pct") - num(b, "win_rate_pct")
    if (not math.isnan(ratio) and ratio > 2.0) or drop > 25.0 or num(b, "win_rate_pct") < 40.0:
        v = "**ŚCIANA**"
    elif (not math.isnan(ratio) and ratio > 1.6) or drop > 15.0 or num(b, "win_rate_pct") < 50.0:
        v = "strefa ostrzegawcza"
    else:
        v = "płynnie"
    return ratio, drop, v, a, b

doc += ["## Klif 18 → 19", "",
        "| Profil | Wygrane 18 → 19 (±95%) | Spadek [pp] | Oczek. 18 → 19 [min] | Iloraz | Ocena |",
        "|---|---:|---:|---:|---:|---|"]
for label, rows in present:
    res = verdict(rows)
    if res is None:
        continue
    ratio, drop, v, a, b = res
    doc.append(f"| {label} | {f1(num(a, 'win_rate_pct'), '%')} → {f1(num(b, 'win_rate_pct'), '%')} "
               f"(±{f1(ci(b))}) | {f1(drop)} | {f1(num(a, 'expected_minutes'))} → "
               f"{f1(num(b, 'expected_minutes'))} | {f1(ratio)}× | {v} |")
doc += ["", "Ściana: iloraz oczekiwanych czasów > 2,0, spadek wygranych > 25 pp albo etap 19 < 40%. "
        "Strefa ostrzegawcza: > 1,6, > 15 pp albo < 50%. Decyduje profil `medale-czas, teraz` "
        "(realny gracz), a potwierdza `medale`.", ""]

# M2: przed vs teraz.
before, now = load(M2[0][1]), load(M2[1][1])
if before and now:
    doc += ["## M2: przed vs teraz (medale-czas, etapy 16–30)", "",
            "| Etap | Poziomy przed → teraz | Wygrane przed → teraz | Różnica [pp] | Oczek. przed → teraz [min] | Uwaga |",
            "|---:|---:|---:|---:|---:|---|"]
    worst = None
    for s in sorted(set(before) & set(now)):
        a, b = before[s], now[s]
        d = num(b, "win_rate_pct") - num(a, "win_rate_pct")
        note = "> −5 pp: rozważyć złagodzenie" if s >= 19 and d < -5.0 else ""
        if s >= 19 and (worst is None or d < worst[1]):
            worst = (s, d)
        doc.append(f"| {s} | {a.get('research_levels', '—')} → {b.get('research_levels', '—')} | "
                   f"{f1(num(a, 'win_rate_pct'), '%')} → {f1(num(b, 'win_rate_pct'), '%')} | {f1(d)} | "
                   f"{f1(num(a, 'expected_minutes'))} → {f1(num(b, 'expected_minutes'))} | {note} |")
    if worst:
        doc += ["", f"Największa zmiana na 19–30: etap {worst[0]}, {f1(worst[1])} pp "
                "(model R12: −1…−3 pp; szum dwóch pomiarów N=100 to ok. ±7 pp na etap)."]
    doc.append("")

# M3: warianty korekty etapu 19.
m3_rows = []
for s in scales:
    for label, tag in (("medale", "medale"), ("docelowy", "target"),
                       ("docelowy + dowódca", "target-cmd"), ("medale-czas, teraz", "czas-now")):
        rows = load(f"report-18-20-d33-m3-{tag}-{s}.csv")
        if rows:
            m3_rows.append((s, label, rows))
if m3_rows:
    m1_by_label = {"medale": load(M1[0][1]), "docelowy": load(M1[1][1]),
                   "docelowy + dowódca": load(M1[2][1]), "medale-czas, teraz": now}
    doc += ["## M3: korekta etapu 19 (BALANCE_POWER_SCALE tylko na etapie 19)", "",
            "Korekta etapu 19 w `Campaign.stageRamp` (`19 -> 1.265f`) odpowiada 1,265 × mnożnik. "
            "Cele R12: `medale` na 19 ≥ 60%, oczekiwany czas 19 ≤ 1,6× etapu 18 (tego samego profilu), "
            "profil docelowy na 19 ≤ 75%.", "",
            "| Mnożnik | Korekta 19 | Profil | Wygrane 19 | Oczek. 19 [min] | Iloraz do 18 | Wygrane 20 | Cele |",
            "|---:|---:|---|---:|---:|---:|---:|---|"]
    for s, label, rows in m3_rows:
        r19, r18, r20 = rows.get(19), rows.get(18), rows.get(20)
        if not r19:
            continue
        e18 = num(r18, "expected_minutes") if r18 else math.nan
        if math.isnan(e18) and m1_by_label.get(label) and m1_by_label[label].get(18):
            e18 = num(m1_by_label[label][18], "expected_minutes")
        ratio = num(r19, "expected_minutes") / e18 if e18 and not math.isnan(e18) else math.nan
        w19 = num(r19, "win_rate_pct")
        goals = []
        if label == "medale":
            goals.append("≥60% ✓" if w19 >= 60 else "≥60% ✗")
        if label.startswith("docelowy"):
            goals.append("≤75% ✓" if w19 <= 75 else "≤75% ✗")
        if not math.isnan(ratio):
            goals.append("≤1,6× ✓" if ratio <= 1.6 else "≤1,6× ✗")
        correction = f"{1.265 * float(s):.3f}".replace(".", ",")
        doc.append(f"| {s} | {correction} | {label} | "
                   f"{f1(w19, '%')} | {f1(num(r19, 'expected_minutes'))} | {f1(ratio)}× | "
                   f"{f1(num(r20, 'win_rate_pct'), '%') if r20 else '—'} | {', '.join(goals)} |")
    doc += ["", "Uwaga: mnożnik w teście i ta sama wartość wpisana w `stageRamp` mogą dać inne wyniki "
            "(zaokrąglenie aiPower zmienia przebieg seedów, docs/BALANCE.md, etap 16). Po wyborze "
            "wartości trzeba zmierzyć stan z kodu.", ""]

out.write_text("\n".join(doc) + "\n", encoding="utf-8")
print(out.read_text(encoding="utf-8"))
PY

echo "Podsumowanie: $OUT"
if [ "${#FAILED[@]}" -gt 0 ]; then
  echo "Nieudane uruchomienia: ${FAILED[*]}" >&2
  exit 1
fi
