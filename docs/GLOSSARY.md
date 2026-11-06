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
| Doświadczenie (`xp`) | XP | XP | XP | XP | XP | XP | XP | ОД | XP | XP |
| Punkty zdrowia (`cx_hp`) | HP | HP | LP | PV | PV | PS | PV | ОЗ | HP | Can |
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

## Terminologia i formatowanie — audyt 88

- **EN:** budowle obronne to *turrets* (liczba pojedyncza *turret*) we wszystkich opisach, w tym w nazwach kart i wież epokowych. *Advance* oznacza postawę armii (`advance`); przejście do następnej epoki to *Evolve* (`evolve`, `tip53_evolve`, `ms_evolutions`). W nazwach własnych epok obowiązuje *Age*: **Steam Age**, **Silicon Age**.
- **DE:** przycisk ekranowy to *Schaltfläche*, nie fizyczna *Taste*. Oddzielaj liczbę i znak procentu spacją: **+30 %**, **−10 %**; w łańcuchach `String.format` zapis `+%d %%` wyświetla `+7 %`.
- **UK:** **ОД** to *очки досвіду* (doświadczenie, klucz `xp`), a **ОЗ** to *очки здоров’я* (punkty zdrowia, `cx_hp`). Nie są to zamienne zasoby. Nazwy epok to **Доба пари**, **Доба кремнію**; w zdaniach stosuj ich poprawną odmianę (*Доби пари*, *Доби кремнію*).
- **NL:** nazwy epok to **Stoomtijd** i **Siliciumtijd**, również w opisach osiągnięć.
- **TR:** przycisk zmiany epoki to **Çağ atla**; postawa ofensywna to **İlerle**. Nie stosuj w tym znaczeniu **Evrim**. Turecki procent pozostaje przed liczbą, zgodnie z powyższymi regułami.
- **Seria logowań:** w `ms_login_paid` nie stawiaj drugiego dwukropka przed nagrodą; `ms_login_day` podaje numer dnia osobno. Zachowuj `%s` dla liczby medali.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Awans epoki (`evolve`) | Awans | Evolve | Aufstieg | Evolucionar | Évoluer | Evolvi | Evoluir | Розвиток | Evolueren | Çağ atla |
| Postawa ofensywna (`advance`) | Naprzód | Advance | Vorrücken | Avanzar | Avancer | Avanza | Avançar | Уперед | Oprukken | İlerle |
| Seria dni (`ms_streak`) | Seria dni | Daily streak | Tages-Serie | Racha diaria | Série quotidienne | Serie giornaliera | Sequência diária | Щоденна серія | Dagreeks | Günlük seri |
| Nagroda dnia (`ms_login_paid`) | Dziś +%s | Today +%s | Heute +%s | Hoy +%s | Aujourd’hui +%s | Oggi +%s | Hoje +%s | Сьогодні +%s | Vandaag +%s | Bugün +%s |

## Uzupełnienie nazw z aktualnej wersji

Każdy wiersz podaje dokładny tekst z `L.kt`; rozróżnienie znaczeń wynika z nagłówka i klucza. Nazwy o podobnym brzmieniu w różnych mechanikach nie muszą być tym samym pojęciem.

### Doktryny

Doktryna to wybierany wariant premii w walce.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Sztandar (`d_banner`) | Sztandar | War Banner | Kampfbanner | Estandarte | Bannière | Stendardo | Estandarte | Бойовий стяг | Strijdvaandel | Savaş Sancağı |
| Zaopatrzenie (`d_supply`) | Zaopatrzenie | Supply Lines | Nachschub | Suministros | Ravitaillement | Rifornimenti | Abastecimento | Постачання | Bevoorrading | İkmal |
| Mistrzowie oblężeń (`d_siege_masters`) | Mistrzowie oblężeń | Siege Masters | Belagerungsmeister | Maestros del asedio | Maîtres de siège | Maestri d'assedio | Mestres do cerco | Майстри облоги | Belegeringsmeesters | Kuşatma Ustaları |
| Czujność (`d_vigilance`) | Czujność | Vigilance | Wachsamkeit | Vigilancia | Vigilance | Vigilanza | Vigilância | Пильність | Waakzaamheid | Teyakkuz |

