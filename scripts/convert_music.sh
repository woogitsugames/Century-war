#!/usr/bin/env bash
# Konwersja muzyki z Suno do assets gry (NIE jest częścią builda; wymaga ffmpeg i ffprobe).
#
#   scripts/convert_music.sh <katalog_z_plikami> [katalog_wyjściowy]
#
# Wejście: WAV/MP3/M4A/FLAC o nazwach z docs/music/SUNO_PROMPTS.md (wielkość liter bez
# znaczenia, np. menu.wav, ERA3_Battle.flac). Inne pliki są pomijane z ostrzeżeniem.
# Wyjście (domyślnie app/src/main/assets/music/): <nazwa>.ogg, OGG Vorbis ~96 kb/s,
# stereo 44,1 kHz, cisza na początku/końcu przycięta, głośność -16 LUFS (dwa przejścia
# loudnorm), pętle (wszystko poza victory/defeat) z wejściem 0,5 s i wyjściem 1,5 s.
# Wynik jest deterministyczny (flagi bitexact, bez metadanych źródła).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IN="${1:-}"
OUT="${2:-$ROOT/app/src/main/assets/music}"
TARGET_LUFS="${TARGET_LUFS:--16}"
TARGET_TP=-1.5
TARGET_LRA=11
FADE_IN=0.5
FADE_OUT=1.5

if [[ -z "$IN" || ! -d "$IN" ]]; then
  echo "Użycie: $0 <katalog_z_plikami> [katalog_wyjściowy]" >&2
  exit 2
fi
for tool in ffmpeg ffprobe; do
  command -v "$tool" >/dev/null || { echo "Brak $tool w PATH" >&2; exit 2; }
done

NAMES=(menu victory defeat)
for e in 0 1 2 3 4 5 6 7 8; do NAMES+=("era${e}_calm" "era${e}_battle"); done
known() { local n; for n in "${NAMES[@]}"; do [[ "$n" == "$1" ]] && return 0; done; return 1; }

# Koder: libvorbis (jakość ~96 kb/s); zapasowo wbudowany, eksperymentalny koder ffmpeg.
if ffmpeg -hide_banner -encoders 2>/dev/null | grep -qE '^ A[^ ]+ libvorbis '; then
  ENC=(-c:a libvorbis -q:a 2.5)
else
  echo "UWAGA: ffmpeg bez libvorbis, używam wbudowanego kodera vorbis (gorsza jakość)." >&2
  ENC=(-c:a vorbis -strict -2 -b:a 96k)
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

# Przycięcie ciszy (próg -50 dB) z obu stron: silenceremove + odwrócenie.
TRIM="silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05,areverse"

# Wartość pola z JSON-a loudnorm (ostatni blok w stderr ffmpeg).
json_field() { grep -o "\"$1\" : \"[^\"]*\"" | tail -n1 | sed -E 's/.*: "([^"]*)"/\1/'; }
measure() {
  ffmpeg -hide_banner -nostats -i "$1" -af "loudnorm=I=$TARGET_LUFS:TP=$TARGET_TP:LRA=$TARGET_LRA:print_format=json" \
    -f null - 2>&1
}
duration() { ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$1"; }

declare -A SEEN=()
DONE=()
shopt -s nullglob nocaseglob
for src in "$IN"/*; do
  [[ -f "$src" ]] || continue
  file="$(basename "$src")"
  ext="${file##*.}"; ext="${ext,,}"
  base="${file%.*}"; base="${base,,}"
  case "$ext" in wav|mp3|m4a|flac) ;; *) echo "UWAGA: pomijam $file (nieobsługiwany format)" >&2; continue ;; esac
  if ! known "$base"; then
    echo "UWAGA: pomijam $file (nazwa spoza listy SUNO_PROMPTS)" >&2
    continue
  fi
  if [[ -n "${SEEN[$base]:-}" ]]; then
    echo "UWAGA: pomijam $file ($base już z ${SEEN[$base]})" >&2
    continue
  fi
  SEEN[$base]="$file"

  trimmed="$TMP/$base.wav"
  ffmpeg -hide_banner -loglevel error -y -i "$src" -vn -af "$TRIM" -ar 44100 -ac 2 -c:a pcm_s16le "$trimmed"
  len="$(duration "$trimmed")"
  stats="$(measure "$trimmed")"
  mi="$(json_field input_i <<<"$stats")"
  mtp="$(json_field input_tp <<<"$stats")"
  mlra="$(json_field input_lra <<<"$stats")"
  mth="$(json_field input_thresh <<<"$stats")"
  moff="$(json_field target_offset <<<"$stats")"

  filter="loudnorm=I=$TARGET_LUFS:TP=$TARGET_TP:LRA=$TARGET_LRA:measured_I=$mi:measured_TP=$mtp:measured_LRA=$mlra:measured_thresh=$mth:offset=$moff:linear=true,aresample=44100"
  meta=()
  if [[ "$base" != victory && "$base" != defeat ]]; then
    # Pętla: łagodne wejście i wyjście, żeby złączenie nie trzaskało; ANDROID_LOOP = pętla bez przerwy.
    fo_start="$(awk -v d="$len" -v f="$FADE_OUT" 'BEGIN { s = d - f; if (s < 0) s = 0; printf "%.3f", s }')"
    filter="$filter,afade=t=in:st=0:d=$FADE_IN,afade=t=out:st=$fo_start:d=$FADE_OUT"
    meta=(-metadata ANDROID_LOOP=true)
  else
    # Przerywnik: tylko bardzo krótkie wyjście przeciw trzaskowi na ostatniej próbce.
    fo_start="$(awk -v d="$len" 'BEGIN { s = d - 0.05; if (s < 0) s = 0; printf "%.3f", s }')"
    filter="$filter,afade=t=out:st=$fo_start:d=0.05"
  fi
  out="$OUT/$base.ogg"
  ffmpeg -hide_banner -loglevel error -y -i "$trimmed" -af "$filter" -ar 44100 -ac 2 \
    -map_metadata -1 "${meta[@]}" -fflags +bitexact -flags:a +bitexact "${ENC[@]}" "$out"
  DONE+=("$base")
done

if [[ ${#DONE[@]} -eq 0 ]]; then
  echo "Nie przekonwertowano żadnego pliku." >&2
  exit 1
fi

printf '%-16s %9s %10s %8s\n' "plik" "długość" "rozmiar" "LUFS"
total=0
for base in "${NAMES[@]}"; do
  out="$OUT/$base.ogg"
  [[ " ${DONE[*]} " == *" $base "* ]] || continue
  len="$(duration "$out")"
  size="$(stat -c %s "$out")"
  total=$((total + size))
  lufs="$(measure "$out" | json_field input_i)"
  printf '%-16s %8.1fs %8.1f KB %8s\n' "$base.ogg" "$len" "$(awk -v s="$size" 'BEGIN{print s/1024}')" "$lufs"
done
printf 'Razem: %d plików, %.2f MB\n' "${#DONE[@]}" "$(awk -v s="$total" 'BEGIN{print s/1048576}')"
missing=()
for base in "${NAMES[@]}"; do [[ -f "$OUT/$base.ogg" ]] || missing+=("$base"); done
if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Brakuje jeszcze (gra użyje syntezy): ${missing[*]}"
fi
