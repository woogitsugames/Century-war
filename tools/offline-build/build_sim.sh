#!/bin/bash
# Kompilacja :sim main+test poza Gradle i uruchomienie wskazanych testów JUnit.
# Użycie: build_sim.sh <katalog sim> [klasy testów...]  (bez klas = wszystkie *Test)
set -e
SIM="$1"; shift
L=/opt/gradle-8.14.3/lib
OUT="${CW_OFFLINE_DIR:-/tmp/cw-offline}/simout"
KCP="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar"
for j in "$L"/trove4j*.jar "$L"/annotations-*.jar "$L"/kotlinx-coroutines-core-jvm-*.jar; do [ -f "$j" ] && KCP="$KCP:$j"; done
STD="$L/kotlin-stdlib-2.0.21.jar"
JU="$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar"
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/test"
find "$SIM/src/main" -name '*.kt' > "$OUT/main.txt"
java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -no-reflect -cp "$STD" \
  -jvm-target 17 -module-name sim -d "$OUT/main" @"$OUT/main.txt"
find "$SIM/src/test" -name '*.kt' > "$OUT/test.txt"
java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -no-reflect \
  -cp "$STD:$JU:$OUT/main" -Xfriend-paths="$OUT/main" -jvm-target 17 -module-name sim -d "$OUT/test" @"$OUT/test.txt"
if [ "$#" -eq 0 ]; then
  set -- $(cd "$OUT/test" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
fi
cd "$SIM"
java -cp "$STD:$JU:$OUT/main:$OUT/test:$SIM/src/test/resources:$SIM/src/main/resources" org.junit.runner.JUnitCore "$@"
