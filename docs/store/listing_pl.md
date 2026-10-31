# Century War — karta Google Play (PL)

## Tytuł
Century War: Wojna epok

## Krótki opis
9 epok, 36 jednostek, 36 etapów kampanii. Strategia bez internetu.

## Pełny opis

Poprowadź armię od wojowników plemiennych aż po erę orbitalną. W Century War walczysz o jeden front: szkol jednostki, stawiaj wieże, zdobywaj doświadczenie i przechodź do kolejnych epok.

Złoto pozwala wzmacniać wojsko i obronę, doświadczenie otwiera kolejne epoki, a doktryny zmieniają taktykę. Po bitwie otrzymujesz medale, które możesz przeznaczyć na trwałe badania.

CO CZEKA NA DOWÓDCĘ?
- **9 epok** — od plemion przez epokę brązu i żelaza po erę orbitalną.
- **36 jednostek** — po cztery role na epokę: walka wręcz, dystansowa, ciężka i elitarna.
- **36 etapów kampanii** — modyfikatory bitew i do 108 gwiazdek do zdobycia.
- **Oblężenie bez końca** — kolejne fale wroga, bossowie i wybór kart rozwijających armię.
- **Wyzwanie dnia** — wspólna konfiguracja bitwy i jedno podejście rankingowe dziennie; potem można trenować.
- **Potyczka** — cztery poziomy trudności.
- **Badania** — 13 ścieżek trwałego rozwoju.
- **Wieże, ostrzały specjalne i kontrola frontu** — taktyczne decyzje podczas każdej bitwy.
- **Rankingi lokalne** — własne rekordy w kampanii, potyczce, oblężeniu i wyzwaniu dnia.
- **Osiągnięcia i misje lokalne** — zdobywaj nagrody i śledź postępy.
- **Kodeks i samouczek** — poznaj jednostki, ich statystyki i zasady walki.
- **Wznawianie bitwy** — wróć do przerwanego starcia.
- **10 języków** — polski, angielski, niemiecki, hiszpański, francuski, włoski, portugalski, ukraiński, niderlandzki i turecki.

GRAJ BEZ STAŁEGO POŁĄCZENIA

Kampania, potyczka, oblężenie bez końca, wyzwanie dnia i lokalny zapis postępu **działają bez internetu**. Krótkie bitwy pasują również do gry w podróży. Jakość grafiki dostosowuje się do możliwości urządzenia, a interfejs obsługuje większe ekrany i urządzenia składane.

W wydaniach z **poprawnie skonfigurowanym Google Play Games** można dodatkowo korzystać z internetowych tablic wyników oraz synchronizacji profilu w chmurze po uwierzytelnieniu w Grach Play. Połączenie z Google nie jest wymagane do podstawowej rozgrywki. Osiągnięcia działają obecnie **lokalnie**; przesyłanie odblokowań do usługi osiągnięć Google Play Games nie jest jeszcze dostępne.

Po pierwszym zwycięstwie możesz otrzymywać **lokalne przypomnienie o wyzwaniu dnia**. Godzinę i włączenie przypomnień ustawiasz w grze; na nowszych Androidach wymagana jest zgoda na powiadomienia. Przypomnienia nie wymagają serwera push.

W grze nie ma reklam, zakupów w aplikacji ani rozgrywki wieloosobowej.

Zbuduj armię. Zmień epokę. Przejmij front.

## ASO — propozycje fraz (nie kopiować jako blok słów kluczowych)
strategia bez internetu; strategia Android; wojna epok; ewolucja armii; bitwy strategiczne; obrona bazy; kampania strategiczna; taktyka wojskowa; bitwy 2D; gry bez internetu.

## Kontrola przed publikacją
- **Limity Google Play:** tytuł **23/30 znaków**; krótki opis **66/80 znaków**.
- Zweryfikuj liczby **9 epok / 36 jednostek / 36 etapów / 108 gwiazdek** w końcowej wersji gry.
- Rozróżniaj **rankingi lokalne** (bez internetu) i **rankingi Google Play Games** (tylko po skonfigurowaniu ID i uwierzytelnieniu).
- W gałęzi bazowej games_ids.xml zawiera REPLACE_ME. **Nie publikuj opisu funkcji sieciowych jako dostępnych**, dopóki release nie ma poprawnych ID i testów Play Games; w przeciwnym razie usuń warunkowy akapit o PGS z publicznej karty.
- **Nie obiecuj osiągnięć Google Play Games** przed wdrożeniem adaptera: lokalne osiągnięcia działają, ale wysyłka do Google nie jest podłączona.
- Przypomnienia są **lokalne**, po pierwszym zwycięstwie i z uprawnieniem systemowym, a nie push.
- Nie reklamuj niewdrożonych reklam, zakupów, multiplayera ani własnych rankingów serwerowych. Sprawdź opis względem finalnego AAB i polityki prywatności.

**Warunek wydania:** W kodzie istnieje osobny, opcjonalny ranking globalny wydawcy: domyślnie wyłączony, wymagający zgody w Ustawieniach i działającego serwera HTTPS. Nie wymieniaj go jako dostępnej funkcji w publicznej karcie przed wdrożeniem i audytem prywatności.
