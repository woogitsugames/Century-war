# Glosariusz tłumaczeń — Century War

Źródłem obowiązujących nazw w grze jest `app/src/main/java/com/centurywar/engine/L.kt`. Poniższa tabela podaje **jedną kanoniczną nazwę danego pojęcia na język**; wszystkie nazwy zostały przepisane bezpośrednio z aktualnych kluczy tabeli. Tłumaczenia w kontekście zdania mogą wymagać odmiany gramatycznej, ale nie zmiany nazwy pojęcia.

Kolejność języków to `L.CODES`: **pl, en, de, es, fr, it, pt, uk, nl, tr**. Portugalski stosuje wariant europejski, a angielski — pisownię amerykańską (`color`, `armor`, `armory`). Długie nazwy trybów i opisy mogą się zawijać; krótkie etykiety przycisków są weryfikowane osobno przez `LTest.buttonLabelsFitWithin18Characters`.

## Zasady redakcyjne

- W kontekście osobowości AI stosuj nazwy ze `sk_person_*` i `bh_ai_*` bez zmiany sensu; w szczególności po angielsku **Siege Expert**, po niemiecku **Belagerer** (nigdy angielskie *Sieger*, czyli myląca nazwa).
- Dla niemieckiego trybu dziennego stosuj **Tageschallenge**; dla holenderskiego oblężenia tygodnia — **Wekelijks beleg**. Krótkie włoskie **Assedio sett.** jest celowym skrótem etykiety wyboru trybu.
- W tekstach francuskich zachowuj odstęp przed `:`, `!`, `?` i `;` oraz typograficzne apostrofy; inne języki powinny stosować właściwe dla siebie zasady interpunkcji i wielkich liter.
- Nazwy kart, uzbrojenia, zdolności i wariantów nie są zamiennikami. Na przykład **Rally** (aktywna zdolność) i **War Cry** (badanie odblokowujące zdolność) to różne obiekty.
- Zmieniaj tylko brzmienie, nie wartości liczbowe, placeholdery (`%d`, `%s`, `%%`) ani formy mnogie (`|`).

## Epoki

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Plemiona (`era_tribal`) | Plemiona | Tribal Age | Stammeszeit | Era tribal | Âge tribal | Età tribale | Era tribal | Доба племен | Stammentijd | Kabile Çağı |
| Epoka brązu (`era_bronze`) | Epoka brązu | Bronze Age | Bronzezeit | Edad del Bronce | Âge du bronze | Età del bronzo | Idade do Bronze | Бронзова доба | Bronstijd | Tunç Çağı |
| Epoka żelaza (`era_iron`) | Epoka żelaza | Iron Age | Eisenzeit | Edad del Hierro | Âge du fer | Età del ferro | Idade do Ferro | Залізна доба | IJzertijd | Demir Çağı |
| Feudalizm (`era_feudal`) | Feudalizm | Feudal Age | Feudalzeit | Era feudal | Âge féodal | Età feudale | Era feudal | Феодальна доба | Feodale tijd | Feodal Çağ |
| Epoka prochu (`era_powder`) | Epoka prochu | Gunpowder Age | Pulverzeit | Era de la pólvora | Âge de la poudre | Età della polvere | Era da pólvora | Доба пороху | Buskruittijd | Barut Çağı |
| Epoka pary (`era_steam`) | Epoka pary | Steam Age | Dampfzeit | Era del vapor | Âge de la vapeur | Età del vapore | Era do vapor | Доба пари | Stoomtijd | Buhar Çağı |
| Epoka stali (`era_steel`) | Epoka stali | Steel Age | Stahlzeit | Era del acero | Âge de l’acier | Età dell'acciaio | Era do aço | Доба сталі | Staaltijd | Çelik Çağı |
| Epoka krzemu (`era_silicon`) | Epoka krzemu | Silicon Age | Siliziumzeit | Era del silicio | Âge du silicium | Età del silicio | Era do silício | Доба кремнію | Siliciumtijd | Silisyum Çağı |
| Era orbitalna (`era_orbital`) | Era orbitalna | Orbital Era | Orbitalzeit | Era orbital | Ère orbitale | Era orbitale | Era orbital | Орбітальна ера | Orbitaal tijdperk | Yörünge Çağı |

