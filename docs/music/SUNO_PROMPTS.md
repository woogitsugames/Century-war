# Muzyka Century War: opisy dla Suno

Decyzja właściciela (1.11.2026): muzyka z Suno dla menu i wszystkich epok, każda epoka w dwóch wersjach: **spokojnej** (przygotowanie, przewaga) i **bojowej** (starcie, presja). Wgranie do gry: zlecenie 142.

## Zasady generowania (ważne)

1. **Płatny plan Suno** w chwili generowania (prawo do użytku komercyjnego). Zapisz datę i plan w `docs/music/LICENSE_NOTES.md`.
2. Każdy utwór **instrumentalny** (włącz „Instrumental”), bez wokalu i słów.
3. Długość 2:00–3:00. Na końcu poproś o pętlę: w opisie jest „seamless loop, no fade out, no ending”. Nie szkodzi, jeśli Suno doda zakończenie — przytniemy w kodzie.
4. **Wersja spokojna i bojowa tej samej epoki:** wygeneruj najpierw bojową, potem spokojną przez „Cover/Remix” albo „Extend” tej samej piosenki z opisem spokojnym — wtedy obie mają tę samą tonację i tempo, a gra płynnie je przenika. Jeśli to niemożliwe — trzymaj podane BPM i tonację.
5. Jeśli Twoja wersja Suno umożliwia **eksport ścieżek (stems)**: pobierz dla wersji bojowej osobno perkusję, bas i resztę. Gra może wtedy dokładać perkusję przy rosnącej presji. Nie jest to wymagane.
6. Nie wpisuj nazw innych gier, filmów ani artystów. Odsłuchaj każdy utwór: jeśli przypomina znaną melodię — wygeneruj ponownie.
7. Pliki: najlepiej **WAV** (przekonwertujemy do OGG). Nazwy jak w tabeli niżej, np. `era3_battle.wav`.

## Wspólny styl (dopisz do każdego opisu)

> `orchestral game soundtrack, cartoon strategy, energetic but not cheesy, clear melody, punchy drums, seamless loop, no fade out, no ending, instrumental`

## Utwory

| Plik | Nastrój | BPM / tonacja | Opis dla Suno (wklej + wspólny styl) |
|---|---|---|---|
| `menu.wav` | heroiczny, zapraszający | 96 / D-dur | `heroic main theme, brass fanfare opening then warm strings, sense of a long journey through history, hopeful and adventurous` |
| `victory.wav` (10–15 s) | triumf | 120 / D-dur | `short victory fanfare, brass and timpani, triumphant, 12 seconds, clear ending` (tu zakończenie jest pożądane) |
| `defeat.wav` (10–15 s) | porażka, ale „spróbuj jeszcze” | 80 / d-moll | `short defeat sting, low strings and soft horn, somber but encouraging, 12 seconds, clear ending` |
| `era0_calm.wav` Plemiona | pierwotny, czujny | 100 / e-moll | `tribal percussion, frame drums, bone rattles, low wooden flute, campfire night, tense calm` |
| `era0_battle.wav` | dziki, nacierający | 132 / e-moll | `primal war drums, stomping rhythm, hand claps, shouting-free, aggressive low flute riff, rising intensity` |
| `era1_calm.wav` Epoka brązu | pustynny, tajemniczy | 98 / d-moll (frygijska) | `ancient desert, hand drums, plucked lyre, reed pipe, warm sand wind, mysterious` |
| `era1_battle.wav` | marsz rydwanów | 130 / d-moll | `ancient battle march, heavy frame drums, bronze cymbals, lyre ostinato, war horns, driving chariot rhythm` |
| `era2_calm.wav` Epoka żelaza | dostojny, legiony | 96 / g-moll | `ancient empire, low brass, steady snare, strings, disciplined and proud, calm before battle` |
| `era2_battle.wav` | legiony w szyku | 128 / g-moll | `marching legions, heavy snare and bass drum, brass ostinato, staccato strings, relentless advance` |
| `era3_calm.wav` Feudalizm | zamek, poranek | 92 / a-moll | `medieval castle morning, lute, recorder, soft hand drum, banners in the wind` |
| `era3_battle.wav` | szarża rycerzy | 134 / a-moll | `medieval battle, galloping rhythm, war drums, horns, fiddle and hurdy-gurdy riff, knights charge` |
| `era4_calm.wav` Proch | renesans, napięcie | 94 / c-moll | `early gunpowder era, harpsichord, strings, military snare roll, tense anticipation` |
| `era4_battle.wav` | salwy muszkietów | 136 / c-moll | `musket battle, snare rolls, cannon-like timpani hits, fast strings, brass calls, heroic and tense` |
| `era5_calm.wav` Para | fabryki, rytm maszyn | 100 / f-moll | `steam age, mechanical clockwork percussion, brass band, accordion, rhythmic engines, industrious` |
| `era5_battle.wav` | pancerne pociągi | 138 / f-moll | `steam age war, pounding piston percussion, brass band march, low tuba riff, whistles, unstoppable machines` |
| `era6_calm.wav` Stal | okopy, cisza przed burzą | 90 / b-moll | `early 20th century war, solemn low brass, distant snare, strings, uneasy calm` |
| `era6_battle.wav` | czołgi, artyleria | 140 / b-moll | `industrial warfare, heavy orchestral percussion, low brass hits, driving strings ostinato, epic and dramatic` |
| `era7_calm.wav` Krzem | nowoczesny, taktyczny | 100 / e-moll | `modern tactical, synth pads, light electronic beat, orchestral strings, focused and cool` |
| `era7_battle.wav` | drony, presja | 142 / e-moll | `modern hybrid orchestral electronic, pulsing synth bass, big drums, staccato strings, high tension` |
| `era8_calm.wav` Orbita | kosmos, przestrzeń | 90 / d-moll | `space age, ambient synth pads, celesta, slow pulse, vast and wondrous` |
| `era8_battle.wav` | bitwa orbitalna | 144 / d-moll | `sci-fi battle, arpeggiated synths, huge drums, choir pads without words, epic and urgent` |

**Warianty epok** (Diesel/Tesla, Rój/Biotech) używają muzyki swojej epoki — nie trzeba osobnych utworów.

## Razem
1 menu + 2 krótkie przerywniki + 9 epok × 2 = **21 utworów**. Rozmiar po konwersji do OGG (ok. 96 kb/s): ok. 30–35 MB. Jeśli to za dużo, gra może pobierać muzykę epok później (decyzja przy zleceniu 142).

## Co dalej
Wrzuć pliki do repo do katalogu `music-src/` (albo daj mi zip) — wtedy wyślę zlecenie 142 (wgranie, pętle, przenikanie spokojna↔bojowa, głośność, test pamięci na tanim telefonie).
