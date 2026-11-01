package com.centurywar.game

import java.io.File
import java.security.MessageDigest

/**
 * Wzorce bitwy odtwarzane tym samym kontrolerem co ranking serwera.
 * Scenariusze i konfiguracje są jawne; wynik, log i pełny snapshot są przypięte w JSON.
 */
object GoldenFixtures {
    data class Scenario(val id: String, val limit: Int, val config: () -> BattleConfig)

    val scenarios = listOf(
        Scenario("campaign-opening", 2400) {
            Campaign.config(Campaign.stages[0], Bonuses(), 91001L)
        },
        Scenario("campaign-night", 3000) {
            Campaign.config(Campaign.stages[28], Bonuses(startGold = 350f), 91002L)
        },
        Scenario("daily-historical", 2200) {
            Daily.config(Daily.RELEASE_DAY - 1L, Bonuses())
        },
        Scenario("daily-event", 2800) {
            Daily.config(Daily.RELEASE_DAY + 3L, Bonuses())
        },
        Scenario("weekly-start-card", 5400) {
            Weekly.config(Weekly.weekNumber(Daily.RELEASE_DAY) + 2L)
        },
        Scenario("endless-with-cards", 11000) {
            BattleConfig(
                mode = Mode.ENDLESS, seed = 91006L, aiPower = 1f,
                startEra = 2, maxEra = 6,
                playerBonuses = Bonuses(
                    startGold = 6000f, incomeMul = 2.5f, unitHpMul = 2.5f,
                    unitDmgMul = 3f, baseHpMul = 3f, popCap = 20, turretSlots = 3,
                ),
            )
        },
        Scenario("skirmish-modifiers", 3600) {
            BattleConfig(
                mode = Mode.SKIRMISH, seed = 91007L, aiPower = 1.25f,
                startEra = 2, maxEra = 5,
                modifiers = linkedSetOf(Modifier.GLASS_CANNON, Modifier.FAST_UNITS),
                weather = Weather.RAIN,
            )
        },
        Scenario("steam-variant-wind", 7200) {
            BattleConfig(
                mode = Mode.SKIRMISH, seed = 91008L, aiPower = 1f,
                startEra = 4, maxEra = 5, weather = Weather.WIND,
                playerBonuses = Bonuses(startGold = 7000f, xpMul = 100f, unitDmgMul = 3f),
            )
        },
        Scenario("silicon-variant", 7200) {
            BattleConfig(
                mode = Mode.SKIRMISH, seed = 91009L, aiPower = 1f,
                startEra = 6, maxEra = 7,
                playerBonuses = Bonuses(startGold = 9000f, xpMul = 100f, unitDmgMul = 3f),
            )
        },
        Scenario("skirmish-night", 3600) {
            BattleConfig(
                mode = Mode.SKIRMISH, seed = 91010L, aiPower = 0.9f,
                startEra = 1, maxEra = 3, weather = Weather.NIGHT,
                modifiers = setOf(Modifier.FOG_OF_WAR),
            )
        },
    )

