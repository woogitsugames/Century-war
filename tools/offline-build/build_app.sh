#!/bin/bash
# Kompilacja :sim + :app (main+test) poza Gradle. Użycie: build_app.sh <repo>
set -e
REPO="$1"
D="${CW_OFFLINE_DIR:-/tmp/cw-offline}"
T="$(cd "$(dirname "$0")" && pwd)"
L=/opt/gradle-8.14.3/lib
OUT=$D/out
KCP="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar"
for j in "$L"/trove4j*.jar "$L"/annotations-*.jar "$L"/kotlinx-coroutines-core-jvm-*.jar; do [ -f "$j" ] && KCP="$KCP:$j"; done
STD="$L/kotlin-stdlib-2.0.21.jar"
ANDROID=$D/android/android-all-16-robolectric-13921718.jar
LIBS=$(ls $D/libs/*.jar | tr '\n' ':')
KC() { java -Xmx3g -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -no-reflect -jvm-target 17 "$@"; }
rm -rf "$OUT"; mkdir -p "$OUT"/{sim,stubs,app,apptest}

echo "== zaślepki (gms, R, BuildConfig)"
python3 -I $T/mkstubs.py $D/stubs/gms >/dev/null
python3 -I $T/mkr.py "$REPO" $D/stubs/gen >/dev/null
# Prawdziwe R.java z aapt2 (build_res.py) zastępuje zaślepkę R z mkr.py.
python3 -I $T/build_res.py "$REPO"
cp $D/res/java/com/centurywar/R.java $D/stubs/gen/com/centurywar/R.java
javac -nowarn -d "$OUT/stubs" -cp "$ANDROID" $(find $D/stubs -name '*.java')

echo "== sim main"
find "$REPO/sim/src/main" -name '*.kt' > "$OUT/sim.txt"
KC -cp "$STD" -module-name sim -d "$OUT/sim" @"$OUT/sim.txt"

echo "== app main"
find "$REPO/app/src/main/java" -name '*.kt' > "$OUT/app.txt"
KC -cp "$STD:$ANDROID:$OUT/stubs:$OUT/sim" -module-name app_debug -d "$OUT/app" @"$OUT/app.txt" 2>&1 | tee "$OUT/app.log"
[ "${PIPESTATUS[0]}" = 0 ] || { echo "APP MAIN FAILED"; exit 1; }

echo "== app test"
find "$REPO/app/src/test/java" -name '*.kt' > "$OUT/apptest.txt"
KC -cp "$STD:$ANDROID:$OUT/stubs:$OUT/sim:$OUT/app:$LIBS" -Xfriend-paths="$OUT/app" -module-name app_debug \
  -d "$OUT/apptest" @"$OUT/apptest.txt" 2>&1 | tee "$OUT/apptest.log"
[ "${PIPESTATUS[0]}" = 0 ] || { echo "APP TEST FAILED"; exit 1; }
echo "== OK"