### Karty Endless

Karta to wybierana premia dostępna w trybie Endless.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Szyk pik (`ec_pike_square`) | Szyk pik | Pike square | Pikenkarree | Cuadro de picas | Carré de piques | Quadrato di picche | Quadrado de piques | Стрій пік | Piekencarré | Mızrak karesi |
| Straż przednia (`ec_vanguard`) | Straż przednia | Vanguard | Vorhut | Vanguardia | Avant-garde | Avanguardia | Vanguarda | Авангард | Voorhoede | Öncü birlik |
| Nagroda za głowę (`ec_boss_bounty`) | Nagroda za głowę | Bounty | Kopfgeld | Recompensa | Prime | Taglia | Recompensa | Нагорода за голову | Premie | Ödül avı |
| Rogi wojenne (`ec_war_horns`) | Rogi wojenne | War horns | Kriegshörner | Cuernos de guerra | Cors de guerre | Corni di guerra | Trompas de guerra | Бойові роги | Oorlogshoorns | Savaş boruları |
| Saperzy (`ec_sappers`) | Saperzy | Sappers | Pioniere | Zapadores | Sapeurs | Guastatori | Sapadores | Сапери | Sappeurs | İstihkamcılar |
| Mur tarcz (`ec_shield_wall`) | Mur tarcz | Shield wall | Schildwall | Muro de escudos | Mur de boucliers | Muro di scudi | Muralha de escudos | Стіна щитів | Schildmuur | Kalkan duvarı |
| Skarbiec wojenny (`ec_war_chest`) | Skarbiec wojenny | War chest | Kriegskasse | Cofre de guerra | Trésor de guerre | Forziere di guerra | Cofre de guerra | Воєнна скарбниця | Oorlogskas | Savaş hazinesi |
| Zakład (`ec_wager`) | Zakład | Wager | Wette | Apuesta | Pari | Scommessa | Aposta | Парі | Weddenschap | İddia |
| Kwatermistrz (`ec_quartermaster`) | Kwatermistrz | Quartermaster | Quartiermeister | Intendente | Intendant | Quartiermastro | Intendente | Інтендант | Kwartiermeester | Levazımcı |
| Mistrzowie murarscy (`ec_master_masons`) | Mistrzowie murarscy | Master masons | Baumeister | Maestros canteros | Maîtres maçons | Mastri muratori | Mestres pedreiros | Майстри-мулярі | Meester-metselaars | Usta duvarcılar |
| Płonące wieże (`ec_burning_towers`) | Płonące wieże | Burning turrets | Brennende Türme | Torretas incendiarias | Tourelles incendiaires | Torrette incendiarie | Torres incendiárias | Палаючі вежі | Brandende torens | Yakan kuleler |
| Krew za złoto (`ec_blood_gold`) | Krew za złoto | Blood for gold | Blut für Gold | Sangre por oro | Du sang pour de l’or | Sangue per oro | Sangue por ouro | Кров за золото | Bloed voor goud | Kana karşılık altın |
| Klonowanie (`ec_cloning`) | Klonowanie | Cloning | Klonen | Clonación | Clonage | Clonazione | Clonagem | Клонування | Klonen | Klonlama |
| Pancerz piechoty (`ec_frontline_plate`) | Pancerz piechoty | Frontline armor | Frontpanzerung | Blindaje frontal | Blindage du front | Armatura del fronte | Blindagem frontal | Броня передової | Frontpantser | Ön cephe zırhı |
| Instruktor (`ec_drill_sergeant`) | Instruktor | Drill sergeant | Ausbilder | Instructor | Instructeur | Istruttore | Instrutor | Інструктор | Instructeur | Eğitmen |
| Szybkie wieże (`ec_rapid_towers`) | Szybkie wieże | Rapid turrets | Schnelle Türme | Torretas rápidas | Tourelles rapides | Torrette rapide | Torres rápidas | Швидкі вежі | Snelle torens | Hızlı kuleler |
| Medycy (`ec_medic`) | Medycy | Medics | Sanitäter | Médicos | Médecins | Medici | Médicos | Медики | Hulpverleners | Sağlıkçılar |
| Skarbiec (`ec_extra_gold`) | Skarbiec | War chest | Kriegskasse | Tesoro de guerra | Trésor de guerre | Forziere di guerra | Baú de guerra | Військова скарбниця | Oorlogskas | Savaş hazinesi |
| Mobilizacja (`ec_mobilization`) | Mobilizacja | Mobilization | Mobilmachung | Movilización | Mobilisation | Mobilitazione | Mobilização | Мобілізація | Mobilisatie | Seferberlik |
| Forteca (`ec_fortress`) | Forteca | Fortress | Festung | Fortaleza | Forteresse | Fortezza | Fortaleza | Фортеця | Vesting | Kale |
| Strzelcy wyborowi (`ec_sharpshooters`) | Strzelcy wyborowi | Sharpshooters | Scharfschützen | Tiradores de élite | Tireurs d’élite | Tiratori scelti | Atiradores de elite | Влучні стрільці | Scherpschutters | Keskin nişancılar |
| Szał bojowy (`ec_berserk`) | Szał bojowy | Berserk | Kampfrausch | Furia de combate | Furie au combat | Furia bellica | Fúria de combate | Бойова лють | Strijdwoede | Savaş çılgınlığı |
| Zbieracze (`ec_scavenger`) | Zbieracze | Scavengers | Plünderer | Recolectores | Récupérateurs | Recuperatori | Catadores | Збирачі | Verzamelaars | Toplayıcılar |
| Marsz bojowy (`ec_march`) | Marsz bojowy | Forced march | Eilmarsch | Marcha forzada | Marche forcée | Marcia forzata | Marcha forçada | Форсований марш | Geforceerde mars | Hızlı yürüyüş |
| Mistrz zrywu (`ec_rally_master`) | Mistrz zrywu | Rally master | Ansturmmeister | Maestro del impulso | Maître de l’élan | Maestro dello slancio | Mestre do ímpeto | Майстер ривка | Stormloopmeester | Atılım ustası |
| Akademia elit (`ec_elite_school`) | Akademia elit | Elite academy | Eliteakademie | Academia de élite | Académie d’élite | Accademia d'élite | Academia de elite | Академія еліти | Eliteacademie | Elit akademisi |
| Ostatnia linia (`ec_last_stand`) | Ostatnia linia | Last stand | Letztes Gefecht | Última defensa | Dernière ligne | Ultima difesa | Última resistência | Останній рубіж | Laatste linie | Son savunma |
| Ekipy naprawcze (`ec_repair_crew`) | Ekipy naprawcze | Repair crews | Reparaturteams | Equipos de reparación | Équipes de réparation | Squadre di riparazione | Equipas de reparação | Ремонтні бригади | Reparatieteams | Onarım ekipleri |
| Szlaki zaopatrzenia (`ec_supply_line`) | Szlaki zaopatrzenia | Supply routes | Nachschublinien | Rutas de suministro | Routes de ravitaillement | Vie di rifornimento | Rotas de abastecimento | Шляхи постачання | Aanvoerlijnen | İkmal yolları |
| Bębny natarcia (`ec_war_drums`) | Bębny natarcia | War drums | Kriegstrommeln | Tambores de guerra | Tambours de guerre | Tamburi di guerra | Tambores de guerra | Бойові барабани | Oorlogstrommels | Savaş davulları |
| Żelazna kurtyna (`ec_iron_curtain`) | Żelazna kurtyna | Iron curtain | Eiserner Vorhang | Cortina de hierro | Rideau de fer | Cortina di ferro | Cortina de ferro | Залізна завіса | IJzeren gordijn | Demir perde |
| Sieć bastionów (`ec_tower_network`) | Sieć bastionów | Turret network | Turmverbund | Red de torres | Réseau de tours | Rete di torri | Rede de torres | Мережа веж | Torenverbond | Kule ağı |
| Sztandar zaciągu (`ec_muster_banner`) | Sztandar zaciągu | Muster banner | Musterbanner | Estandarte de leva | Bannière de levée | Stendardo di leva | Estandarte de recrutamento | Стяг набору | Wervingsbanier | Seferberlik sancağı |
| Ostatni bastion (`ec_last_bastion`) | Ostatni bastion | Final bastion | Letztes Bollwerk | Último bastión | Dernier bastion | Ultimo bastione | Último bastião | Останній бастіон | Laatste bolwerk | Son burç |
| Żar zbrojowni (`ec_ember_forge`) | Żar zbrojowni | Ember forge | Glut-Schmiede | Forja ardiente | Forge ardente | Forgia ardente | Forja incandescente | Жар кузні | Gloeiende smidse | Kor demirhanesi |
| Serce cytadeli (`ec_citadel_heart`) | Serce cytadeli | Citadel heart | Herz der Zitadelle | Corazón de la ciudadela | Cœur de la citadelle | Cuore della cittadella | Coração da cidadela | Серце цитаделі | Hart van de citadel | Hisarın kalbi |
| Medycy polowi (`ec_field_medics`) | Medycy polowi | Field medics | Feldsanitäter | Médicos de campaña | Médecins de campagne | Medici da campo | Médicos de campanha | Польові медики | Veldmedici | Saha sıhhiyecileri |
| Ostatnia iskra (`ec_final_spark`) | Ostatnia iskra | Final spark | Letzter Funke | Última chispa | Dernière étincelle | Ultima scintilla | Última faísca | Остання іскра | Laatste vonk | Son kıvılcım |
| Osłona pierwszego ciosu (`ec_first_guard`) | Osłona pierwszego ciosu | First-hit guard | Erstschlagschutz | Guardia inicial | Protection initiale | Guardia iniziale | Guarda inicial | Захист першого удару | Bescherming bij volle HP | İlk darbe kalkanı |
| Odnowa po bossie (`ec_boss_recall`) | Odnowa po bossie | Boss recharge | Aufladung nach Bosskampf | Recarga tras el jefe | Recharge après le boss | Ricarica dopo il boss | Recarga após o chefe | Відновлення після боса | Herladen na baas | Boss sonrası yenileme |

