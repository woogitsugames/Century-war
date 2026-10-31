# Rozgałęzienia epok — raport z balansu

## Założenia

Dwie niezależne pary wariantów: Diesel/Tesla (epoka 4) oraz Cyber/Bio (epoka 6).
Symulacja wykonuje **240 deterministycznych seedów**, od 6100 do 6339.
Każdy seed jest uruchamiany dwukrotnie z zamianą stron, więc porównanie obejmuje
**480 pojedynków na epokę**. Start i maksymalna epoka są identyczne,
moc AI po obu stronach wynosi 1, obie strony mają te same bazowe bonusy.
Obie armie prowadzi to samo AI, a wariant jest ustalony przy starcie.
Pojedynek kończy się zwycięstwem, przegraną albo limitem 15 minut gry.
Remis daje po 0,5 punktu obu wariantom.

## Wyniki po korektach parametrów

| Epoka | Wariant A | Wygrane + 0,5 remisu | Wariant B | Wygrane + 0,5 remisu | Różnica |
| --- | --- | ---: | --- | ---: | ---: |
| Para (4) | Diesel | 47,8125% | Tesla | 52,1875% | 4,375 pp |
| Krzem (6) | Cyber | 47,9167% | Bio | 52,0833% | 4,1667 pp |

**Tolerancja ±5 punktów procentowych między współczynnikami wariantów jest zachowana
dla powyższej serii prób**. Wyniki pochodzą z lokalnego uruchomienia czystego
silnika Kotlin/JVM (bez zależności od Androida). Test
`VariantBalanceReportTest` w CI odtwarza dokładnie ten sam zakres seedów,
konfiguracje i punktację. Dokładne wyniki i potwierdzenie statusu CI
są dostępne w logach workflow.

### Mechaniki i styl

- **Diesel**: wolne i wytrzymałe jednostki, działa pancerne i mocniejsze osłony.
- **Tesla**: kruche jednostki, ataki łańcuchowe oraz elektryczne uderzenia obszarowe.
- **Cyber**: tańsze i szybsze drony, nacisk liczebny roju.
- **Bio**: droższe biomechy o dużej wytrzymałości i regeneracji.

Kluczowe parametry skorygowano po pierwszej próbie,
w której Tesla i Cyber miały zdecydowaną przewagę. Nie wykryto różnic w RNG:
doświadczenie jest deterministyczne dla danego seeda.
Wyniki nie dowodzą idealnej równowagi w innych trybach, zestawach badań,
poziomach trudności ani w rozgrywce przeciw człowiekowi.

## Odtwarzanie

```bash
./gradlew :app:testDebugUnitTest --tests '*VariantBalanceReportTest*' --console=plain --no-configuration-cache
```

Test sprawdza próg 5 pp i wypisuje wyniki obu par.
