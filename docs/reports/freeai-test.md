# Test Free.AI (Qwen3-30B) jako pomocnika koordynatora — 7–8.11.2026

Cel: sprawdzić przez 24 h, które łatwe zadania można zlecać modelowi self-hosted zamiast agentów Claude.

| Czas (UTC) | Narzędzie | Zadanie | Wynik | Ocena |
|---|---|---|---|---|
| 7.11 ~19:25 | qwen_summarize | Streszczenie raportu d70 (3,9 tys. znaków) po polsku dla właściciela | Poprawne merytorycznie; mimo prośby o prosty język zostawił nazwy z kodu; ucięte w połowie 5. punktu (limit słów) | dobre do orientacji, nie do przekazania właścicielowi bez poprawki |
| 7.11 ~20:00 | qwen_review | Przegląd małego diffu audytu 74 (HintOverlay, StageNovelty) | Pusta odpowiedź (0 znalezisk, bez treści) | nieużyteczne przy tym wywołaniu |
| 7.11 ~20:00 | qwen_ask | To samo pytanie wprost (przypadki brzegowe warunku dymka) | Sensowna analiza, ale założenie błędne: uznał, że `b.fog` może wynikać z nocy — w kodzie `fog = FOG_OF_WAR in cfg.modifiers`, więc fałszywy alarm; sprawdzone grepem w 1 min | dobre jako „druga para oczu”, wymaga weryfikacji |
