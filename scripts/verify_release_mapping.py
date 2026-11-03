#!/usr/bin/env python3
"""Sprawdza stabilnosc trwalych formatow po R8: mapping, usage i keep."""

import argparse
import re
from pathlib import Path

# Klasy zapisujace JSON i binarne formaty oraz ich nosniki danych. Kodery
# korzystaja z jawnych kluczy/kolejnosci bajtow, ale zmiany nazw tych pol
# blokujemy takze w minifikowanym artefakcie jako dodatkowa ochrone.
REQUIRED_FIELDS = {
    "com.centurywar.meta.Profile": (
        "name", "medals", "levels", "stars", "normalStars", "campaignDifficulty",
        "bestWave", "lastRankedDaily", "tutorialDone", "lang", "playerId",
        "playerSecret", "globalRanking", "achievementCounters", "missionState",
        # A156-10: numer ostatnio rozliczonej bitwy (nagroda dokladnie raz).
        "lastSettledBattle",
    ),
    "com.centurywar.meta.ScoreEntry": ("name", "score", "day", "replay", "bannerId"),
    "com.centurywar.game.BattleSnapshot": ("INSTANCE", "VERSION"),
    "com.centurywar.game.RestoredBattle": ("battle", "rankedDaily", "camX", "battleId"),
    "com.centurywar.game.SnapshotStore": ("file", "seq", "written", "owner"),
    "com.centurywar.game.BattleConfig": (
        "mode", "seed", "aiPower", "startEra", "maxEra", "modifiers",
        "playerBonuses", "stageIndex", "tutorial", "aiPersonality", "variantsEnabled",
        "weather", "weekly", "weeklyCard", "dailyEvent",
    ),
    "com.centurywar.game.Bonuses": (
        "startGold", "incomeMul", "unitHpMul", "unitDmgMul", "baseHpMul",
        "turretDmgMul", "trainMul", "xpMul", "popCap", "abilityCdMul",
        "turretSlots", "eliteUnlocked", "rallyUnlocked",
    ),
    "com.centurywar.game.Replay": ("INSTANCE", "VERSION"),
    "com.centurywar.game.Replay$Command": ("step", "kind", "argument"),
    "com.centurywar.game.Replay$Log": ("steps", "commands", "version", "dailyEvent"),
    "com.centurywar.game.ReplayRecord": ("INSTANCE", "VERSION"),
    "com.centurywar.game.ReplayRecord$Meta": (
        "mode", "stage", "winner", "score", "time", "wave", "dateMillis", "nick",
    ),
    "com.centurywar.game.ReplayRecord$Record": (
        "cfg", "log", "meta", "replayVersion", "fingerprint", "playable",
    ),
}

# Wartosci enumow sa serializowane jako nazwy lub ordinale. Oprocz kontroli
# calej klasy pilnujemy reprezentatywnych stalych przechowywanych w plikach.
REQUIRED_ENUM_FIELDS = {
    "com.centurywar.meta.AchievementMetric": (),
    # Zapisywany po nazwie w missionState profilu.
    "com.centurywar.meta.MissionType": ("BATTLES", "KILLS", "MODIFIER_WINS", "REPLAYS_WATCHED",
                                      "COMMANDER_SUMMONS", "TOP_TIER_WINS"),
    "com.centurywar.game.AiPersonality": (),
    "com.centurywar.game.CampaignDifficulty": ("EASY", "NORMAL", "HARD"),
    "com.centurywar.game.DailyEvent": (),
    "com.centurywar.game.Doctrine": (),
    "com.centurywar.game.EndlessCard": (),
    "com.centurywar.game.EndlessWaveTheme": (),
    "com.centurywar.game.EraArt": (),
    "com.centurywar.game.Mode": ("CAMPAIGN", "SKIRMISH", "ENDLESS", "DAILY"),
    "com.centurywar.game.Modifier": (),
    "com.centurywar.game.Replay$Kind": ("QUEUE", "CANCEL", "EVOLVE", "SUMMON_COMMANDER"),
    "com.centurywar.game.Role": (),
    "com.centurywar.game.Weather": (),
}

REQUIRED = {**REQUIRED_FIELDS, **REQUIRED_ENUM_FIELDS}
CLASS_LINE = re.compile(r"^([^\s#]+) -> ([^\s:]+):\s*$")
FIELD_LINE = re.compile(r"^\s+[\w.$\[\]<>?,]+\s+([\w$]+)\s+->\s+([\w$]+)\s*$")


