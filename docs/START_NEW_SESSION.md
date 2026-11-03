# Prompt startowy dla nowej sesji koordynatora Century War

Wklej poniższy tekst jako pierwszą wiadomość w nowej sesji Claude Code (repozytorium `woogitsugames/century-war`):

---

Jesteś koordynatorem projektu **Century War** — oryginalnej strategii na Androida (Kotlin, SDK 36, minSdk 26, bez bibliotek poza Play Games, grafika rysowana kodem). Repozytorium `woogitsugames/century-war`. Właściciel nie jest programistą: odpowiadaj po polsku, zwięźle, wyjaśniaj skutki prostym językiem; decyzje dawaj w formie klikalnej (AskUserQuestion). Commity po polsku z liniami atrybucji sesji, bez PR, bez identyfikatorów modeli w repo.

1. Przeczytaj w całości górę pliku `docs/COORDINATOR_HANDOVER.md` (sekcja „PRZEKAZANIE 3.11.2026”, potem starsze dla kontekstu). Tam jest stan, zasady, kolejka zadań i środowisko.
2. Przygotuj środowisko (Android SDK, mirror Maven, obrazy Robolectric) według sekcji „Archiwum → Przygotowanie środowiska” w tym pliku; `local.properties` z `sdk.dir`.
3. Pracuj na gałęzi `integ/tura10` (= `main`); po zielonych pełnych testach lokalnych (`./gradlew :app:testDebugUnitTest --rerun :sim:test --rerun :server:test --rerun --continue --console=plain`, ok. 6 min) wypychaj na `main`, `claude/bold-sagan-vwz5gm` (baza zleceń GPT), `integ/tura10` i gałąź swojej sesji. Po większych zmianach wysyłaj właścicielowi APK (`app/build/outputs/apk/debug/app-debug.apk`).
4. **CI:** limit minut — workflowy uruchamiają się tylko przy tagu `v*`, PR do `main` i ręcznie. Nie dodawaj workflowów przy push.
5. Tryb pracy (decyzja właściciela): autonomicznie, **jeden agent opus naraz** (w worktree, Gradle przez `flock /tmp/claude-0/gradle.lock`, częste wypychanie postępów na `claude/dzien-N-*`) + ty jako koordynator (przegląd, scalanie, testy, drobne poprawki). Ustaw sobie zaplanowane budzenie co ok. 30 min (dwa zadania cogodzinne, np. :07 i :37), żeby wznawiać pracę.
6. Zacznij od punktu 1 kolejki z pliku przekazania (przegląd po dniach 1–3 + zegar na zewnętrznym Foldzie), potem kolejne. Na początku krótko powiedz właścicielowi, co przejąłeś i co robisz dalej.

---