### Wydarzenia dnia

Wydarzenie dnia określa warunki dziennego wyzwania.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Dzień strzelców (`day_marksmen`) | Dzień strzelców | Marksmen Day | Tag der Schützen | Día de tiradores | Jour des tireurs | Giorno dei tiratori | Dia dos atiradores | День стрільців | Schuttersdag | Nişancı Günü |
| Dzień szybkich zdolności (`day_rapid`) | Dzień szybkich zdolności | Rapid Abilities Day | Tag der schnellen Kräfte | Día de habilidades rápidas | Jour des pouvoirs rapides | Giorno delle abilità rapide | Dia das habilidades rápidas | День швидких здібностей | Dag van snelle krachten | Hızlı Yetenekler Günü |
| Dzień nawały (`day_barrage`) | Dzień nawały | Barrage Day | Tag des Sperrfeuers | Día del bombardeo | Jour du barrage | Giorno dello sbarramento | Dia do bombardeamento | День обстрілу | Dag van het spervuur | Baraj Ateşi Günü |
| Dzień twierdz (`day_fortress`) | Dzień twierdz | Fortress Day | Tag der Festungen | Día de fortalezas | Jour des forteresses | Giorno delle fortezze | Dia das fortalezas | День фортець | Vestingdag | Kale Günü |
| Dzień złotego startu (`day_gold`) | Dzień złotego startu | Golden Start Day | Tag des Goldstarts | Día del inicio dorado | Jour du départ doré | Giorno della partenza d'oro | Dia do início dourado | День золотого старту | Dag van de gouden start | Altın Başlangıç Günü |
| Dzień lustrzanych armii (`day_mirror`) | Dzień lustrzanych armii | Mirror Armies Day | Tag der Spiegelarmeen | Día de ejércitos espejo | Jour des armées miroirs | Giorno degli eserciti specchio | Dia dos exércitos espelho | День дзеркальних армій | Dag van spiegellegers | Ayna Ordular Günü |
| Dzień inżynierów (`day_siege_works`) | Dzień inżynierów | Engineers' Day | Tag der Ingenieure | Día de ingenieros | Jour des ingénieurs | Giorno degli ingegneri | Dia dos engenheiros | День інженерів | Ingenieursdag | Mühendis Günü |
| Forsowny marsz (`day_forced_march`) | Forsowny marsz | Forced March | Gewaltmarsch | Marcha forzada | Marche forcée | Marcia forzata | Marcha forçada | Форсований марш | Geforceerde mars | Hızlı Yürüyüş |
| Łupy wojenne (`day_spoils`) | Łupy wojenne | Spoils of War | Kriegsbeute | Botín de guerra | Butin de guerre | Bottino di guerra | Espólios de guerra | Воєнна здобич | Oorlogsbuit | Savaş Ganimeti |
| Dzień ciężkiej piechoty (`day184_heavy`) | Dzień ciężkiej piechoty | Heavy Infantry Day | Tag der schweren Infanterie | Día de infantería pesada | Jour de l'infanterie lourde | Giorno della fanteria pesante | Dia da infantaria pesada | День важкої піхоти | Dag van de zware infanterie | Ağır Piyade Günü |
| Pojedynek wież (`day184_towers`) | Pojedynek wież | Turret Duel | Turmduell | Duelo de torres | Duel de tours | Duello di torri | Duelo de torres | Дуель веж | Torenduel | Kule Düellosu |
| Dzień nauki (`day206_lore`) | Dzień nauki | Day of Learning | Tag des Lernens | Día del aprendizaje | Jour de l'apprentissage | Giorno dell'apprendimento | Dia da aprendizagem | День навчання | Dag van het leren | Öğrenme Günü |
| Dzień furii (`day206_fury`) | Dzień furii | Day of Fury | Tag des Zorns | Día de furia | Jour de furie | Giorno della furia | Dia da fúria | День люті | Dag van woede | Öfke Günü |