## Tryby rozgrywki

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Kampania (`campaign`) | Kampania | Campaign | Kampagne | Campaña | Campagne | Campagna | Campanha | Кампанія | Campagne | Sefer |
| Oblężenie bez końca (`endless`) | Oblężenie bez końca | Endless Siege | Endlose Belagerung | Asedio sin fin | Siège sans fin | Assedio infinito | Cerco sem fim | Нескінченна облога | Eindeloos beleg | Sonsuz Kuşatma |
| Wyzwanie dnia (`daily`) | Wyzwanie dnia | Daily Challenge | Tageschallenge | Desafío diario | Défi du jour | Sfida del giorno | Desafio diário | Виклик дня | Daguitdaging | Günlük Mücadele |
| Oblężenie tygodnia (`weekly`) | Oblężenie tygodnia | Weekly Siege | Wochenbelagerung | Asedio semanal | Siège hebdomadaire | Assedio sett. | Cerco semanal | Тижнева облога | Wekelijks beleg | Haftalık Kuşatma |
| Potyczka (`skirmish`) | Potyczka | Skirmish | Gefecht | Escaramuza | Escarmouche | Schermaglia | Escaramuça | Сутичка | Schermutseling | Çatışma |

## Role i uzbrojenie

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Jednostki do walki wręcz (`cx_melee`) | Wręcz | Melee | Nahkampf | Cuerpo a cuerpo | Mêlée | Mischia | Corpo a corpo | Ближній бій | Nabijgevecht | Yakın dövüş |
| Jednostki dystansowe (`cx_ranged`) | Dystansowa | Ranged | Fernkampf | A distancia | Distance | A distanza | À distância | Дальній бій | Afstand | Menzilli |
| Jednostki ciężkie (`cx_heavy`) | Ciężka | Heavy | Schwer | Pesada | Lourde | Pesante | Pesada | Важкі | Zwaar | Ağır |
| Jednostki elitarne (`cx_elite`) | Elita | Elite | Elite | Élite | Élite | Élite | Elite | Еліта | Elite | Seçkin |
| Wieża (`cx_turret`) | Wieża | Turret | Turm | Torreta | Tourelle | Torretta | Torre | Вежа | Toren | Kule |
| Ostrzał (`cx_barrage`) | Ostrzał | Barrage | Sperrfeuer | Bombardeo | Barrage | Sbarramento | Bombardeio | Обстріл | Spervuur | Baraj ateşi |

## Osobowości przeciwnika (AI)

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| AI: szturm (`sk_person_0`) | Szturmowiec | Assault | Stürmer | Asalto | Assaut | Assalto | Assalto | Штурмовик | Aanval | Hücumcu |
| AI: inżynier (`sk_person_1`) | Inżynier | Engineer | Ingenieur | Ingeniero | Ingénieur | Ingegnere | Engenheiro | Інженер | Ingenieur | Mühendis |
| AI: ekonomia (`sk_person_2`) | Ekonomista | Economist | Ökonom | Economista | Économiste | Economista | Economista | Економіст | Econoom | Ekonomist |
| AI: oblężenie (`sk_person_3`) | Oblężnik | Siege Expert | Belagerer | Asediador | Assiégeant | Assediante | Sitiador | Облоговець | Belegeraar | Kuşatmacı |
| AI: równowaga (`sk_person_4`) | Zrównoważony | Balanced | Ausgewogen | Equilibrado | Équilibré | Equilibrato | Equilibrado | Збалансований | Evenwichtig | Dengeli |

## Warianty epok

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Spalinowa (`v_combustion`) | Spalinowa | Combustion | Verbrennung | Combustión | Combustion | Combustione | Combustão | Паливна | Verbranding | Yanmalı |
| Galwaniczna (`v_galvanic`) | Galwaniczna | Galvanic | Galvanisch | Galvánica | Galvanique | Galvanica | Galvânica | Гальванічна | Galvanisch | Galvanik |
| Rojowa (`v_swarm`) | Rojowa | Swarm | Schwarm | Enjambre | Essaim | Sciame | Enxame | Ройова | Zwerm | Sürü |
| Biotechniczna (`v_biotech`) | Biotechniczna | Biotechnical | Biotechnisch | Biotecnológica | Biotechnique | Biotecnologica | Biotecnológica | Біотехнічна | Biotechnisch | Biyoteknolojik |

