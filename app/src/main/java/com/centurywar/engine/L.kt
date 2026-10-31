package com.centurywar.engine

import java.util.Locale

/**
 * Teksty gry. Gra rysuje wszystko na Canvas, więc trzymamy teksty w kodzie zamiast
 * w zasobach XML. Każdy wpis to tablica w kolejności [CODES]; brakujący lub pusty
 * przekład zastępuje angielski. Nowy język: dopisz kod do CODES/NAMES i kolumnę w tabeli.
 */
object L {
    /** Kody języków (ISO 639-1) w kolejności kolumn tabeli. */
    val CODES = arrayOf("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")
    /** Nazwy języków w nich samych (przycisk w Ustawieniach). */
    val NAMES = arrayOf("Polski", "English", "Deutsch", "Español", "Français", "Italiano", "Português", "Українська", "Nederlands", "Türkçe")
    const val PL = 0
    const val EN = 1

    /** Indeks bieżącego języka w [CODES]. */
    var lang: Int = systemLang()

    /** Zgodność wsteczna: true = polski. */
    var polish: Boolean
        get() = lang == PL
        set(v) { lang = if (v) PL else EN }

    /** Indeks języka dla kodu (np. "de", "pt-BR", "pt_BR") albo -1. */
    fun indexOf(code: String): Int =
        CODES.indexOf(code.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT))

    /** Język systemu, jeśli go obsługujemy; inaczej angielski. */
    fun systemLang(): Int {
        val idx = indexOf(Locale.getDefault().language)
        return if (idx >= 0) idx else EN
    }

    /** Ustawia język z kodu profilu ("" = systemowy). */
    fun setCode(code: String) {
        val idx = if (code.isEmpty()) -1 else indexOf(code)
        lang = if (idx >= 0) idx else systemLang()
    }

    operator fun get(key: String): String = text(key, lang)

    /** Tekst w danym języku (brak lub pusty przekład → angielski). */
    fun text(key: String, langIdx: Int): String {
        val e = table[key] ?: return key
        val s = e.getOrNull(langIdx)
        return if (s.isNullOrEmpty()) e[EN] else s
    }

    fun f(key: String, vararg args: Any): String = String.format(Locale.ROOT, get(key), *args)

    /** Klucze tabeli (dla testów). */
    val keys: Set<String> get() = table.keys

    /** Surowy wpis tabeli (dla testów). */
    fun raw(key: String): Array<out String>? = table[key]

    private fun t(vararg s: String): Array<out String> = s

    // kolejność kolumn: pl, en, de, es, fr, it, pt, uk, nl, tr
    private val table: HashMap<String, Array<out String>> = hashMapOf(
        // --- menu
        "title" to t("CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR", "CENTURY WAR"),
        "subtitle" to t("Wojna przez stulecia", "War across the centuries", "Krieg durch die Jahrhunderte", "Guerra a través de los siglos", "La guerre à travers les siècles", "Guerra attraverso i secoli", "Guerra através dos séculos", "Війна крізь століття", "Oorlog door de eeuwen heen", "Yüzyıllar boyu savaş"),
        "campaign" to t("Kampania", "Campaign", "Kampagne", "Campaña", "Campagne", "Campagna", "Campanha", "Кампанія", "Campagne", "Sefer"),
        "endless" to t("Oblężenie bez końca", "Endless Siege", "Endlose Belagerung", "Asedio sin fin", "Siège sans fin", "Assedio infinito", "Cerco sem fim", "Нескінченна облога", "Eindeloos beleg", "Sonsuz Kuşatma"),
        "daily" to t("Wyzwanie dnia", "Daily Challenge", "Tageschallenge", "Desafío diario", "Défi du jour", "Sfida del giorno", "Desafio diário", "Виклик дня", "Daguitdaging", "Günlük Mücadele"),
        "skirmish" to t("Potyczka", "Skirmish", "Gefecht", "Escaramuza", "Escarmouche", "Schermaglia", "Escaramuça", "Сутичка", "Schermutseling", "Çatışma"),
        "research" to t("Badania", "Research", "Forschung", "Investigación", "Recherche", "Ricerca", "Pesquisa", "Дослідження", "Onderzoek", "Araştırma"),
        "rankings" to t("Rankingi", "Rankings", "Ranglisten", "Clasificación", "Classements", "Classifiche", "Ranking", "Рейтинги", "Ranglijsten", "Sıralamalar"),
        "settings" to t("Ustawienia", "Settings", "Einstellungen", "Ajustes", "Paramètres", "Impostazioni", "Ajustes", "Налаштування", "Instellingen", "Ayarlar"),
        "back" to t("Wstecz", "Back", "Zurück", "Atrás", "Retour", "Indietro", "Voltar", "Назад", "Terug", "Geri"),
        "play" to t("Graj", "Play", "Spielen", "Jugar", "Jouer", "Gioca", "Jogar", "Грати", "Spelen", "Oyna"),
        "medals" to t("Medale", "Medals", "Medaillen", "Medallas", "Médailles", "Medaglie", "Medalhas", "Медалі", "Medailles", "Madalyalar"),
        "stars" to t("Gwiazdki", "Stars", "Sterne", "Estrellas", "Étoiles", "Stelle", "Estrelas", "Зірки", "Sterren", "Yıldızlar"),
        "locked" to t("Zablokowane", "Locked", "Gesperrt", "Bloqueado", "Verrouillé", "Bloccato", "Bloqueado", "Заблоковано", "Vergrendeld", "Kilitli"),
        "best" to t("Najlepszy", "Best", "Bestwert", "Mejor", "Meilleur", "Migliore", "Melhor", "Рекорд", "Beste", "En iyi"),
        "stage_n" to t("Etap %d", "Stage %d", "Etappe %d", "Etapa %d", "Étape %d", "Tappa %d", "Etapa %d", "Етап %d", "Etappe %d", "Bölüm %d"),
        "eras_range" to t("Epoki: %s → %s", "Eras: %s → %s", "Epochen: %s → %s", "Eras: %s → %s", "Ères : %s → %s", "Ere: %s → %s", "Eras: %s → %s", "Епохи: %s → %s", "Tijdperken: %s → %s", "Çağlar: %s → %s"),
        "ai_power" to t("Siła wroga: %d%%", "Enemy strength: %d%%", "Feindstärke: %d%%", "Fuerza enemiga: %d%%", "Force ennemie : %d%%", "Forza nemica: %d%%", "Força inimiga: %d%%", "Сила ворога: %d%%", "Vijandelijke kracht: %d%%", "Düşman gücü: %%%d"),
        "modifiers" to t("Modyfikatory", "Modifiers", "Modifikatoren", "Modificadores", "Modificateurs", "Modificatori", "Modificadores", "Модифікатори", "Modificatoren", "Değiştiriciler"),
        "none" to t("brak", "none", "keine", "ninguno", "aucun", "nessuno", "nenhum", "немає", "geen", "yok"),
        "start" to t("Start", "Start", "Start", "Empezar", "Démarrer", "Inizia", "Iniciar", "Старт", "Start", "Başla"),
        "difficulty" to t("Trudność", "Difficulty", "Schwierigkeit", "Dificultad", "Difficulté", "Difficoltà", "Dificuldade", "Складність", "Moeilijkheid", "Zorluk"),
        "diff_0" to t("Łatwy", "Easy", "Leicht", "Fácil", "Facile", "Facile", "Fácil", "Легко", "Makkelijk", "Kolay"),
        "diff_1" to t("Normalny", "Normal", "Normal", "Normal", "Normal", "Normale", "Normal", "Нормально", "Normaal", "Normal"),
        "diff_2" to t("Trudny", "Hard", "Schwer", "Difícil", "Difficile", "Difficile", "Difícil", "Важко", "Moeilijk", "Zor"),
        "diff_3" to t("Szalony", "Insane", "Wahnsinn", "Demencial", "Démentiel", "Folle", "Insano", "Божевільно", "Krankzinnig", "Çılgın"),
        "daily_info" to t(
            "Te same zasady dla wszystkich graczy. Badania nie działają — liczy się taktyka.",
            "Same rules for every player. Research is disabled — only tactics count.",
            "Gleiche Regeln für alle Spieler. Forschung ist deaktiviert — nur Taktik zählt.",
            "Mismas reglas para todos los jugadores. Investigación desactivada — solo cuenta la táctica.",
            "Mêmes règles pour tous les joueurs. Recherche désactivée — seule la tactique compte.",
            "Stesse regole per tutti i giocatori. Ricerca disattivata — conta solo la tattica.",
            "Mesmas regras para todos os jogadores. Pesquisa desativada — só a tática conta.",
            "Однакові правила для всіх гравців. Дослідження вимкнено — важить лише тактика.",
            "Dezelfde regels voor iedereen. Onderzoek staat uit — alleen tactiek telt.",
            "Tüm oyuncular için aynı kurallar. Araştırma kapalı — yalnızca taktik önemli."),
        "daily_ranked" to t("Podejście rankingowe: %s", "Ranked attempt: %s", "Ranglistenversuch: %s", "Intento clasificatorio: %s", "Tentative classée : %s", "Tentativo in classifica: %s", "Tentativa ranqueada: %s", "Рейтингова спроба: %s", "Ranglijstpoging: %s", "Sıralama denemesi: %s"),
        "daily_used" to t("wykorzystane (trening)", "used (practice)", "verbraucht (Training)", "usado (práctica)", "utilisée (entraînement)", "usato (allenamento)", "usada (treino)", "використано (тренування)", "gebruikt (oefenen)", "kullanıldı (antrenman)"),
        "daily_free" to t("dostępne", "available", "verfügbar", "disponible", "disponible", "disponibile", "disponível", "доступна", "beschikbaar", "hazır"),
        "endless_info" to t(
            "Przetrwaj jak najwięcej fal. Co 5 fal wybierasz doktrynę.",
            "Survive as many waves as you can. Pick a doctrine every 5 waves.",
            "Überstehe so viele Wellen wie möglich. Alle 5 Wellen wählst du eine Doktrin.",
            "Sobrevive a tantas oleadas como puedas. Cada 5 oleadas eliges una doctrina.",
            "Survivez au plus grand nombre de vagues. Toutes les 5 vagues, choisissez une doctrine.",
            "Sopravvivi a più ondate possibile. Ogni 5 ondate scegli una dottrina.",
            "Sobreviva ao máximo de ondas. A cada 5 ondas, escolha uma doutrina.",
            "Протримайся якомога більше хвиль. Кожні 5 хвиль обираєш доктрину.",
            "Overleef zoveel mogelijk golven. Elke 5 golven kies je een doctrine.",
            "Olabildiğince çok dalgaya dayan. Her 5 dalgada bir doktrin seç."),
        "level" to t("Poz. %d/%d", "Lv %d/%d", "St. %d/%d", "Nv. %d/%d", "Niv. %d/%d", "Liv. %d/%d", "Nv. %d/%d", "Рів. %d/%d", "Niv. %d/%d", "Sv. %d/%d"),
        "max" to t("MAKS", "MAX", "MAX", "MÁX", "MAX", "MAX", "MÁX", "МАКС", "MAX", "MAKS"),
        "buy" to t("Kup", "Buy", "Kaufen", "Comprar", "Acheter", "Compra", "Comprar", "Купити", "Kopen", "Satın al"),
        "name" to t("Nazwa dowódcy", "Commander name", "Kommandantenname", "Nombre del comandante", "Nom du commandant", "Nome comandante", "Nome do comandante", "Ім'я командира", "Naam commandant", "Komutan adı"),
        "quality" to t("Grafika", "Graphics", "Grafik", "Gráficos", "Graphismes", "Grafica", "Gráficos", "Графіка", "Graphics", "Grafik"),
        "q_auto" to t("Auto", "Auto", "Auto", "Auto", "Auto", "Auto", "Auto", "Авто", "Auto", "Oto"),
        "q_low" to t("Niska", "Low", "Niedrig", "Baja", "Basse", "Bassa", "Baixa", "Низька", "Laag", "Düşük"),
        "q_med" to t("Średnia", "Medium", "Mittel", "Media", "Moyenne", "Media", "Média", "Середня", "Middel", "Orta"),
        "q_high" to t("Wysoka", "High", "Hoch", "Alta", "Haute", "Alta", "Alta", "Висока", "Hoog", "Yüksek"),
        "fps" to t("Limit FPS", "FPS limit", "FPS-Limit", "Límite de FPS", "Limite FPS", "Limite FPS", "Limite de FPS", "Ліміт FPS", "FPS-limiet", "FPS sınırı"),
        "fps_max" to t("Ekran", "Display", "Display", "Pantalla", "Écran", "Schermo", "Tela", "Екран", "Scherm", "Ekran"),
        "language" to t("Język", "Language", "Sprache", "Idioma", "Langue", "Lingua", "Idioma", "Мова", "Taal", "Dil"),
        "vibration" to t("Wibracje", "Vibration", "Vibration", "Vibración", "Vibrations", "Vibrazione", "Vibração", "Вібрація", "Trillen", "Titreşim"),
        "sound" to t("Dźwięk", "Sound", "Ton", "Sonido", "Son", "Audio", "Som", "Звук", "Geluid", "Ses"),
        "on" to t("Wł.", "On", "An", "Sí", "Oui", "Sì", "Lig.", "Увімк.", "Aan", "Açık"),
        "off" to t("Wył.", "Off", "Aus", "No", "Non", "No", "Desl.", "Вимк.", "Uit", "Kapalı"),
        "show_fps" to t("Pokaż FPS", "Show FPS", "FPS anzeigen", "Mostrar FPS", "Afficher FPS", "Mostra FPS", "Mostrar FPS", "Показувати FPS", "FPS tonen", "FPS göster"),
        "reset" to t("Resetuj postęp", "Reset progress", "Fortschritt zurücksetzen", "Reiniciar progreso", "Effacer la progression", "Azzera progressi", "Zerar progresso", "Скинути прогрес", "Voortgang wissen", "İlerlemeyi sıfırla"),
        "reset_confirm" to t("Na pewno? Dotknij ponownie", "Sure? Tap again", "Sicher? Nochmal tippen", "¿Seguro? Toca otra vez", "Sûr ? Touchez encore", "Sicuro? Tocca di nuovo", "Certeza? Toque de novo", "Впевнені? Торкніться ще раз", "Zeker? Tik nogmaals", "Emin misin? Tekrar dokun"),
        "board_endless" to t("Oblężenie — fale", "Siege — waves", "Belagerung — Wellen", "Asedio — oleadas", "Siège — vagues", "Assedio — ondate", "Cerco — ondas", "Облога — хвилі", "Beleg — golven", "Kuşatma — dalgalar"),
        "board_daily" to t("Wyzwanie dnia (dziś)", "Daily challenge (today)", "Tageschallenge (heute)", "Desafío diario (hoy)", "Défi du jour (aujourd'hui)", "Sfida del giorno (oggi)", "Desafio diário (hoje)", "Виклик дня (сьогодні)", "Daguitdaging (vandaag)", "Günlük Mücadele (bugün)"),
        "board_campaign" to t("Kampania — gwiazdki", "Campaign — stars", "Kampagne — Sterne", "Campaña — estrellas", "Campagne — étoiles", "Campagna — stelle", "Campanha — estrelas", "Кампанія — зірки", "Campagne — sterren", "Sefer — yıldızlar"),
        "board_skirmish" to t("Potyczka — wynik", "Skirmish — score", "Gefecht — Punkte", "Escaramuza — puntos", "Escarmouche — score", "Schermaglia — punteggio", "Escaramuça — pontos", "Сутичка — очки", "Schermutseling — score", "Çatışma — puan"),
        "no_scores" to t("Brak wyników — zagraj!", "No scores yet — play!", "Noch keine Ergebnisse — spiel los!", "Aún no hay resultados — ¡juega!", "Aucun score — jouez !", "Nessun risultato — gioca!", "Sem resultados — jogue!", "Ще немає результатів — грай!", "Nog geen scores — speel!", "Henüz skor yok — oyna!"),
        "online_note" to t(
            "Ranking globalny: Google Play Games (po konfiguracji w Play Console)",
            "Global ranking: Google Play Games (after Play Console setup)",
            "Globale Rangliste: Google Play Games (nach Einrichtung in der Play Console)",
            "Clasificación global: Google Play Games (tras configurar Play Console)",
            "Classement mondial : Google Play Games (après configuration de la Play Console)",
            "Classifica globale: Google Play Games (dopo la configurazione in Play Console)",
            "Ranking global: Google Play Games (após configurar o Play Console)",
            "Глобальний рейтинг: Google Play Games (після налаштування в Play Console)",
            "Wereldwijde ranglijst: Google Play Games (na instelling in Play Console)",
            "Küresel sıralama: Google Play Games (Play Console ayarından sonra)"),
        "stats" to t("Statystyki", "Statistics", "Statistik", "Estadísticas", "Statistiques", "Statistiche", "Estatísticas", "Статистика", "Statistieken", "İstatistikler"),

        // --- bitwa
        "gold" to t("Złoto", "Gold", "Gold", "Oro", "Or", "Oro", "Ouro", "Золото", "Goud", "Altın"),
        "xp" to t("XP", "XP", "XP", "XP", "XP", "XP", "XP", "XP", "XP", "XP"),
        "evolve" to t("Awans", "Evolve", "Aufstieg", "Evolucionar", "Évoluer", "Evolvi", "Evoluir", "Розвиток", "Evolueren", "Evrim"),
        "next_era" to t("Nowa epoka!", "New era!", "Neue Epoche!", "¡Nueva era!", "Nouvelle ère !", "Nuova era!", "Nova era!", "Нова епоха!", "Nieuw tijdperk!", "Yeni çağ!"),
        "turrets" to t("Wieże", "Turrets", "Türme", "Torretas", "Tourelles", "Torrette", "Torres", "Вежі", "Torens", "Kuleler"),
        "armory" to t("Zbrojownia", "Armory", "Rüstkammer", "Armería", "Armurerie", "Armeria", "Arsenal", "Зброярня", "Wapenkamer", "Cephanelik"),
        "armory_desc" to t("+8% obrażeń i HP nowych jednostek", "+8% damage & HP for new units", "+8% Schaden & LP für neue Einheiten", "+8% daño y PV a nuevas unidades", "+8% dégâts et PV des nouvelles unités", "+8% danni e PS alle nuove unità", "+8% dano e PV para novas unidades", "+8% шкоди та ОЗ новим бійцям", "+8% schade & HP voor nieuwe eenheden", "Yeni birimlere +%8 hasar ve can"),
        "hold" to t("Trzymaj", "Hold", "Halten", "Mantener", "Tenir", "Tieni", "Manter", "Тримати", "Houden", "Tut"),
        "advance" to t("Naprzód", "Advance", "Vorrücken", "Avanzar", "Avancer", "Avanza", "Avançar", "Уперед", "Oprukken", "İlerle"),
        "rally" to t("Zryw", "Rally", "Ansturm", "Ímpetu", "Élan", "Slancio", "Ímpeto", "Ривок", "Stormloop", "Atılım"),
        "paused" to t("Pauza", "Paused", "Pausiert", "En pausa", "Pause", "In pausa", "Pausado", "Пауза", "Gepauzeerd", "Duraklatıldı"),
        "resume" to t("Wznów", "Resume", "Weiter", "Reanudar", "Reprendre", "Riprendi", "Continuar", "Продовжити", "Hervatten", "Devam"),
        "quit" to t("Poddaj się", "Surrender", "Aufgeben", "Rendirse", "Abandonner", "Arrenditi", "Render-se", "Здатися", "Opgeven", "Teslim ol"),
        "restart" to t("Od nowa", "Restart", "Neustart", "Reiniciar", "Recommencer", "Ricomincia", "Reiniciar", "Заново", "Opnieuw", "Yeniden"),
        "victory" to t("ZWYCIĘSTWO", "VICTORY", "SIEG", "VICTORIA", "VICTOIRE", "VITTORIA", "VITÓRIA", "ПЕРЕМОГА", "OVERWINNING", "ZAFER"),
        "defeat" to t("PORAŻKA", "DEFEAT", "NIEDERLAGE", "DERROTA", "DÉFAITE", "SCONFITTA", "DERROTA", "ПОРАЗКА", "NEDERLAAG", "YENİLGİ"),
        "siege_over" to t("OBLĘŻENIE ZAKOŃCZONE", "SIEGE ENDED", "BELAGERUNG BEENDET", "ASEDIO TERMINADO", "SIÈGE TERMINÉ", "ASSEDIO CONCLUSO", "CERCO ENCERRADO", "ОБЛОГУ ЗАВЕРШЕНО", "BELEG VOORBIJ", "KUŞATMA BİTTİ"),
        "choose_doctrine" to t("Wybierz doktrynę", "Choose a doctrine", "Wähle eine Doktrin", "Elige una doctrina", "Choisissez une doctrine", "Scegli una dottrina", "Escolha uma doutrina", "Обери доктрину", "Kies een doctrine", "Bir doktrin seç"),
        "wave" to t("Fala %d", "Wave %d", "Welle %d", "Oleada %d", "Vague %d", "Ondata %d", "Onda %d", "Хвиля %d", "Golf %d", "Dalga %d"),
        "time" to t("Czas", "Time", "Zeit", "Tiempo", "Temps", "Tempo", "Tempo", "Час", "Tijd", "Süre"),
        "kills" to t("Zabici", "Kills", "Abschüsse", "Bajas", "Éliminations", "Uccisioni", "Abates", "Вбивства", "Kills", "Öldürme"),
        "score" to t("Wynik", "Score", "Punkte", "Puntuación", "Score", "Punteggio", "Pontuação", "Очки", "Score", "Puan"),
        "reward" to t("Nagroda", "Reward", "Belohnung", "Recompensa", "Récompense", "Ricompensa", "Recompensa", "Нагорода", "Beloning", "Ödül"),
        "new_record" to t("NOWY REKORD!", "NEW RECORD!", "NEUER REKORD!", "¡NUEVO RÉCORD!", "NOUVEAU RECORD !", "NUOVO RECORD!", "NOVO RECORDE!", "НОВИЙ РЕКОРД!", "NIEUW RECORD!", "YENİ REKOR!"),
        "continue" to t("Dalej", "Continue", "Weiter", "Continuar", "Continuer", "Continua", "Continuar", "Далі", "Verder", "Devam"),
        "slot" to t("Slot %d", "Slot %d", "Platz %d", "Ranura %d", "Emplacement %d", "Slot %d", "Espaço %d", "Слот %d", "Slot %d", "Yuva %d"),
        "sell" to t("Sprzedaj", "Sell", "Verkaufen", "Vender", "Vendre", "Vendi", "Vender", "Продати", "Verkopen", "Sat"),
        "upgrade" to t("Ulepsz", "Upgrade", "Verbessern", "Mejorar", "Améliorer", "Potenzia", "Melhorar", "Покращити", "Upgraden", "Yükselt"),
        "empty" to t("pusty", "empty", "leer", "vacío", "vide", "vuoto", "vazio", "порожньо", "leeg", "boş"),
        "rank" to t("Ranga %d", "Rank %d", "Rang %d", "Rango %d", "Rang %d", "Grado %d", "Patente %d", "Ранг %d", "Rang %d", "Rütbe %d"),
        "siege_pressure" to t("Presja oblężnicza ×%.1f", "Siege pressure ×%.1f", "Belagerungsdruck ×%.1f", "Presión de asedio ×%.1f", "Pression de siège ×%.1f", "Pressione d'assedio ×%.1f", "Pressão do cerco ×%.1f", "Тиск облоги ×%.1f", "Belegdruk ×%.1f", "Kuşatma baskısı ×%.1f"),
        "why_lost" to t("Wskazówka", "Tip", "Tipp", "Consejo", "Conseil", "Suggerimento", "Dica", "Порада", "Tip", "İpucu"),
        "tip_eco" to t(
            "Wróg miał więcej złota — pchaj linię frontu, by zwiększyć przychód.",
            "The enemy out-earned you — push the front line to boost income.",
            "Der Feind hat mehr verdient — schieb die Front vor, um dein Einkommen zu steigern.",
            "El enemigo ganó más oro — empuja el frente para aumentar tus ingresos.",
            "L'ennemi a gagné plus d'or — repoussez la ligne de front pour augmenter vos revenus.",
            "Il nemico ha guadagnato di più — spingi la linea del fronte per aumentare le entrate.",
            "O inimigo ganhou mais ouro — avance a linha de frente para aumentar sua renda.",
            "Ворог мав більше золота — тисни лінію фронту, щоб збільшити дохід.",
            "De vijand verdiende meer — duw de frontlinie op voor meer inkomsten.",
            "Düşman daha çok altın kazandı — geliri artırmak için cepheyi ileri it."),
        "tip_era" to t(
            "Wróg szybciej awansował — oszczędzaj XP i awansuj wcześniej.",
            "The enemy evolved faster — evolve earlier.",
            "Der Feind ist schneller aufgestiegen — steig früher auf.",
            "El enemigo evolucionó más rápido — evoluciona antes.",
            "L'ennemi a évolué plus vite — évoluez plus tôt.",
            "Il nemico si è evoluto più in fretta — evolvi prima.",
            "O inimigo evoluiu mais rápido — evolua mais cedo.",
            "Ворог розвивався швидше — переходь у нову епоху раніше.",
            "De vijand evolueerde sneller — evolueer eerder.",
            "Düşman daha hızlı evrimleşti — daha erken evrimleş."),
        "tip_turret" to t(
            "Wieże i Zbrojownia pomagają, gdy armia jest pełna.",
            "Turrets and the Armory help when your army is full.",
            "Türme und die Rüstkammer helfen, wenn deine Armee voll ist.",
            "Las torretas y la armería ayudan cuando tu ejército está completo.",
            "Les tourelles et l'armurerie aident quand votre armée est au complet.",
            "Torrette e armeria aiutano quando l'esercito è al completo.",
            "Torres e o Arsenal ajudam quando seu exército está cheio.",
            "Вежі та зброярня допомагають, коли армія повна.",
            "Torens en de wapenkamer helpen als je leger vol is.",
            "Kuleler ve cephanelik, ordun doluyken işe yarar."),
        "tip_ability" to t(
            "Używaj ostrzału, gdy wróg zbije się w grupę.",
            "Use the barrage when enemies bunch up.",
            "Setz das Sperrfeuer ein, wenn sich Feinde ballen.",
            "Usa el bombardeo cuando los enemigos se agrupen.",
            "Utilisez le barrage quand les ennemis se regroupent.",
            "Usa il fuoco di sbarramento quando i nemici si ammassano.",
            "Use o bombardeio quando os inimigos se agruparem.",
            "Використовуй обстріл, коли вороги скупчуються.",
            "Gebruik het spervuur als vijanden samenklonteren.",
            "Düşmanlar toplandığında baraj ateşini kullan."),
        "cap" to t("Armia", "Army", "Armee", "Ejército", "Armée", "Esercito", "Exército", "Армія", "Leger", "Ordu"),
        "tut_1" to t(
            "Dotknij jednostki na dole, aby ją wyszkolić.",
            "Tap a unit below to train it.",
            "Tippe unten auf eine Einheit, um sie auszubilden.",
            "Toca una unidad abajo para entrenarla.",
            "Touchez une unité en bas pour l'entraîner.",
            "Tocca un'unità in basso per addestrarla.",
            "Toque em uma unidade abaixo para treiná-la.",
            "Торкнися бійця внизу, щоб навчити його.",
            "Tik onderaan op een eenheid om die te trainen.",
            "Eğitmek için aşağıdan bir birime dokun."),
        "tut_2" to t(
            "Zabijaj wrogów → złoto i XP. Pełny pasek XP = awans epoki.",
            "Kill enemies → gold & XP. Full XP bar = new era.",
            "Besiege Feinde → Gold & XP. Voller XP-Balken = neue Epoche.",
            "Derrota enemigos → oro y XP. Barra de XP llena = nueva era.",
            "Battez des ennemis → or et XP. Barre d'XP pleine = nouvelle ère.",
            "Sconfiggi i nemici → oro e XP. Barra XP piena = nuova era.",
            "Derrote inimigos → ouro e XP. Barra de XP cheia = nova era.",
            "Знищуй ворогів → золото й XP. Повна шкала XP = нова епоха.",
            "Versla vijanden → goud & XP. Volle XP-balk = nieuw tijdperk.",
            "Düşmanları yen → altın ve XP. Dolu XP çubuğu = yeni çağ."),
        "tut_3" to t(
            "Przeciągnij pole bitwy, by przesunąć widok.",
            "Drag the battlefield to move the camera.",
            "Zieh das Schlachtfeld, um die Kamera zu bewegen.",
            "Arrastra el campo de batalla para mover la cámara.",
            "Faites glisser le champ de bataille pour déplacer la caméra.",
            "Trascina il campo di battaglia per spostare la visuale.",
            "Arraste o campo de batalha para mover a câmera.",
            "Перетягуй поле бою, щоб рухати камеру.",
            "Sleep over het slagveld om de camera te bewegen.",
            "Kamerayı kaydırmak için savaş alanını sürükle."),

        // --- epoki
        "era_tribal" to t("Plemiona", "Tribal", "Stammeszeit", "Era tribal", "Âge tribal", "Età tribale", "Era tribal", "Доба племен", "Stammentijd", "Kabile Çağı"),
        "era_bronze" to t("Epoka brązu", "Bronze Age", "Bronzezeit", "Edad del Bronce", "Âge du bronze", "Età del bronzo", "Idade do Bronze", "Бронзова доба", "Bronstijd", "Tunç Çağı"),
        "era_feudal" to t("Feudalizm", "Feudal", "Feudalzeit", "Era feudal", "Âge féodal", "Età feudale", "Era feudal", "Феодальна доба", "Feodale tijd", "Feodal Çağ"),
        "era_powder" to t("Epoka prochu", "Gunpowder", "Pulverzeit", "Era de la pólvora", "Âge de la poudre", "Età della polvere", "Era da pólvora", "Доба пороху", "Buskruittijd", "Barut Çağı"),
        "era_steam" to t("Epoka pary", "Steam Age", "Dampfzeit", "Era del vapor", "Âge de la vapeur", "Età del vapore", "Era do vapor", "Доба пари", "Stoomtijd", "Buhar Çağı"),
        "era_steel" to t("Epoka stali", "Steel Age", "Stahlzeit", "Era del acero", "Âge de l'acier", "Età dell'acciaio", "Era do aço", "Доба сталі", "Staaltijd", "Çelik Çağı"),
        "era_silicon" to t("Epoka krzemu", "Silicon Age", "Siliziumzeit", "Era del silicio", "Âge du silicium", "Età del silicio", "Era do silício", "Доба кремнію", "Siliciumtijd", "Silikon Çağı"),
        "era_orbital" to t("Era orbitalna", "Orbital Era", "Orbitalzeit", "Era orbital", "Ère orbitale", "Era orbitale", "Era orbital", "Орбітальна ера", "Orbitaal tijdperk", "Yörünge Çağı"),

        // --- jednostki
        "u_clubman" to t("Pałkarz", "Clubman", "Keulenträger", "Garrotero", "Massier", "Mazziere", "Porreteiro", "Палицяр", "Knotsdrager", "Sopacı"),
        "u_slinger" to t("Procarz", "Slinger", "Schleuderer", "Hondero", "Frondeur", "Frombolière", "Fundeiro", "Пращник", "Slingeraar", "Sapancı"),
        "u_brute" to t("Osiłek", "Brute", "Schläger", "Bruto", "Brute", "Bruto", "Brutamontes", "Здоровань", "Bruut", "Dev"),
        "u_tusker" to t("Kłowiec", "Tusker", "Mammut", "Mamut", "Mammouth", "Mammut", "Mamute", "Мамонт", "Mammoet", "Mamut"),
        "u_spearman" to t("Włócznik", "Spearman", "Speerträger", "Lancero", "Lancier", "Lanciere", "Lanceiro", "Списник", "Speerdrager", "Mızrakçı"),
        "u_javelineer" to t("Oszczepnik", "Javelineer", "Speerwerfer", "Jabalinero", "Javelinier", "Giavellottiere", "Dardeiro", "Метальник", "Speerwerper", "Ciritçi"),
        "u_chariot" to t("Rydwan", "Chariot", "Streitwagen", "Carro de guerra", "Char", "Biga", "Biga", "Колісниця", "Strijdwagen", "Savaş Arabası"),
        "u_warbeast" to t("Bestia wojenna", "War Beast", "Kriegsbestie", "Bestia de guerra", "Bête de guerre", "Bestia da guerra", "Fera de guerra", "Бойовий звір", "Oorlogsbeest", "Savaş Canavarı"),
        "u_footman" to t("Zbrojny", "Footman", "Waffenknecht", "Soldado", "Homme d'armes", "Armigero", "Soldado", "Латник", "Voetknecht", "Yaya Asker"),
        "u_crossbow" to t("Kusznik", "Crossbowman", "Armbrustschütze", "Ballestero", "Arbalétrier", "Balestriere", "Besteiro", "Арбалетник", "Kruisboogschutter", "Arbaletçi"),
        "u_knight" to t("Rycerz", "Knight", "Ritter", "Caballero", "Chevalier", "Cavaliere", "Cavaleiro", "Лицар", "Ridder", "Şövalye"),
        "u_trebuchet" to t("Trebusz", "Trebuchet", "Tribock", "Fundíbulo", "Trébuchet", "Trabucco", "Trabuco", "Требушет", "Blijde", "Mancınık"),
        "u_pikeman" to t("Pikinier", "Pikeman", "Pikenier", "Piquero", "Piquier", "Picchiere", "Piqueiro", "Пікінер", "Piekenier", "Kargıcı"),
        "u_musketeer" to t("Muszkieter", "Musketeer", "Musketier", "Mosquetero", "Mousquetaire", "Moschettiere", "Mosqueteiro", "Мушкетер", "Musketier", "Misketçi"),
        "u_dragoon" to t("Dragon", "Dragoon", "Dragoner", "Dragón", "Dragon", "Dragone", "Dragão", "Драгун", "Dragonder", "Dragon"),
        "u_cannon" to t("Armata", "Cannon", "Kanone", "Cañón", "Canon", "Cannone", "Canhão", "Гармата", "Kanon", "Top"),
        "u_rifleman" to t("Strzelec", "Rifleman", "Schütze", "Fusilero", "Fusilier", "Fuciliere", "Fuzileiro", "Стрілець", "Schutter", "Tüfekçi"),
        "u_sharpshooter" to t("Wyborowy", "Sharpshooter", "Scharfschütze", "Tirador", "Tireur d'élite", "Tiratore scelto", "Atirador de elite", "Снайпер", "Scherpschutter", "Keskin Nişancı"),
        "u_walker" to t("Kroczak parowy", "Steam Walker", "Dampfläufer", "Caminante a vapor", "Marcheur à vapeur", "Camminatore a vapore", "Andador a vapor", "Паровий крокохід", "Stoomloper", "Buharlı Yürüyücü"),
        "u_ironclad" to t("Pancernik lądowy", "Land Ironclad", "Landpanzerschiff", "Acorazado terrestre", "Cuirassé terrestre", "Corazzata terrestre", "Encouraçado terrestre", "Сухопутний броненосець", "Landpantserschip", "Kara Zırhlısı"),
        "u_infantry" to t("Piechur", "Infantry", "Infanterist", "Infante", "Fantassin", "Fante", "Infante", "Піхотинець", "Infanterist", "Piyade"),
        "u_gunner" to t("Karabinier", "Machine Gunner", "MG-Schütze", "Ametrallador", "Mitrailleur", "Mitragliere", "Metralhador", "Кулеметник", "Mitrailleur", "Makineli Er"),
        "u_halftrack" to t("Półgąsienicowy", "Halftrack", "Halbkette", "Semioruga", "Semi-chenillé", "Semicingolato", "Meia-lagarta", "Напівгусеничник", "Halfrups", "Yarı Paletli"),
        "u_heavytank" to t("Ciężki czołg", "Heavy Tank", "Schwerer Panzer", "Tanque pesado", "Char lourd", "Carro pesante", "Tanque pesado", "Важкий танк", "Zware tank", "Ağır Tank"),
        "u_marine" to t("Komandos", "Marine", "Marinesoldat", "Comando", "Commando", "Incursore", "Comando", "Морпіх", "Marinier", "Komando"),
        "u_rocketeer" to t("Rakietnik", "Rocketeer", "Raketenschütze", "Lanzacohetes", "Lance-roquettes", "Lanciarazzi", "Lança-foguetes", "Ракетник", "Raketschutter", "Roketçi"),
        "u_apc" to t("Transporter", "APC", "Transporter", "Blindado", "Blindé", "Blindato", "Blindado", "БТР", "Pantserwagen", "Zırhlı Araç"),
        "u_mech" to t("Mech bojowy", "Battle Mech", "Kampfmech", "Mech de combate", "Mécha de combat", "Mech da battaglia", "Mech de combate", "Бойовий мех", "Gevechtsmech", "Savaş Robotu"),
        "u_plasma" to t("Plazmowiec", "Plasma Trooper", "Plasmasoldat", "Soldado de plasma", "Soldat plasma", "Soldato al plasma", "Soldado de plasma", "Плазмовик", "Plasmasoldaat", "Plazma Askeri"),
        "u_railgunner" to t("Szynowiec", "Railgunner", "Railgun-Schütze", "Cañonero de riel", "Canonnier à rail", "Fuciliere a rotaia", "Atirador de trilho", "Рейковик", "Railkanonnier", "Raylı Topçu"),
        "u_hovertank" to t("Poduszkowiec", "Hover Tank", "Schwebepanzer", "Aerotanque", "Aéroglisseur", "Carro antigravità", "Tanque flutuante", "Ширяючий танк", "Zweeftank", "Uçan Tank"),
        "u_titan" to t("Tytan", "Titan", "Titan", "Titán", "Titan", "Titano", "Titã", "Титан", "Titan", "Titan"),

        // --- wieże
        "t_rock" to t("Miotacz kamieni", "Rock Thrower", "Steinwerfer", "Lanzapiedras", "Lance-rochers", "Lanciapietre", "Lança-pedras", "Каменемет", "Steenwerper", "Taş Atar"),
        "t_ballista" to t("Balista", "Ballista", "Balliste", "Balista", "Baliste", "Balista", "Balista", "Баліста", "Ballista", "Balista"),
        "t_arrow" to t("Wieża łucznicza", "Arrow Tower", "Pfeilturm", "Torre de flechas", "Tour d'archers", "Torre d'arcieri", "Torre de flechas", "Вежа лучників", "Pijltoren", "Ok Kulesi"),
        "t_cannon" to t("Bateria dział", "Cannon Battery", "Kanonenbatterie", "Batería de cañones", "Batterie de canons", "Batteria di cannoni", "Bateria de canhões", "Гарматна батарея", "Kanonnenbatterij", "Top Bataryası"),
        "t_gatling" to t("Kartaczownica", "Gatling", "Gatling", "Gatling", "Gatling", "Gatling", "Gatling", "Картечниця", "Gatling", "Gatling"),
        "t_flak" to t("Działo przeciwlotnicze", "Flak Gun", "Flak", "Cañón antiaéreo", "Canon DCA", "Contraerea", "Antiaéreo", "Зенітка", "Luchtafweer", "Uçaksavar"),
        "t_missile" to t("Wyrzutnia rakiet", "Missile Pod", "Raketenwerfer", "Lanzamisiles", "Lance-missiles", "Lanciamissili", "Lança-mísseis", "Ракетна установка", "Raketwerper", "Füze Rampası"),
        "t_laser" to t("Działo laserowe", "Laser Cannon", "Laserkanone", "Cañón láser", "Canon laser", "Cannone laser", "Canhão laser", "Лазерна гармата", "Laserkanon", "Lazer Topu"),

        // --- ostrzały
        "ab_rocks" to t("Lawina głazów", "Rockslide", "Steinlawine", "Alud de rocas", "Éboulement", "Frana", "Deslizamento", "Каменепад", "Steenlawine", "Kaya Yağmuru"),
        "ab_spears" to t("Grad oszczepów", "Spear Hail", "Speerhagel", "Lluvia de lanzas", "Grêle de lances", "Pioggia di lance", "Chuva de lanças", "Град списів", "Speerregen", "Mızrak Yağmuru"),
        "ab_arrows" to t("Deszcz strzał", "Arrow Rain", "Pfeilregen", "Lluvia de flechas", "Pluie de flèches", "Pioggia di frecce", "Chuva de flechas", "Дощ стріл", "Pijlenregen", "Ok Yağmuru"),
        "ab_cannons" to t("Salwa burtowa", "Broadside", "Breitseite", "Andanada", "Bordée", "Bordata", "Salva de canhões", "Бортовий залп", "Volle laag", "Top Yaylımı"),
        "ab_shells" to t("Nawała artylerii", "Artillery Storm", "Trommelfeuer", "Tormenta artillera", "Pilonnage", "Martellamento", "Barragem de artilharia", "Артилерійський шквал", "Granaatregen", "Topçu Fırtınası"),
        "ab_bombers" to t("Nalot", "Air Raid", "Luftangriff", "Ataque aéreo", "Raid aérien", "Raid aereo", "Ataque aéreo", "Авіаналіт", "Luchtaanval", "Hava Saldırısı"),
        "ab_missiles" to t("Uderzenie rakiet", "Missile Strike", "Raketenschlag", "Ataque de misiles", "Frappe de missiles", "Attacco missilistico", "Ataque de mísseis", "Ракетний удар", "Raketaanval", "Füze Saldırısı"),
        "ab_orbital" to t("Lanca orbitalna", "Orbital Lance", "Orbitallanze", "Lanza orbital", "Lance orbitale", "Lancia orbitale", "Lança orbital", "Орбітальний спис", "Orbitale lans", "Yörünge Mızrağı"),

        // --- doktryny
        "d_economy" to t("Gospodarka", "Economy", "Wirtschaft", "Economía", "Économie", "Economia", "Economia", "Економіка", "Economie", "Ekonomi"),
        "d_economy_d" to t("+25% przychodu złota", "+25% gold income", "+25% Goldeinkommen", "+25% de ingresos de oro", "+25% de revenus en or", "+25% entrate d'oro", "+25% de renda de ouro", "+25% доходу золота", "+25% goudinkomen", "+%25 altın geliri"),
        "d_siege" to t("Oblężenie", "Siege", "Belagerung", "Asedio", "Siège", "Assedio", "Cerco", "Облога", "Beleg", "Kuşatma"),
        "d_siege_d" to t("+30% obrażeń bazom, +20% wieżom", "+30% base damage, +20% turrets", "+30% Schaden an Basen, +20% Türme", "+30% daño a bases, +20% torretas", "+30% dégâts aux bases, +20% tourelles", "+30% danni alle basi, +20% torrette", "+30% dano a bases, +20% torres", "+30% шкоди базам, +20% вежам", "+30% schade aan bases, +20% torens", "+%30 üs hasarı, +%20 kuleler"),
        "d_bulwark" to t("Bastion", "Bulwark", "Bollwerk", "Baluarte", "Rempart", "Baluardo", "Baluarte", "Бастіон", "Bolwerk", "Kale"),
        "d_bulwark_d" to t("+20% HP bazy, +10% HP jednostek", "+20% base HP, +10% unit HP", "+20% Basis-LP, +10% Einheiten-LP", "+20% PV de base, +10% PV de unidades", "+20% PV de base, +10% PV des unités", "+20% PS base, +10% PS unità", "+20% PV da base, +10% PV das unidades", "+20% ОЗ бази, +10% ОЗ бійців", "+20% basis-HP, +10% eenheid-HP", "+%20 üs canı, +%10 birim canı"),
        "d_swift" to t("Mobilność", "Swiftness", "Schnelligkeit", "Rapidez", "Célérité", "Rapidità", "Agilidade", "Мобільність", "Snelheid", "Çeviklik"),
        "d_swift_d" to t("+25% szkolenia, +10% ruchu", "+25% training, +10% speed", "+25% Ausbildung, +10% Tempo", "+25% entrenamiento, +10% velocidad", "+25% entraînement, +10% vitesse", "+25% addestramento, +10% velocità", "+25% treino, +10% velocidade", "+25% навчання, +10% швидкості", "+25% training, +10% snelheid", "+%25 eğitim, +%10 hız"),
        "d_veteran" to t("Weterani", "Veterans", "Veteranen", "Veteranos", "Vétérans", "Veterani", "Veteranos", "Ветерани", "Veteranen", "Gaziler"),
        "d_veteran_d" to t("+30% zdobywanego XP", "+30% XP gained", "+30% erhaltene XP", "+30% de XP obtenida", "+30% d'XP gagnée", "+30% XP ottenuti", "+30% de XP obtido", "+30% отриманого XP", "+30% verdiende XP", "+%30 kazanılan XP"),
        "d_fury" to t("Furia", "Fury", "Raserei", "Furia", "Fureur", "Furia", "Fúria", "Лють", "Woede", "Öfke"),
        "d_fury_d" to t("+12% obrażeń jednostek", "+12% unit damage", "+12% Einheitenschaden", "+12% daño de unidades", "+12% dégâts des unités", "+12% danni delle unità", "+12% dano das unidades", "+12% шкоди бійців", "+12% eenheidsschade", "+%12 birim hasarı"),
        "d_arsenal" to t("Arsenał", "Arsenal", "Arsenal", "Arsenal", "Arsenal", "Arsenale", "Artilharia", "Арсенал", "Arsenaal", "Tophane"),
        "d_arsenal_d" to t("+1 slot wieży, -20% kosztu wież", "+1 turret slot, -20% turret cost", "+1 Turmplatz, -20% Turmkosten", "+1 ranura de torreta, -20% coste de torretas", "+1 emplacement de tourelle, -20% coût des tourelles", "+1 slot torretta, -20% costo torrette", "+1 espaço de torre, -20% custo de torres", "+1 слот вежі, -20% вартості веж", "+1 torenslot, -20% torenkosten", "+1 kule yuvası, -%20 kule maliyeti"),
        "d_tactics" to t("Taktyka", "Tactics", "Taktik", "Táctica", "Tactique", "Tattica", "Tática", "Тактика", "Tactiek", "Taktik"),
        "d_tactics_d" to t("-25% odnowienia zdolności", "-25% ability cooldown", "-25% Abklingzeit", "-25% recarga de habilidades", "-25% de recharge des capacités", "-25% ricarica abilità", "-25% recarga de habilidades", "-25% перезарядки здібностей", "-25% afkoeltijd vaardigheden", "-%25 yetenek bekleme süresi"),
        "d_plunder" to t("Grabież", "Plunder", "Plünderung", "Saqueo", "Pillage", "Saccheggio", "Saque", "Грабіж", "Plundering", "Yağma"),
        "d_plunder_d" to t("+35% złota za zabójstwa", "+35% gold per kill", "+35% Gold pro Abschuss", "+35% oro por baja", "+35% d'or par élimination", "+35% oro per uccisione", "+35% ouro por abate", "+35% золота за вбивство", "+35% goud per kill", "Öldürme başına +%35 altın"),

        // --- modyfikatory
        "m_income" to t("Podwójny przychód", "Double income", "Doppeltes Einkommen", "Ingresos dobles", "Revenus doublés", "Entrate doppie", "Renda dobrada", "Подвійний дохід", "Dubbel inkomen", "Çifte Gelir"),
        "m_fragile" to t("Kruche bazy", "Fragile bases", "Zerbrechliche Basen", "Bases frágiles", "Bases fragiles", "Basi fragili", "Bases frágeis", "Крихкі бази", "Fragiele bases", "Kırılgan Üsler"),
        "m_fast" to t("Szybkie wojska", "Fast troops", "Schnelle Truppen", "Tropas rápidas", "Troupes rapides", "Truppe veloci", "Tropas rápidas", "Швидкі війська", "Snelle troepen", "Hızlı Birlikler"),
        "m_noturrets" to t("Bez wież", "No turrets", "Keine Türme", "Sin torretas", "Sans tourelles", "Niente torrette", "Sem torres", "Без веж", "Geen torens", "Kulesiz"),
        "m_ranged" to t("Horda strzelców", "Ranged horde", "Schützenhorde", "Horda de tiradores", "Horde de tireurs", "Orda di tiratori", "Horda de atiradores", "Орда стрільців", "Schuttershorde", "Nişancı Sürüsü"),
        "m_glass" to t("Szklane działa", "Glass cannons", "Glaskanonen", "Cañones de cristal", "Canons de verre", "Cannoni di vetro", "Canhões de vidro", "Скляні гармати", "Glazen kanonnen", "Cam Toplar"),
        "m_rich" to t("Bogaty start", "Rich start", "Reicher Start", "Inicio rico", "Départ fortuné", "Partenza ricca", "Início rico", "Багатий старт", "Rijke start", "Zengin Başlangıç"),
        "m_slowxp" to t("Wolny rozwój", "Slow progress", "Langsamer Fortschritt", "Progreso lento", "Progression lente", "Progresso lento", "Progresso lento", "Повільний розвиток", "Trage voortgang", "Yavaş Gelişim"),
        "m_armored" to t("Opancerzony wróg", "Armored foe", "Gepanzerter Feind", "Enemigo blindado", "Ennemi blindé", "Nemico corazzato", "Inimigo blindado", "Броньований ворог", "Gepantserde vijand", "Zırhlı Düşman"),
        "m_fog" to t("Mgła wojny", "Fog of war", "Nebel des Krieges", "Niebla de guerra", "Brouillard de guerre", "Nebbia di guerra", "Névoa de guerra", "Туман війни", "Oorlogsmist", "Savaş Sisi"),

        // --- badania
        "r_startGold" to t("Skarbiec", "Treasury", "Schatzkammer", "Tesoro", "Trésor", "Tesoreria", "Tesouro", "Скарбниця", "Schatkist", "Hazine"),
        "r_startGold_d" to t("+40 złota na start", "+40 starting gold", "+40 Startgold", "+40 de oro inicial", "+40 or de départ", "+40 oro iniziale", "+40 de ouro inicial", "+40 золота на старті", "+40 startgoud", "+40 başlangıç altını"),
        "r_income" to t("Handel", "Trade", "Handel", "Comercio", "Commerce", "Commercio", "Comércio", "Торгівля", "Handel", "Ticaret"),
        "r_income_d" to t("+6% przychodu", "+6% income", "+6% Einkommen", "+6% ingresos", "+6% revenus", "+6% entrate", "+6% renda", "+6% доходу", "+6% inkomen", "+%6 gelir"),
        "r_unitHp" to t("Pancerze", "Armor", "Rüstung", "Armadura", "Armures", "Armature", "Armaduras", "Обладунки", "Bepantsering", "Zırh"),
        "r_unitHp_d" to t("+4% HP jednostek", "+4% unit HP", "+4% Einheiten-LP", "+4% PV de unidades", "+4% PV des unités", "+4% PS unità", "+4% PV das unidades", "+4% ОЗ бійців", "+4% eenheid-HP", "+%4 birim canı"),
        "r_unitDmg" to t("Kowalstwo", "Smithing", "Schmiedekunst", "Herrería", "Forge", "Forgiatura", "Ferraria", "Ковальство", "Smeedkunst", "Demircilik"),
        "r_unitDmg_d" to t("+4% obrażeń jednostek", "+4% unit damage", "+4% Einheitenschaden", "+4% daño de unidades", "+4% dégâts des unités", "+4% danni unità", "+4% dano das unidades", "+4% шкоди бійців", "+4% eenheidsschade", "+%4 birim hasarı"),
        "r_baseHp" to t("Fortyfikacje", "Fortifications", "Befestigungen", "Fortificaciones", "Fortifications", "Fortificazioni", "Fortificações", "Фортифікації", "Vestingwerken", "Tahkimat"),
        "r_baseHp_d" to t("+8% HP bazy", "+8% base HP", "+8% Basis-LP", "+8% PV de base", "+8% PV de base", "+8% PS base", "+8% PV da base", "+8% ОЗ бази", "+8% basis-HP", "+%8 üs canı"),
        "r_turretDmg" to t("Balistyka", "Ballistics", "Ballistik", "Balística", "Balistique", "Balistica", "Balística", "Балістика", "Ballistiek", "Balistik"),
        "r_turretDmg_d" to t("+6% obrażeń wież", "+6% turret damage", "+6% Turmschaden", "+6% daño de torretas", "+6% dégâts des tourelles", "+6% danni torrette", "+6% dano das torres", "+6% шкоди веж", "+6% torenschade", "+%6 kule hasarı"),
        "r_train" to t("Musztra", "Drill", "Drill", "Instrucción", "Exercice", "Addestramento", "Treinamento", "Муштра", "Exercitie", "Talim"),
        "r_train_d" to t("+5% szybkości szkolenia", "+5% training speed", "+5% Ausbildungstempo", "+5% velocidad de entrenamiento", "+5% vitesse d'entraînement", "+5% velocità addestramento", "+5% velocidade de treino", "+5% швидкості навчання", "+5% trainingssnelheid", "+%5 eğitim hızı"),
        "r_xp" to t("Kroniki", "Chronicles", "Chroniken", "Crónicas", "Chroniques", "Cronache", "Crônicas", "Літописи", "Kronieken", "Kronikler"),
        "r_xp_d" to t("+5% XP", "+5% XP", "+5% XP", "+5% XP", "+5% XP", "+5% XP", "+5% XP", "+5% XP", "+5% XP", "+%5 XP"),
        "r_cap" to t("Koszary", "Barracks", "Kaserne", "Cuarteles", "Casernes", "Caserma", "Quartel", "Казарми", "Kazerne", "Kışla"),
        "r_cap_d" to t("+1 limit armii", "+1 army cap", "+1 Armeelimit", "+1 límite de ejército", "+1 limite d'armée", "+1 limite esercito", "+1 limite do exército", "+1 ліміт армії", "+1 legerlimiet", "+1 ordu sınırı"),
        "r_cd" to t("Sztab", "Command", "Stab", "Estado Mayor", "État-major", "Stato maggiore", "Comando", "Штаб", "Staf", "Karargâh"),
        "r_cd_d" to t("-5% odnowienia zdolności", "-5% ability cooldown", "-5% Abklingzeit", "-5% recarga de habilidades", "-5% de recharge des capacités", "-5% ricarica abilità", "-5% recarga de habilidades", "-5% перезарядки здібностей", "-5% afkoeltijd vaardigheden", "-%5 yetenek bekleme süresi"),
        "r_slots" to t("Inżynieria", "Engineering", "Technik", "Ingeniería", "Ingénierie", "Ingegneria", "Engenharia", "Інженерія", "Techniek", "Mühendislik"),
        "r_slots_d" to t("+1 slot wieży", "+1 turret slot", "+1 Turmplatz", "+1 ranura de torreta", "+1 emplacement de tourelle", "+1 slot torretta", "+1 espaço de torre", "+1 слот вежі", "+1 torenslot", "+1 kule yuvası"),
        "r_elite" to t("Doktryna elit", "Elite Doctrine", "Elitedoktrin", "Doctrina de élite", "Doctrine d'élite", "Dottrina d'élite", "Doutrina de elite", "Елітна доктрина", "Elitedoctrine", "Seçkin Doktrini"),
        "r_elite_d" to t("Odblokowuje jednostki elitarne", "Unlocks elite units", "Schaltet Elite-Einheiten frei", "Desbloquea unidades de élite", "Débloque les unités d'élite", "Sblocca le unità d'élite", "Desbloqueia unidades de elite", "Відкриває елітних бійців", "Ontgrendelt elite-eenheden", "Seçkin birlikleri açar"),
        "r_rally" to t("Okrzyk bojowy", "War Cry", "Kriegsschrei", "Grito de guerra", "Cri de guerre", "Grido di battaglia", "Grito de guerra", "Бойовий клич", "Strijdkreet", "Savaş Narası"),
        "r_rally_d" to t("Odblokowuje Zryw (+40% tempa)", "Unlocks Rally (+40% tempo)", "Schaltet Ansturm frei (+40% Tempo)", "Desbloquea Ímpetu (+40% de ritmo)", "Débloque Élan (+40% de cadence)", "Sblocca Slancio (+40% ritmo)", "Desbloqueia Ímpeto (+40% de ritmo)", "Відкриває Ривок (+40% темпу)", "Ontgrendelt Stormloop (+40% tempo)", "Atılım'ı açar (+%40 tempo)"),
    )
}