### Zestawy tygodnia

Zestaw tygodnia określa warunki wyzwania tygodniowego.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Złote ruiny (`day109_gold`) | Złote ruiny | Golden Ruins | Goldene Ruinen | Ruinas doradas | Ruines dorées | Rovine dorate | Ruínas douradas | Золоті руїни | Gouden ruïnes | Altın Harabeler |
| Marsz we mgle (`day109_march`) | Marsz we mgle | March in the Fog | Marsch im Nebel | Marcha en la niebla | Marche dans le brouillard | Marcia nella nebbia | Marcha no nevoeiro | Марш у тумані | Mars in de mist | Sisli Yürüyüş |
| Strzelcy i wieże (`day109_rain`) | Strzelcy i wieże | Ranged Troops and Turrets | Schützen und Türme | Tiradores y torretas | Tireurs et tourelles | Tiratori e torrette | Atiradores e torres | Стрільці та вежі | Schutters en torens | Nişancılar ve Kuleler |
| Bogaci weterani (`day109_veterans`) | Bogaci weterani | Wealthy Veterans | Reiche Veteranen | Veteranos ricos | Vétérans fortunés | Veterani facoltosi | Veteranos ricos | Заможні ветерани | Rijke veteranen | Zengin Gaziler |
| Najemna fala (`day109_hired`) | Najemna fala | Mercenary Wave | Söldnerwelle | Oleada mercenaria | Vague mercenaire | Ondata mercenaria | Onda mercenária | Хвиля найманців | Huurlingengolf | Paralı Asker Dalgası |
| Żelazny szturm (`day109_iron`) | Żelazny szturm | Iron Assault | Eiserner Sturm | Asalto de hierro | Assaut de fer | Assalto di ferro | Assalto de ferro | Залізний штурм | IJzeren aanval | Demir Hücum |
| Nocna straż (`week184_night`) | Nocna straż | Night Watch | Nachtwache | Guardia nocturna | Garde de nuit | Guardia notturna | Guarda noturna | Нічна варта | Nachtwacht | Gece Nöbeti |
| Żołd i łupy (`week184_mercs`) | Żołd i łupy | Pay and Plunder | Sold und Beute | Paga y botín | Solde et pillage | Paga e bottino | Soldo e saque | Платня і здобич | Soldij en buit | Maaş ve Yağma |
| Kruchy bastion (`week184_bastion`) | Kruchy bastion | Fragile Bastion | Brüchige Bastion | Bastión frágil | Bastion fragile | Bastione fragile | Bastião frágil | Крихкий бастіон | Broos bastion | Kırılgan Burç |
| Wojna błyskawiczna (`week206_blitz`) | Wojna błyskawiczna | Lightning War | Blitzfeldzug | Guerra relámpago | Guerre éclair | Guerra lampo | Guerra relâmpago | Блискавична війна | Bliksemoorlog | Yıldırım Savaşı |
| Żelazny szyk (`week206_pikes`) | Żelazny szyk | Iron Formation | Eiserne Formation | Formación de hierro | Formation de fer | Formazione di ferro | Formação de ferro | Залізний стрій | IJzeren formatie | Demir Düzen |