def parse_mapping(text: str) -> dict[str, tuple[str, dict[str, str]]]:
    result = {}
    current = None
    for line in text.splitlines():
        if line.lstrip().startswith("#"):
            continue
        cls = CLASS_LINE.fullmatch(line)
        if cls:
            current = cls.group(1)
            if current in result:
                raise ValueError(f"Powtorzona klasa w mapping.txt: {current}")
            result[current] = (cls.group(2), {})
            continue
        if not line.startswith((" ", "\t")):
            current = None
            continue
        if current is None:
            continue
        field = FIELD_LINE.fullmatch(line)
        if field:
            original, renamed = field.groups()
            if original in result[current][1]:
                raise ValueError(f"Powtorzone pole w mapping.txt: {current}.{original}")
            result[current][1][original] = renamed
    return result


def removed_by_r8(usage: str) -> set[tuple[str, str | None]]:
    """Odczyt usage.txt; naglowek klasy moze tez zawierac usuniete metody."""
    removed = set()
    owner = None
    has_members = False

    def finish_owner() -> None:
        if owner in REQUIRED and not has_members:
            removed.add((owner, None))

    for line in usage.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line[0].isspace():
            if owner is not None:
                finish_owner()
            owner = line.rstrip().removesuffix(":")
            has_members = False
            continue
        has_members = True
        if owner not in REQUIRED or "(" in line:
            continue
        # np. "    int medals"; wiersze metod zawieraja nawiasy.
        member = line.strip().split()[-1]
        removed.add((owner, member))
    if owner is not None:
        finish_owner()
    return removed


def verify(mapping_text: str, usage_text: str | None = None,
           configuration_text: str | None = None) -> tuple[int, int]:
    mapping = parse_mapping(mapping_text)
    removed = removed_by_r8(usage_text) if usage_text is not None else set()
    errors = []
    checked_fields = 0
    # R8 nie umieszcza w mapping.txt wszystkich niezmienionych pol, nawet przy -keep.
    # Kontrolujemy faktycznie zastosowane reguly i liste usunietych elementow.
    if configuration_text is not None:
        active_rules = {line.strip() for line in configuration_text.splitlines()}
        for name in REQUIRED_FIELDS:
            rule = f"-keep class {name} {{ <fields>; }}"
            if rule not in active_rules:
                errors.append(f"Brak zastosowanej reguly keep dla pol: {name}")
        for name in REQUIRED_ENUM_FIELDS:
            rule = f"-keep enum {name} {{ *; }}"
            if rule not in active_rules:
                errors.append(f"Brak zastosowanej reguly keep dla enum: {name}")
    for original, expected_fields in REQUIRED.items():
        cls = mapping.get(original)
        if cls is None:
            errors.append(f"Brak klasy w mapping.txt: {original}")
            continue
        if cls[0] != original:
            errors.append(f"Przemianowana klasa: {original} -> {cls[0]}")
        if (original, None) in removed:
            errors.append(f"Usunieta klasa w usage.txt: {original}")
        for field in expected_fields:
            checked_fields += 1
            # Brak wpisu oznacza niezmieniona nazwe albo usuniety element.
            # W trybie CI kontrola usage.txt i zastosowanych -keep rozstrzyga to.
            new_name = cls[1].get(field, field)
            if new_name != field:
                errors.append(f"Przemianowane pole: {original}.{field} -> {new_name}")
            if (original, field) in removed:
                errors.append(f"Usuniete pole w usage.txt: {original}.{field}")
    if errors:
        raise ValueError("Kontrola mapowania R8 niezaliczona:\n - " + "\n - ".join(errors))
    return len(REQUIRED), checked_fields


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--usage", type=Path, required=True)
    parser.add_argument("--configuration", type=Path, required=True)
    args = parser.parse_args()
    if not args.mapping.is_file():
        parser.error(f"Brak mapping.txt: {args.mapping}")
    if not args.usage.is_file():
        parser.error(f"Brak usage.txt: {args.usage}")
    if not args.configuration.is_file():
        parser.error(f"Brak configuration.txt: {args.configuration}")
    classes, fields = verify(
        args.mapping.read_text(encoding="utf-8", errors="replace"),
        args.usage.read_text(encoding="utf-8", errors="replace"),
        args.configuration.read_text(encoding="utf-8", errors="replace"),
    )
    print(f"Mapowanie R8 OK: {classes} klas (w tym enumy), {fields} pol: "
          "brak zmian nazw, brak usuniec w usage.txt, obecne reguly keep")


if __name__ == "__main__":
    main()