## Pogoda

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Bezchmurnie (`w_clear`) | Bezchmurnie | Clear | Klar | Despejado | Ciel dégagé | Sereno | Céu limpo | Ясно | Helder | Açık |
| Deszcz (`w_rain`) | Deszcz | Rain | Regen | Lluvia | Pluie | Pioggia | Chuva | Дощ | Regen | Yağmur |
| Wiatr (`w_wind`) | Wiatr | Wind | Wind | Viento | Vent | Vento | Vento | Вітер | Wind | Rüzgâr |
| Noc (`w_night`) | Noc | Night | Nacht | Noche | Nuit | Notte | Noite | Ніч | Nacht | Gece |

## Zdolności specjalne

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Zryw (`rally`) | Zryw | Rally | Ansturm | Ímpetu | Élan | Slancio | Ímpeto | Ривок | Stormloop | Atılım |
| Lawina głazów (`ab_rocks`) | Lawina głazów | Rockslide | Steinlawine | Alud de rocas | Éboulement | Frana | Deslizamento | Каменепад | Steenlawine | Kaya Yağmuru |
| Grad oszczepów (`ab_spears`) | Grad oszczepów | Spear Hail | Speerhagel | Lluvia de lanzas | Grêle de lances | Pioggia di lance | Chuva de lanças | Град списів | Speerregen | Mızrak Yağmuru |
| Salwa oszczepów (`ab_iron_pilum`) | Salwa oszczepów | Javelin Volley | Speersalve | Salva de jabalinas | Salve de javelots | Salva di giavellotti | Salva de dardos | Залп списів | Speerensalvo | Mızrak Yaylımı |
| Deszcz strzał (`ab_arrows`) | Deszcz strzał | Arrow Rain | Pfeilregen | Lluvia de flechas | Pluie de flèches | Pioggia di frecce | Chuva de flechas | Дощ стріл | Pijlenregen | Ok Yağmuru |
| Salwa burtowa (`ab_cannons`) | Salwa burtowa | Broadside | Breitseite | Andanada | Bordée | Bordata | Salva de canhões | Бортовий залп | Volle laag | Top Yaylımı |
| Nawała artylerii (`ab_shells`) | Nawała artylerii | Artillery Storm | Trommelfeuer | Tormenta artillera | Pilonnage | Martellamento | Barragem de artilharia | Артилерійський шквал | Granaatregen | Topçu Fırtınası |
| Nalot (`ab_bombers`) | Nalot | Air Raid | Luftangriff | Ataque aéreo | Raid aérien | Raid aereo | Ataque aéreo | Авіаналіт | Luchtaanval | Hava Saldırısı |
| Uderzenie rakiet (`ab_missiles`) | Uderzenie rakiet | Missile Strike | Raketenschlag | Ataque de misiles | Frappe de missiles | Attacco missilistico | Ataque de mísseis | Ракетний удар | Raketaanval | Füze Saldırısı |
| Lanca orbitalna (`ab_orbital`) | Lanca orbitalna | Orbital Lance | Orbitallanze | Lanza orbital | Lance orbitale | Lancia orbitale | Lança orbital | Орбітальний спис | Orbitale lans | Yörünge Mızrağı |
| Ostrzał spalinowy (`ab_combustion`) | Ostrzał spalinowy | Combustion barrage | Verbrennungs-Sperrfeuer | Bombardeo de combustión | Barrage à combustion | Sbarramento a combustione | Barragem de combustão | Паливний обстріл | Verbrandingsspervuur | Yanmalı bombardıman |
| Ostrzał galwaniczny (`ab_galvanic`) | Ostrzał galwaniczny | Galvanic barrage | Galvanischer Beschuss | Bombardeo galvánico | Barrage galvanique | Sbarramento galvanico | Barragem galvânica | Гальванічний обстріл | Galvanisch spervuur | Galvanik bombardıman |
| Ostrzał roju (`ab_swarm`) | Ostrzał roju | Swarm barrage | Schwarmbeschuss | Bombardeo del enjambre | Barrage de l’essaim | Sbarramento dello sciame | Barragem do enxame | Ройовий обстріл | Zwermspervuur | Sürü bombardımanı |
| Ostrzał biotechniczny (`ab_biotech`) | Ostrzał biotechniczny | Biotech barrage | Biotechnischer Beschuss | Bombardeo biotécnico | Barrage biotechnique | Sbarramento biotecnico | Barragem biotécnica | Біотехнічний обстріл | Biotechnisch spervuur | Biyoteknik bombardıman |

