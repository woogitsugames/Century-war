#!/usr/bin/env bash
# Próba zachowania symulacji po R8 bez urządzenia (tryb JVM, --classfile).
#
# R8 minifikuje moduł sim + pomocnika złotych powtórek z testów + sterownik R8Smoke
# z regułami app/proguard-rules.pro i proguard-android-optimize (jak w APK).
# Sterownik najpierw na klasach niezminifikowanych zapisuje bajty zrzutów v16..VERSION (dziś v20)
# i rekordu powtórki, a potem wersja po R8 musi: odtworzyć 10 złotych wzorców,
# wczytać te bajty (z battleId), zapisać je identycznie i dalej symulować tak samo.
#
# Wymaga: zbudowanych :sim:testClasses i :app:assembleRelease (plik reguł domyślnych AGP),
# JDK 17 oraz R8 z Android SDK (cmdline-tools/latest/lib/r8.jar).
# Użycie: tools/r8-sim-smoke/run.sh [katalog_roboczy]   (domyślnie build/r8-sim-smoke)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${1:-$ROOT/build/r8-sim-smoke}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/root/android-sdk}}"
R8JAR="$SDK/cmdline-tools/latest/lib/r8.jar"
K="$ROOT/sim/build/classes/kotlin"
GOLD="$ROOT/sim/src/test/resources/golden"
DEFAULT_RULES="$(ls "$ROOT"/app/build/intermediates/default_proguard_files/global/proguard-android-optimize.txt-* | head -n 1)"
STD="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" \
  -name 'kotlin-stdlib-2.2.20.jar' | head -n 1)"
JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
for f in "$R8JAR" "$K/main" "$K/test" "$DEFAULT_RULES" "$STD"; do
  test -e "$f" || { echo "Brak: $f (zbuduj :sim:testClasses i :app:assembleRelease)" >&2; exit 2; }
done

rm -rf "$WORK"; mkdir -p "$WORK/out" "$WORK/in" "$WORK/data"
javac -encoding UTF-8 -d "$WORK/out" -cp "$K/main:$K/test:$STD" "$ROOT/tools/r8-sim-smoke/R8Smoke.java"
(cd "$K/main" && jar cf "$WORK/in/sim.jar" .)
(cd "$K/test" && jar cf "$WORK/in/simtest.jar" com/centurywar/game/GoldenFixtures*.class)
(cd "$WORK/out" && jar cf "$WORK/in/driver.jar" .)

# Reguły projektu bez klas Androida (meta/notifications/komponenty manifestu).
grep -v '^-keep class com.centurywar.\(CenturyWarApp\|MainActivity\|meta\.\|notifications\.\)' \
  "$ROOT/app/proguard-rules.pro" | grep -v 'AchievementMetric\|MissionType' > "$WORK/rules.pro"
{
  echo "-include $DEFAULT_RULES"
  echo "-keep class cwr8.R8Smoke { public static void main(java.lang.String[]); }"
  echo "-dontwarn android.**"
  echo "-dontwarn androidx.**"
  echo "-dontwarn org.jetbrains.annotations.**"
  echo "-printmapping $WORK/mapping.txt"
  echo "-printusage $WORK/usage.txt"
} >> "$WORK/rules.pro"
java -cp "$R8JAR" com.android.tools.r8.R8 --classfile --release --output "$WORK/min.jar" \
  --lib "$JDK" --pg-conf "$WORK/rules.pro" \
  "$WORK/in/sim.jar" "$WORK/in/simtest.jar" "$WORK/in/driver.jar" "$STD" > "$WORK/r8.log" 2>&1

echo "--- przed R8 (bajty referencyjne)"
java -cp "$WORK/out:$K/main:$K/test:$STD" cwr8.R8Smoke "$GOLD" "$WORK/data" write | tail -n 1
echo "--- po R8 (odczyt bajtów z wersji niezminifikowanej)"
java -cp "$WORK/min.jar" cwr8.R8Smoke "$GOLD" "$WORK/data" read
echo "Klasy com.centurywar przemianowane przez R8: $(grep -c '^com\.centurywar.* -> [a-z]' "$WORK/mapping.txt" || true)"
