# Zlecenie 16 — balans ulepszeń jednostek w epoce

## Założenia

Każda z 32 jednostek (8 epok × 4 role) ma trzy stopnie. Zakup kolejnego stopnia dotyczy **jednej roli w aktualnej epoce**, a nie całej armii. Nie zwiększa kosztu werbunku i wpływa na jednostki tworzone po zakupie. Jednostki już obecne na polu zachowują swój stopień. Po awansie epoki wszystkie cztery ulepszenia strony wracają do stopnia 1; oddziałów pozostających na polu nie osłabiamy.

Reset jest celowy: bez niego inwestycja w pierwszej epoce byłaby stałą premią przenoszoną na wielokrotnie silniejsze jednostki następnych epok. Wydatek konkuruje z rekrutacją i wieżami, spowalnia bezpośredni nacisk na awans epoki, ale nie prowadzi do nieograniczonego wzrostu parametrów.

## Parametry przed i po (model analityczny)

| Parametr | Przed zmianą / stopień 1 | Stopień 2 | Stopień 3 |
|---|---:|---:|---:|
| HP nowej jednostki | 100% | 120% | 140% |
| Obrażenia nowej jednostki | 100% | 120% | 140% |
| Piechota (MELEE), szybkość | 100% | 100% | 110% |
| Strzelec (RANGED), zasięg | 100% | 100% | 110% |
| Ciężka (HEAVY), szybkość szkolenia | 100% | 100% | 115% |
| Elita (ELITE), promień rażenia | bazowy | bazowy | bazowy + 25 jednostek świata |

Przykładowe koszty w **pierwszej epoce**, w złocie; oba zakupy są wymagane do stopnia 3:

| Rola | 1 → 2 | 2 → 3 | Łącznie do 3 |
|---|---:|---:|---:|
| MELEE | 65 | 136 | 201 |
| RANGED | 95 | 199 | 294 |
| HEAVY | 155 | 325 | 480 |
| ELITE | 255 | 535 | 790 |

Dla kolejnych epok koszty rosną według `Defs.costScale(era) = 1.9^era`, a wynik każdorazowo zaokrąglany jest w dół do liczby całkowitej. Mnożnik drugiego zakupu względem pierwszego wynosi 2,1. Premie HP i obrażeń kumulują się z badaniami, doktrynami i zbrojownią na etapie tworzenia nowej jednostki.

**Ograniczenie:** powyższa tabela jest wyliczeniem z zasad gry, nie pomiarem uzyskanym podczas kompilacji. Test `BattleSimTest.unitTierBalanceReport` drukuje parametry wszystkich 32 jednostek przed zmianą, na stopniu 2 i 3 oraz koszty. `BattleSimTest.campaignStagesFinish` śledzi przebieg AI kontra AI na 32 etapach; efekty praktyczne należy porównać z logami przed wdrożeniem.

## Strategie AI

AI kupuje ulepszenia, gdy ma co najmniej dwa oddziały rozważanej roli (w polu lub kolejce), albo pełną armię z co najmniej jednym takim oddziałem. Odkłada rezerwę na jednostki oraz rezygnuje z ulepszeń, jeśli awans epoki jest blisko (ponad 55% wymaganego XP), chyba że armia jest pełna. Wybiera role w ustalonej kolejności: strzelcy, wręcz, ciężkie, elitarne. Nie korzysta z ukrytych dotacji ani niedeterministycznych liczb losowych.

## Scenariusze regresji

- Koszty w każdej epoce, brak zakupu przy niedostatku złota, limit 3, blokada elity.
- Nowy wojownik otrzymuje premię; po awansie epoki kupione stopnie są kasowane, poprzedni weteran nie traci parametrów.
- AI wybiera ulepszenie obecnej formacji przy odpowiedniej rezerwie złota.
- Migawka v3 odtwarza `Side.unitTier` i `Fighter.tier` oraz dalszy deterministyczny przebieg bitwy.
- Istniejące testy determinizmu, wszystkich epok, języków, zrzutów i screenshotów pozostają obowiązujące.

## Ryzyka i dalsza kalibracja

- Inwestycja w stopnie 2–3 tuż przed awansem może być nieopłacalna; HUD pokazuje ostrzeżenie o resecie.
- Jednostki starej epoki znajdujące się w kolejce po awansie powstają na stopniu 1, a te na polu zachowują stopień. To świadomy kompromis bez zmiany formatu kolejki.
- Wzrost obrażeń obszarowych elity może przyspieszać niszczenie dużych skupisk; należy ocenić logi 32 etapów i długość potyczek.
- Format zapisów bitwy podniesiono z v2 do v3; migawki poprzedniej wersji są odrzucane zgodnie z dotychczasową polityką.