## Doktryny

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Gospodarka (`d_economy`) | Gospodarka | Economy | Wirtschaft | Economía | Économie | Economia | Economia | Економіка | Economie | Ekonomi |
| Oblężenie (`d_siege`) | Oblężenie | Siege | Belagerung | Asedio | Siège | Assedio | Cerco | Облога | Beleg | Kuşatma |
| Bastion (`d_bulwark`) | Bastion | Bulwark | Bollwerk | Baluarte | Rempart | Baluardo | Baluarte | Бастіон | Bolwerk | Kale |
| Mobilność (`d_swift`) | Mobilność | Swiftness | Schnelligkeit | Rapidez | Célérité | Rapidità | Agilidade | Мобільність | Snelheid | Çeviklik |
| Weterani (`d_veteran`) | Weterani | Veterans | Veteranen | Veteranos | Vétérans | Veterani | Veteranos | Ветерани | Veteranen | Gaziler |
| Furia (`d_fury`) | Furia | Fury | Raserei | Furia | Fureur | Furia | Fúria | Лють | Woede | Öfke |
| Arsenał (`d_arsenal`) | Arsenał | Arsenal | Arsenal | Arsenal | Arsenal | Arsenale | Artilharia | Арсенал | Arsenaal | Tophane |
| Taktyka (`d_tactics`) | Taktyka | Tactics | Taktik | Táctica | Tactique | Tattica | Tática | Тактика | Tactiek | Taktik |
| Grabież (`d_plunder`) | Grabież | Plunder | Plünderung | Saqueo | Pillage | Saccheggio | Saque | Грабіж | Plundering | Yağma |

## Modyfikatory bitwy

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Podwójny przychód (`m_income`) | Podwójny przychód | Double income | Doppeltes Einkommen | Ingresos dobles | Revenus doublés | Entrate doppie | Renda dobrada | Подвійний дохід | Dubbel inkomen | Çifte Gelir |
| Kruche bazy (`m_fragile`) | Kruche bazy | Fragile bases | Zerbrechliche Basen | Bases frágiles | Bases fragiles | Basi fragili | Bases frágeis | Крихкі бази | Fragiele bases | Kırılgan Üsler |
| Szybkie wojska (`m_fast`) | Szybkie wojska | Fast troops | Schnelle Truppen | Tropas rápidas | Troupes rapides | Truppe veloci | Tropas rápidas | Швидкі війська | Snelle troepen | Hızlı Birlikler |
| Bez wież (`m_noturrets`) | Bez wież | No turrets | Keine Türme | Sin torretas | Sans tourelles | Niente torrette | Sem torres | Без веж | Geen torens | Kulesiz |
| Horda strzelców (`m_ranged`) | Horda strzelców | Ranged horde | Schützenhorde | Horda de tiradores | Horde de tireurs | Orda di tiratori | Horda de atiradores | Орда стрільців | Schuttershorde | Nişancı Sürüsü |
| Szklane działa (`m_glass`) | Szklane działa | Glass cannons | Glaskanonen | Cañones de cristal | Canons de verre | Cannoni di vetro | Canhões de vidro | Скляні гармати | Glazen kanonnen | Cam Toplar |
| Bogaty start (`m_rich`) | Bogaty start | Rich start | Reicher Start | Inicio rico | Départ fortuné | Partenza ricca | Início rico | Багатий старт | Rijke start | Zengin Başlangıç |
| Wolny rozwój (`m_slowxp`) | Wolny rozwój | Slow progress | Langsamer Fortschritt | Progreso lento | Progression lente | Progresso lento | Progresso lento | Повільний розвиток | Trage voortgang | Yavaş Gelişim |
| Opancerzony wróg (`m_armored`) | Opancerzony wróg | Armored foe | Gepanzerter Feind | Enemigo blindado | Ennemi blindé | Nemico corazzato | Inimigo blindado | Броньований ворог | Gepantserde vijand | Zırhlı Düşman |
| Mgła wojny (`m_fog`) | Mgła wojny | Fog of war | Nebel des Krieges | Niebla de guerra | Brouillard de guerre | Nebbia di guerra | Névoa de guerra | Туман війни | Oorlogsmist | Savaş Sisi |