### Rozkazy i inne nazwy interfejsu

Nazwy poniżej zachowują kontekst klucza: tryb, jednostka, zasób albo przycisk.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Rozkazy sztabu (`o209_title`) | Rozkazy sztabu | Staff Orders | Stabsbefehle | Órdenes del estado mayor | Ordres d'état-major | Ordini dello stato maggiore | Ordens do estado-maior | Накази штабу | Stafbevelen | Karargâh emirleri |
| Rozkazy (`o209_menu`) | Rozkazy | Orders | Befehle | Órdenes | Ordres | Ordini | Ordens | Накази | Bevelen | Emirler |
| Punkty sztabu (`o209_points_short`) | Punkty sztabu | Staff points | Stabspunkte | Puntos | Points | Punti | Pontos | Очки штабу | Stafpunten | Karargâh puanı |
| Cel główny (`o209_main`) | Cel główny | Main goal | Hauptziel | Objetivo principal | Objectif principal | Obiettivo principale | Objetivo principal | Головна мета | Hoofddoel | Ana hedef |
| Cele dodatkowe (`o209_bonus`) | Cele dodatkowe | Bonus goals | Zusatzziele | Objetivos extra | Objectifs bonus | Obiettivi extra | Objetivos extra | Додаткові цілі | Extra doelen | Ek hedefler |
| dowódca (`o209_k_commander`) | dowódca | commander | Kommandant | comandante | commandant | comandante | comandante | командир | commandant | komutan |
| Zryw (`battle84_rally_short`) | Zryw | Rally | Sturm | Impulso | Élan | Slancio | Ímpeto | Ривок | Aanval | Hücum |
| Wydarzenie dnia (`cx184_daily_event`) | Wydarzenie dnia | Daily event | Tagesereignis | Evento diario | Événement du jour | Evento del giorno | Evento diário | Подія дня | Dagevenement | Günlük etkinlik |
| Zestaw tygodnia (`cx184_weekly_set`) | Zestaw tygodnia | Weekly rule set | Wochen-Regelset | Reglas semanales | Règles de la semaine | Regole settimanali | Regras semanais | Набір тижня | Weekregels | Haftalık kural seti |
| Stopnie wież (`cx176_tiers`) | Stopnie wież | Turret tiers | Turmstufen | Niveles de torreta | Niveaux de tourelle | Gradi delle torrette | Níveis de torre | Рівні веж | Torenniveaus | Kule seviyeleri |
| Taktyka dowódcy (`cx176_commander_tips`) | Taktyka dowódcy | Commander tactics | Kommandanten-Taktik | Tácticas del comandante | Tactiques du commandant | Tattiche del comandante | Táticas do comandante | Тактика командира | Commandanttactiek | Komutan taktikleri |
| Nagroda (`reward`) | Nagroda | Reward | Belohnung | Recompensa | Récompense | Ricompensa | Recompensa | Нагорода | Beloning | Ödül |
| Fala (`bh_wave`) | Fala | Wave | Welle | Oleada | Vague | Ondata | Onda | Хвиля | Golf | Dalga |
| Karta startowa (`weekly_start_card`) | Karta startowa | Starting card | Startkarte | Carta inicial | Carte de départ | Carta iniziale | Carta inicial | Стартова карта | Startkaart | Başlangıç kartı |
| Wybierz kartę (`choose_card`) | Wybierz kartę | Choose a card | Wähle eine Karte | Elige una carta | Choisir une carte | Scegli una carta | Escolhe uma carta | Обери карту | Kies een kaart | Bir kart seç |
| Tydzień (`weekly_week_tab`) | Tydzień | Week | Woche | Semana | Semaine | Settimana | Semana | Тиждень | Week | Hafta |
| Tydzień (`global_period_week`) | Tydzień | Week | Woche | Semana | Semaine | Settimana | Semana | Тиждень | Week | Hafta |
| Gospodarz (`cx127_economist`) | Gospodarz | Economist | Ökonom | Economista | Économe | Economista | Economista | Господарник | Econoom | Ekonomist |
| Oblężnik (`cx127_sieger`) | Oblężnik | Siege Expert | Belagerer | Asediador | Assiégeant | Assediante | Sitiador | Майстер облоги | Belegeraar | Kuşatmacı |
| Taktyk (`cx127_balanced`) | Taktyk | Tactician | Taktiker | Táctico | Tacticien | Tattico | Tático | Тактик | Tacticus | Taktikçi |

