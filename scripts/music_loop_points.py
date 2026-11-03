#!/usr/bin/env python3
"""Punkty pętli dla convert_music.sh (bez zależności poza ffmpeg i biblioteką standardową).

Użycie: music_loop_points.py <plik.wav 44,1 kHz> <nakładka_w_próbkach>
Wypisuje: "<start> <koniec>" w próbkach 44,1 kHz.

Utwory z Suno mają zwykle cichsze wejście i wybrzmienie na końcu; zszycie samych brzegów
daje na złączeniu dołek głośności („oddech”). Dlatego:
- koniec = ostatnie miejsce, gdzie poziom 1 s jest nie niższy niż mediana - 4 dB (najwyżej
  12 s przed końcem pliku), przesunięty o najwyżej 1,5 s wstecz tak, żeby rytm nakładki
  (obwiednia uderzeń 10 ms) najlepiej pokrywał się z rytmem początku pętli,
- start = pierwsze takie miejsce w ciągu pierwszych 12 s (inaczej 0).
"""
import math
import struct
import subprocess
import sys

SR = 44100
ENV_SR = 11025
BLOCK = ENV_SR // 100          # 10 ms
LEVEL_DB = 4.0                 # próg względem mediany
EDGE_S = 12.0                  # jak daleko od brzegu szukać
ALIGN_S = 1.5                  # zakres dopasowania rytmu końca


def envelope(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(ENV_SR), "-f", "s16le", "-"],
        capture_output=True, check=True).stdout
    n = len(raw) // 2
    samples = struct.unpack("<%dh" % n, raw[:n * 2])
    power = []
    for i in range(0, n - BLOCK + 1, BLOCK):
        s = 0
        for v in samples[i:i + BLOCK]:
            s += v * v
        power.append(s / BLOCK)
    return power


def main():
    path, xf = sys.argv[1], int(sys.argv[2])
    power = envelope(path)
    blocks = len(power)
    total = blocks * BLOCK * SR // ENV_SR
    xb = max(1, xf * 100 // SR)
    if blocks < 8 * xb or blocks < 400:
        print(0, total)
        return
    # Poziom 1 s (średnia mocy, okno wyśrodkowane), w dB.
    half = 50
    pre = [0.0]
    for p in power:
        pre.append(pre[-1] + p)
    def level(i):
        a, b = max(0, i - half), min(blocks, i + half)
        return 10 * math.log10(max((pre[b] - pre[a]) / (b - a), 1e-3))
    levels = [level(i) for i in range(blocks)]
    med = sorted(levels)[blocks // 2]
    edge = int(EDGE_S * 100)
    # Nakładka i sekunda za nią muszą być w pełni utworu (nie w cichym wstępie/wybrzmieniu):
    # pierwsze/ostatnie miejsce, gdzie najniższy poziom w tym oknie >= mediana - LEVEL_DB;
    # gdy takiego nie ma w zasięgu, miejsce o najwyższym takim minimum.
    span = xb + 100
    def window_min(a, b):
        return min(levels[max(0, a):min(blocks, b)])
    start, best_start = 0, None
    for i in range(0, min(edge, blocks // 6)):
        w = window_min(i, i + span)
        if w >= med - LEVEL_DB:
            start = i
            break
        if best_start is None or w > best_start[0] + 0.5:
            best_start = (w, i)
    else:
        start = best_start[1] if best_start else 0
    end0, best_end = blocks, None
    for i in range(blocks, max(blocks - edge, blocks // 2), -1):
        w = window_min(i - span, i)
        if w >= med - LEVEL_DB:
            end0 = i
            break
        if best_end is None or w > best_end[0] + 0.5:
            best_end = (w, i)
    else:
        end0 = best_end[1] if best_end else blocks
    # Obwiednia uderzeń: dodatnie przyrosty log-mocy.
    logp = [10 * math.log10(max(p, 1e-3)) for p in power]
    onset = [0.0] + [max(0.0, logp[i] - logp[i - 1]) for i in range(1, blocks)]
    head = onset[start:start + xb]
    hm = sum(head) / len(head)
    hv = math.sqrt(sum((v - hm) ** 2 for v in head))
    best, best_e = -2.0, end0
    if hv > 1e-6:
        for e in range(end0, max(end0 - int(ALIGN_S * 100), start + 4 * xb), -1):
            tail = onset[e - xb:e]
            tm = sum(tail) / xb
            tv = math.sqrt(sum((v - tm) ** 2 for v in tail))
            if tv <= 1e-6:
                continue
            c = sum((tail[k] - tm) * (head[k] - hm) for k in range(xb)) / (tv * hv)
            if c > best + 0.02:  # bliżej oryginalnego końca, gdy różnica jest znikoma
                best, best_e = c, e
    to_samples = lambda b: min(total, b * BLOCK * SR // ENV_SR)
    s, e = to_samples(start), to_samples(best_e)
    if e - s < 4 * xf:
        s, e = 0, total
    print(s, e)


if __name__ == "__main__":
    main()