## Zasoby i postęp

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Złoto (`gold`) | Złoto | Gold | Gold | Oro | Or | Oro | Ouro | Золото | Goud | Altın |
| Doświadczenie (`xp`) | XP | XP | XP | XP | XP | XP | XP | XP | XP | XP |
| Medale (`medals`) | Medale | Medals | Medaillen | Medallas | Médailles | Medaglie | Medalhas | Медалі | Medailles | Madalyalar |
| Gwiazdki (`stars`) | Gwiazdki | Stars | Sterne | Estrellas | Étoiles | Stelle | Estrelas | Зірки | Sterren | Yıldızlar |

## Rzadkość kart

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Zwykła (`er_common`) | Zwykła | Common | Gewöhnlich | Común | Commune | Comune | Comum | Звичайна | Gewoon | Yaygın |
| Niepospolita (`er_uncommon`) | Niepospolita | Uncommon | Ungewöhnlich | Poco común | Peu commune | Non comune | Incomum | Незвичайна | Ongewoon | Sıradışı |
| Rzadka (`er_rare`) | Rzadka | Rare | Selten | Rara | Rare | Rara | Rara | Рідкісна | Zeldzaam | Nadir |
| Legendarna (`er_legendary`) | Legendarna | Legendary | Legendär | Legendaria | Légendaire | Leggendaria | Lendária | Легендарна | Legendarisch | Efsanevi |

## Rankingi

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Rankingi (`rankings`) | Rankingi | Rankings | Ranglisten | Clasificación | Classements | Classifiche | Classificações | Рейтинги | Ranglijsten | Sıralamalar |
| Ranking globalny (`global_ranking`) | Ranking globalny | Global ranking | Weltrangliste | Clasificación mundial | Classement mondial | Classifica globale | Ranking mundial | Світовий рейтинг | Wereldranglijst | Küresel sıralama |
| Oblężenie — fale (`board_endless`) | Oblężenie — fale | Siege — waves | Belagerung — Wellen | Asedio — oleadas | Siège — vagues | Assedio — ondate | Cerco — ondas | Облога — хвилі | Beleg — golven | Kuşatma — dalgalar |
| Wyzwanie dnia (dziś) (`board_daily`) | Wyzwanie dnia (dziś) | Daily challenge (today) | Tageschallenge (heute) | Desafío diario (hoy) | Défi du jour (aujourd’hui) | Sfida del giorno (oggi) | Desafio diário (hoje) | Виклик дня (сьогодні) | Daguitdaging (vandaag) | Günlük Mücadele (bugün) |
| Kampania — gwiazdki (`board_campaign`) | Kampania — gwiazdki | Campaign — stars | Kampagne — Sterne | Campaña — estrellas | Campagne — étoiles | Campagna — stelle | Campanha — estrelas | Кампанія — зірки | Campagne — sterren | Sefer — yıldızlar |
| Potyczka — wynik (`board_skirmish`) | Potyczka — wynik | Skirmish — score | Gefecht — Punkte | Escaramuza — puntos | Escarmouche — score | Schermaglia — punteggio | Escaramuça — pontos | Сутичка — очки | Schermutseling — score | Çatışma — puan |
| Tydzień (`board_weekly`) | Tydzień | Weekly | Woche | Semana | Semaine | Settimana | Semana | Тиждень | Week | Haftalık |

## Dymki, samouczek i podpowiedzi

