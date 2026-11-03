#!/usr/bin/env bash
# Test skryptu convert_music.sh na sygnałach testowych ffmpeg (sinus z ciszą na brzegach).
# Uruchom: scripts/test_convert_music.sh   (wymaga ffmpeg; nie jest częścią builda)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
IN="$WORK/in"; OUT="$WORK/out"; OUT2="$WORK/out2"
mkdir -p "$IN"

# 1 s ciszy + 6 s tonu + 1 s ciszy; różne formaty, poziomy i wielkość liter.
tone() { # nazwa częstotliwość głośność
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "anullsrc=r=48000:cl=stereo:d=1" \
    -f lavfi -i "sine=f=$2:r=48000:d=6" -f lavfi -i "anullsrc=r=48000:cl=stereo:d=1" \
    -filter_complex "[1]volume=$3,aformat=channel_layouts=stereo[t];[0][t][2]concat=n=3:v=0:a=1" "$IN/$1"
}
tone menu.wav 440 0.9
tone ERA3_Battle.flac 330 0.2
tone era3_calm.mp3 220 0.5
tone victory.m4a 523 0.7
tone nieznany.wav 100 0.5
tone "era3_battle (1).wav" 330 0.5

log="$("$ROOT/scripts/convert_music.sh" "$IN" "$OUT" 2>&1)"
echo "$log"

fail() { echo "BŁĄD: $*" >&2; exit 1; }
for n in menu era3_battle era3_calm victory; do
  [[ -f "$OUT/$n.ogg" ]] || fail "brak $n.ogg"
  codec="$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels -of csv=p=0 "$OUT/$n.ogg")"
  [[ "$codec" == "vorbis,44100,2" ]] || fail "$n: format $codec"
  len="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$OUT/$n.ogg")"
  if [[ "$n" == victory ]]; then
    awk -v d="$len" 'BEGIN { exit !(d > 5.7 && d < 6.4) }' || fail "$n: cisza nieprzycięta ($len s)"
  else
    # Pętla: 6 s tonu minus nakładka (najwyżej 1/4 nagrania = 1,5 s) zszyta w pliku.
    awk -v d="$len" 'BEGIN { exit !(d > 4.3 && d < 4.7) }' || fail "$n: pętla $len s (oczekiwane ~4,5 s)"
  fi
done
[[ $(ls "$OUT" | wc -l) -eq 4 ]] || fail "nieznane pliki trafiły do wyjścia: $(ls "$OUT")"
grep -q "nieznany.wav" <<<"$log" || fail "brak ostrzeżenia o nieznanym pliku"
grep -q "era3_battle (1).wav" <<<"$log" || fail "brak ostrzeżenia o pliku z dopiskiem"
# Głośność: wszystkie ok. -16 LUFS mimo różnych poziomów wejścia.
while read -r name _ _ _ lufs; do
  [[ "$name" == *.ogg ]] || continue
  awk -v l="$lufs" 'BEGIN { exit !(l > -17.5 && l < -14.5) }' || fail "$name: $lufs LUFS"
done <<<"$log"
# Pętle mają znacznik ANDROID_LOOP, przerywniki nie.
ffprobe -v error -show_entries format_tags=ANDROID_LOOP:stream_tags=ANDROID_LOOP -of default=nw=1 "$OUT/menu.ogg" | grep -qi "true" \
  || fail "menu.ogg bez ANDROID_LOOP"
if ffprobe -v error -show_entries format_tags:stream_tags -of default=nw=1 "$OUT/victory.ogg" | grep -qi "ANDROID_LOOP"; then
  fail "victory.ogg nie powinien być pętlą"
fi
# Złączenie pętli bez „oddechu”: RMS pierwszych i ostatnich 50 ms jak w środku (stały ton).
edge_rms() { # plik początek_s
  ffmpeg -hide_banner -nostats -ss "$2" -t 0.05 -i "$1" -ac 1 -af astats -f null - 2>&1 \
    | grep "RMS level dB" | tail -n1 | sed -E 's/.*: //'
}
for n in menu era3_calm; do
  mid="$(edge_rms "$OUT/$n.ogg" 2)"
  for at in 0 4.4; do
    e="$(edge_rms "$OUT/$n.ogg" "$at")"
    awk -v a="$e" -v b="$mid" 'BEGIN { d = a - b; exit !(d > -3 && d < 3) }' \
      || fail "$n: poziom przy złączeniu ($at s) $e dB, w środku $mid dB"
  done
done
# Determinizm: drugi przebieg daje identyczne bajty.
"$ROOT/scripts/convert_music.sh" "$IN" "$OUT2" >/dev/null 2>&1
for f in "$OUT"/*.ogg; do cmp -s "$f" "$OUT2/$(basename "$f")" || fail "niedeterministyczny $(basename "$f")"; done
echo "OK: convert_music.sh"