    /** Stały harmonogram prób rozkazów; zapisujemy wyłącznie zaakceptowane komendy. */
    fun render(scenario: Scenario): String {
        val cfg = scenario.config()
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        while (!battle.over && battle.stepCount < scenario.limit) {
            when {
                battle.pendingVariant != null -> check(controller.variant(1))
                battle.pendingChoice != null -> check(controller.doctrine(0))
                battle.pendingCards != null -> check(controller.card(1))
                else -> {
                    val step = battle.stepCount
                    if (step == 0) {
                        controller.queue(Role.MELEE)
                        controller.buyTurret(0)
                    }
                    if (step % 160 == 25) controller.queue(Role.MELEE)
                    if (step % 290 == 80) controller.queue(Role.RANGED)
                    if (step % 1700 == 1300) controller.queue(Role.HEAVY)
                    if (step % 700 == 200) controller.evolve()
                    if (step % 1800 == 1000) controller.upgradeUnit(Role.MELEE)
                    if (step % 1400 == 700) controller.buyTurret(0)
                    if (step % 1900 == 1000) controller.upgradeTurret(0)
                    if (step % 1200 == 900) controller.barrage()
                    if (step % 2200 == 1200) controller.rally()
                }
            }
            // Awans może otworzyć kolejny wybór w tym samym kroku.
            if (battle.pendingVariant != null) check(controller.variant(1))
            if (battle.pendingChoice != null) check(controller.doctrine(0))
            if (battle.pendingCards != null) check(controller.card(1))
            val before = battle.stepCount
            battle.step()
            check(battle.stepCount == before + 1) {
                "Symulacja zawisła w kroku ${battle.stepCount}: ${scenario.id}"
            }
            battle.fx.count = 0
        }
        if (!battle.over) check(controller.surrender())
        battle.fx.count = 0

        val log = controller.log()
        check(log.steps <= scenario.limit)
        val encoded = Replay.encode(log)
        val verified = Replay.verify(cfg, encoded)
        check(verified.score == battle.score() && verified.winner == battle.winner &&
            verified.wave == battle.wave && verified.steps == log.steps &&
            verified.kills == battle.player.kills && verified.enemyKills == battle.enemy.kills &&
            verified.time == battle.time) {
            "Weryfikator serwera odtworzył inny wynik: ${scenario.id}"
        }
        val playback = ReplayPlayer(cfg, log)
        playback.advance(log.steps + 1)
        check(playback.finished && !playback.broken) {
            "Nie udało się odtworzyć stanu końcowego: ${scenario.id}"
        }
        val snapshot = BattleSnapshot.encode(battle)
        val playedSnapshot = BattleSnapshot.encode(playback.battle)
        check(snapshot.contentEquals(playedSnapshot)) {
            "Odtworzony stan różni się od zapisanego: ${scenario.id}"
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(snapshot)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        return buildString {
            appendLine("{")
            appendLine("  \"id\": \"${scenario.id}\",")
            appendLine("  \"config\": ${configJson(cfg)},")
            appendLine("  \"replayVersion\": ${Replay.VERSION},")
            appendLine("  \"replay\": \"${encoded}\",")
            appendLine("  \"commands\": [")
            for ((index, command) in log.commands.withIndex()) {
                append("    {\"step\":${command.step},\"kind\":\"${command.kind}\",\"argument\":${command.argument}}")
                appendLine(if (index == log.commands.lastIndex) "" else ",")
            }
            appendLine("  ],")
            appendLine(
                "  \"expected\": {\"score\":${verified.score},\"winner\":${verified.winner}," +
                    "\"wave\":${verified.wave},\"steps\":${verified.steps}," +
                    "\"kills\":${verified.kills},\"enemyKills\":${verified.enemyKills}," +
                    "\"timeBits\":${verified.time.toRawBits()},\"snapshotSha256\":\"${hash}\"}"
            )
            appendLine("}")
        }
    }

    private fun quote(value: String) = "\"${value}\""
    private fun nullable(value: Enum<*>?) = value?.let { quote(it.name) } ?: "null"

    /** Kanoniczny JSON obejmuje wszystkie pola konfiguracji i bonusów. */
    private fun configJson(cfg: BattleConfig): String {
        val b = cfg.playerBonuses
        val bonuses = listOf(
            "\"startGold\":${b.startGold}", "\"incomeMul\":${b.incomeMul}",
            "\"unitHpMul\":${b.unitHpMul}", "\"unitDmgMul\":${b.unitDmgMul}",
            "\"baseHpMul\":${b.baseHpMul}", "\"turretDmgMul\":${b.turretDmgMul}",
            "\"trainMul\":${b.trainMul}", "\"xpMul\":${b.xpMul}",
            "\"popCap\":${b.popCap}", "\"abilityCdMul\":${b.abilityCdMul}",
            "\"turretSlots\":${b.turretSlots}", "\"eliteUnlocked\":${b.eliteUnlocked}",
            "\"rallyUnlocked\":${b.rallyUnlocked}",
        ).joinToString(",")
        val modifiers = cfg.modifiers.sortedBy { it.ordinal }.joinToString(",") { quote(it.name) }
        return listOf(
            "\"mode\":${quote(cfg.mode.name)}", "\"seed\":${cfg.seed}",
            "\"aiPower\":${cfg.aiPower}", "\"startEra\":${cfg.startEra}",
            "\"maxEra\":${cfg.maxEra}", "\"modifiers\":[${modifiers}]",
            "\"bonuses\":{${bonuses}}", "\"stageIndex\":${cfg.stageIndex}",
            "\"tutorial\":${cfg.tutorial}", "\"aiPersonality\":${nullable(cfg.aiPersonality)}",
            "\"variantsEnabled\":${cfg.variantsEnabled}", "\"weather\":${quote(cfg.weather.name)}",
            "\"weekly\":${cfg.weekly}", "\"weeklyCard\":${nullable(cfg.weeklyCard)}",
            "\"dailyEvent\":${nullable(cfg.dailyEvent)}",
        ).joinToString(",", "{", "}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.contentEquals(arrayOf("--regenerate"))) {
            "Aktualizacja wzorców tylko przez zadanie regenerateGolden."
        }
        val directory = File("src/test/resources/golden")
        check(directory.isDirectory || directory.mkdirs())
        val names = scenarios.map { it.id }.toSet()
        directory.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in names }
            ?.forEach { check(it.delete()) }
        scenarios.forEach { scenario ->
            val path = File(directory, "${scenario.id}.json")
            path.writeText(render(scenario), Charsets.UTF_8)
            println("Zapisano ${path.path}")
        }
    }
}
