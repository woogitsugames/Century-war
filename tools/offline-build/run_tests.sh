#!/bin/bash
# Uruchamia testy :app (JUnit/Robolectric) z klas zbudowanych przez build_app.sh
# i zasobów z build_res.py. Użycie: run_tests.sh <repo> <klasa testu...>
REPO="$1"; shift
D="${CW_OFFLINE_DIR:-/tmp/cw-offline}"
T="$(cd "$(dirname "$0")" && pwd)"
OUT=$D/out
L=/opt/gradle-8.14.3/lib
STD="$L/kotlin-stdlib-2.0.21.jar"
LIBS=$(ls $D/libs/*.jar | tr '\n' ':')
DEP=$D/robodeps
mkdir -p $DEP $D/cfg/com/android/tools
for j in ~/.m2/repository/org/robolectric/android-all-instrumented/*/*.jar $D/android/android-all-instrumented-*.jar; do
  ln -sf "$j" "$DEP/$(basename "$j")"
done
{
  echo "android_merged_manifest=$D/cfg/AndroidManifest.xml"
  echo "android_merged_assets=$REPO/app/src/main/assets"
  echo "android_resource_apk=$D/res/resources.ap_"
  echo "android_custom_package=com.centurywar"
} > $D/cfg/com/android/tools/test_config.properties
cd "$REPO/app"
java -Xmx2g -XX:+UseG1GC -XX:G1PeriodicGCInterval=2000 --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
  -Drobolectric.offline=true -Drobolectric.dependency.dir=$DEP \
  -cp "$STD:$OUT/apptest:$OUT/app:$OUT/sim:$OUT/stubs:$D/cfg:$REPO/app/src/test/resources:$REPO/sim/src/main/resources:$LIBS:$D/monitor:$D/android/mockable-android-36.jar" \
  org.junit.runner.JUnitCore "$@"