### Często używane pojęcia walki i postępu

Nazwy ogólne jednostek i działań, parametrów, wyników oraz interfejsu. Kolumny podają etykietę z podanego klucza; w zdaniach mogą występować odmiana i warianty kontekstowe.

| Pojęcie (klucz) | PL | EN | DE | ES | FR | IT | PT | UK | NL | TR |
|---|---|---|---|---|---|---|---|---|---|---|
| Dowódca (`comm125_name`) | Dowódca | Commander | Kommandant | Comandante | Commandant | Comandante | Comandante | Командир | Commandant | Komutan |
| Wieże (`turrets`) | Wieże | Turrets | Türme | Torretas | Tourelles | Torrette | Torres | Вежі | Torens | Kuleler |
| Powtórka (`replay`) | Powtórka | Replay | Wiederholung | Repetición | Replay | Replay | Repetição | Повтор | Herhaling | Tekrar |
| Badania (`research`) | Badania | Research | Forschung | Investigación | Recherche | Ricerca | Pesquisa | Дослідження | Onderzoek | Araştırma |
| Wróg (`bs104_enemy`) | Wróg | Enemy | Gegner | Enemigo | Ennemi | Nemico | Inimigo | Ворог | Vijand | Rakip |
| HP baz (`bs104_hp`) | HP baz | Base HP | Basis-LP | PV de bases | PV des bases | PV delle basi | PV das bases | Міцність баз | Basis-HP | Üs canı |
| Pogoda (`w_label`) | Pogoda | Weather | Wetter | Clima | Météo | Meteo | Clima | Погода | Weer | Hava |
| Czas (`time`) | Czas | Time | Zeit | Tiempo | Temps | Tempo | Tempo | Час | Tijd | Süre |
| Wynik (`score`) | Wynik | Score | Punkte | Puntuación | Score | Punteggio | Pontuação | Очки | Score | Puan |
| Ulepsz (`upgrade`) | Ulepsz | Upgrade | Verbessern | Mejorar | Améliorer | Potenzia | Melhorar | Покращити | Upgraden | Yükselt |
| Armia (`cap`) | Armia | Army | Armee | Ejército | Armée | Esercito | Exército | Армія | Leger | Ordu |
| Piechur (`u_infantry`) | Piechur | Infantry | Infanterist | Infante | Fantassin | Fante | Infante | Піхотинець | Infanterist | Piyade |
| BOSS (`eb_boss`) | BOSS | BOSS | BOSS | JEFE | BOSS | BOSS | CHEFE | БОС | BAAS | BOSS |
| Koszt (`cx_cost`) | Koszt | Cost | Kosten | Coste | Coût | Costo | Custo | Ціна | Kosten | Maliyet |
| Obrażenia (`cx_dmg`) | Obrażenia | Damage | Schaden | Daño | Dégâts | Danni | Dano | Шкода | Schade | Hasar |
| Zasięg (`cx_range`) | Zasięg | Range | Reichweite | Alcance | Portée | Gittata | Alcance | Дальність | Bereik | Menzil |
| Szybkość (`cx_speed`) | Szybkość | Speed | Tempo | Velocidad | Vitesse | Velocità | Velocidade | Швидкість | Snelheid | Hız |
| Szkolenie (`cx_train`) | Szkolenie | Training | Ausbildung | Entrenamiento | Entraînement | Addestramento | Treino | Навчання | Training | Eğitim |
| Odnowienie (`cx_cd`) | Odnowienie | Cooldown | Abklingzeit | Enfriamiento | Récupération | Recupero | Espera | Відновлення | Afkoeltijd | Bekleme |
| Bitwy (`bh_battles`) | Bitwy | Battles | Schlachten | Batallas | Batailles | Battaglie | Batalhas | Битви | Gevechten | Savaşlar |
| Wygrane (`bh_wins`) | Wygrane | Wins | Siege | Victorias | Victoires | Vittorie | Vitórias | Перемоги | Overwinningen | Galibiyetler |
| Etap (`bh_stage`) | Etap | Stage | Etappe | Etapa | Étape | Tappa | Etapa | Етап | Etappe | Aşama |
| Jednostki (`cx62_units`) | Jednostki | Units | Einheiten | Unidades | Unités | Unità | Unidades | Бійці | Eenheden | Birimler |
| Trudny (`campaign126_hard`) | Trudny | Hard | Schwer | Difícil | Difficile | Difficile | Difícil | Складний | Moeilijk | Zor |
| Bitwa (`a178_battle`) | Bitwa | Battle | Schlacht | Batalla | Bataille | Battaglia | Batalha | Битва | Gevecht | Savaş |
| PORAŻKA (`defeat`) | PORAŻKA | DEFEAT | NIEDERLAGE | DERROTA | DÉFAITE | SCONFITTA | DERROTA | ПОРАЗКА | NEDERLAAG | YENİLGİ |
| Ustawienia (`settings`) | Ustawienia | Settings | Einstellungen | Ajustes | Paramètres | Impostazioni | Ajustes | Налаштування | Instellingen | Ayarlar |
| wręcz (`o209_k_melee`) | wręcz | melee | Nahkampf | cuerpo a cuerpo | mêlée | mischia | corpo a corpo | ближній бій | melee | yakın dövüş |
| dystans (`o209_k_ranged`) | dystans | ranged | Fernkampf | a distancia | distance | distanza | à distância | стрільці | afstand | menzilli |
| ciężcy (`o209_k_heavy`) | ciężcy | heavy | Schwere | pesados | lourds | pesanti | pesados | важкі | zwaar | ağır |
| ostrzał (`o209_k_barrage`) | ostrzał | barrage | Sperrfeuer | bombardeo | barrage | sbarramento | barragem | обстріл | spervuur | yaylım ateşi |

## Reguły utrzymania

Przy każdej zmianie nazwy porównuj `L.kt` z glosariuszem, aktualizuj równocześnie powiązane ekrany (`bh_ai_*`, `sk_person_*`, `cx_*`) i uruchom testy `LTest`. Klucze pozostają bez zmian, a liczba i kolejność dziesięciu tłumaczeń są obowiązkowe.