Dymek (HintOverlay) jest krótką wskazówką kontekstową. Opis po przytrzymaniu przycisku (tip53_*) to osobny element: nie należy traktować ich jako tego samego komunikatu ani mieszać terminów z nazwami zdolności.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Wskazówka (hint_title) | Wskazówka | Hint | Hinweis | Consejo | Astuce | Suggerimento | Dica | Підказка | Tip | İpucu |
| Przycisk potwierdzenia wskazówki (hint_ok) | OK | OK | OK | Vale | OK | OK | OK | Гаразд | OK | Tamam |
| Ostrzał — nagłówek opisu (tip53_barrage_title) | Ostrzał | Barrage | Sperrfeuer | Bombardeo | Barrage | Sbarramento | Bombardeio | Обстріл | Spervuur | Baraj ateşi |
| Kolejka — nagłówek opisu (tip53_queue_title) | Kolejka | Queue | Warteschlange | Cola | File d'attente | Coda | Fila | Черга | Wachtrij | Sıra |
| Zbrojownia w samouczku (tut_armory54_t) | Zbrojownia | Armory | Waffenkammer | Armería | Armurerie | Armeria | Arsenal | Зброярня | Wapenkamer | Cephanelik |
| Zryw w samouczku (tut_rally54_t) | Zryw wojsk | Troop rally | Ansturm | Impulso de tropas | Élan des troupes | Slancio delle truppe | Ímpeto das tropas | Ривок війська | Troepenstoot | Atılım |

## Zapowiedzi fal i bossów

Zapowiedzi ew_* są nazwami konkretnych fal, a boss_* — nazwami typów bossów. W opisach osiągnięć ach_boss_*_d stosuj przetłumaczoną nazwę trybu z klucza endless zamiast nieprzetłumaczonego *Endless* (wyjątek: angielski).

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Nadciąga boss (ew_boss) | Nadciąga boss! | Boss incoming! | Boss im Anmarsch! | ¡Se acerca un jefe! | Boss en approche ! | Boss in arrivo! | Chefe a caminho! | Бос наближається! | Baas op komst! | Boss geliyor! |
| Tabor oblężniczy (ew_siege_train) | Tabor oblężniczy | Siege train | Belagerungstross | Tren de asedio | Convoi de siège | Convoglio d'assedio | Comboio de cerco | Облоговий обоз | Belegeringstrein | Kuşatma konvoyu |
| Harcownicy (ew_skirmishers) | Harcownicy | Skirmishers | Plänkler | Hostigadores | Tirailleurs | Schermagliatori | Escaramuçadores | Застрільники | Schermutselaars | Akıncılar |
| Boss: odrzut (boss_knockback) | BOSS — ODRZUT | BOSS — KNOCKBACK | BOSS — RÜCKSTOSS | JEFE — EMPUJE | BOSS — REPOUSSEMENT | BOSS — RESPINTA | CHEFE — REPULSÃO | БОС — ВІДКИДАННЯ | BAAS — TERUGSLAG | BOSS — GERİ İTME |
| Boss: aura tempa (boss_aura) | BOSS — AURA SZYBKOŚCI | BOSS — SPEED AURA | BOSS — TEMPOAURA | JEFE — AURA DE VELOCIDAD | BOSS — AURA DE VITESSE | BOSS — AURA DI VELOCITÀ | CHEFE — AURA DE VELOCIDADE | БОС — АУРА ШВИДКОСТІ | BAAS — SNELHEIDSAURA | BOSS — HIZ AURASI |

## Karty, kodeks i doktryny — nowe etykiety

Karta oblężenia (ec_*) nie jest doktryną (d_*): nie należy ich zamieniać. Kategoria zdolności i Zbrojowni (cx62_abilities) dotyczy **Zbrojowni**, a nie ogólnie broni. Skróty w zakładkach zachowują limit 18 znaków; pełne nazwy są w opisach i w istniejącej tabeli doktryn.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Kategorie: zdolności i Zbrojownia (cx62_abilities) | Zdoln. i zbroj. | Skills & Armory | Fähigk./Waffenk. | Habil. y armería | Pouvoirs/armurerie | Abilità/armeria | Poderes/arsenal | Уміння/зброярня | Vaardigh./Wapenk. | Yetenek/Cephanelik |
| Kategorie: doktryny (cx62_doctrines) | Doktryny | Doctrines | Doktrinen | Doctrinas | Doctrines | Dottrine | Doutrinas | Доктрини | Doctrines | Doktrinler |
| Kategorie: karty oblężenia (cx62_cards) | Karty oblężenia | Endless cards | Belagerungskarten | Cartas sin fin | Cartes sans fin | Carte senza fine | Cartas sem fim | Карти облоги | Belegkaarten | Kuşatma kartları |
| Kategorie: modyfikatory (cx62_modifiers) | Modyfikatory | Modifiers | Modifikatoren | Modificadores | Modificateurs | Modificatori | Modificadores | Модифікатори | Modificatoren | Değiştiriciler |
| Karta: dodatkowy slot wieży (ec_tower_slot) | Dodatkowa wieża | Extra turret slot | Zusätzlicher Turmplatz | Espacio para torreta | Emplacement tourelle | Slot torretta extra | Espaço de torre | Додатковий слот вежі | Extra torenslot | Ek kule yuvası |
| Karta: elita bez kosztu (ec_free_elite) | Elitarne posiłki | Free elite | Gratis Elite | Élite gratis | Élite gratuite | Elite gratuita | Elite grátis | Безкоштовна еліта | Gratis elite | Ücretsiz elit |
| Karta: automatyczny ostrzał (ec_auto_barrage) | Samoczynny ostrzał | Auto barrage | Automatisches Sperrfeuer | Bombardeo automático | Barrage automatique | Fuoco automatico | Bombardeio automático | Автоматичний обстріл | Automatisch spervuur | Otomatik bombardıman |

## Raport błędu i moderacja

Raport techniczny awarii nie jest zgłoszeniem gracza do moderacji: report_* oznacza raport zapisany lokalnie po awarii i świadomie udostępniany przez użytkownika. Przycisk zgłoszenia musi mieć najwyżej 18 znaków, stąd krótsze brzmienia niż pełne terminy w zdaniach.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Zgłoś problem (report_problem) | Zgłoś problem | Report a problem | Problem melden | Avisar de un fallo | Signaler un bug | Segnala un errore | Comunicar um erro | Повідомити про баг | Probleem melden | Sorun bildir |
| Wykryto awarię (report_prompt_title) | Wykryto awarię | A crash was detected | Absturz erkannt | Se detectó un fallo | Un plantage a été détecté | Rilevato un arresto anomalo | Falha detetada | Виявлено збій | Crash gedetecteerd | Çökme algılandı |
| Udostępnij raport (report_share_title) | Udostępnij raport | Share report | Bericht teilen | Compartir informe | Partager le rapport | Condividi rapporto | Partilhar relatório | Поділитися звітом | Rapport delen | Raporu paylaş |
| Udostępnij (report_send) | Udostępnij | Share | Teilen | Compartir | Partager | Condividi | Partilhar | Поділитися | Delen | Paylaş |
| Nie teraz (report_cancel) | Nie teraz | Not now | Nicht jetzt | Ahora no | Pas maintenant | Non ora | Agora não | Не зараз | Niet nu | Şimdi değil |

**Moderacja (termin redakcyjny, bez istniejącego klucza UI):**

| PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|
| Moderacja | Moderation | Moderation | Moderación | Modération | Moderazione | Moderação | Модерація | Moderatie | Moderasyon |

Nie dodawaj tego terminu do ekranu ani tabeli L.kt bez osobnego zlecenia i ustalenia rzeczywistego mechanizmu moderacji.

## Kontrola form liczby mnogiej w misjach 61

Dla kluczy ms_weather_wins, ms_custom_skirmish, ms_endless_bosses, ms_unit_upgrades, ms_replays_watched i ms_endless_cards obowiązują trzy warianty po polsku i ukraińsku, dwa w pozostałych językach poza tureckim (jeden). Kontroluj formy dla **1, 2, 5, 21, 22, 25**, a także zachowanie separatora pionowego. Klucz ms_weekly_waves podaje numer fali, a nie liczbę rzeczownika, więc nie wymaga wariantów.

W języku tureckim znak procentu jest **przed** liczbą (np. +%8); w sformatowanych tekstach cx62_* odpowiada temu składnia +%%%d lub −%%%d. Nie należy automatycznie jej zamieniać na format innych języków.

## Reguły utrzymania

Przy każdej zmianie nazwy porównuj `L.kt` z glosariuszem, aktualizuj równocześnie powiązane ekrany (`bh_ai_*`, `sk_person_*`, `cx_*`) i uruchom testy `LTest`. Klucze pozostają bez zmian, a liczba i kolejność dziesięciu tłumaczeń są obowiązkowe.
