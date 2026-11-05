#!/bin/bash
# Pobiera i przygotowuje wszystko, czego potrzebują build_app.sh i run_tests.sh.
# Katalog roboczy: $CW_OFFLINE_DIR (domyślnie /tmp/cw-offline). Nic nie trafia do repo.
# Źródła: Maven Central (mirror GCS), archive.ubuntu.com (apt-get download). Bez dl.google.com.
set -e
D="${CW_OFFLINE_DIR:-/tmp/cw-offline}"
T="$(cd "$(dirname "$0")" && pwd)"
MIRROR="${CW_MAVEN_MIRROR:-https://maven-central.storage-download.googleapis.com/maven2}"
mkdir -p "$D/android" "$D/libs"

echo "== robolectric 4.16 + junit 4.13.2 (Maven Central, bez androidx.test z Google Maven)"
sed "s|https://maven-central.storage-download.googleapis.com/maven2/|$MIRROR/|" "$T/settings.xml" > "$D/settings.xml"
mvn -q -s "$D/settings.xml" -f "$T/pom.xml" dependency:copy-dependencies -DoutputDirectory="$D/libs"

echo "== obrazy android-all (sha1 sprawdzane)"
fetch() { # <artifactId> <wersja>
  local f="$1-$2.jar" u="$MIRROR/org/robolectric/$1/$2/$1-$2.jar"
  if [ ! -f "$D/android/$f" ]; then curl -sSf -o "$D/android/$f" "$u"; fi
  echo "$(curl -sSf "$u.sha1")  $D/android/$f" | sha1sum -c --quiet
}
fetch android-all 16-robolectric-13921718              # kompilacja (API 36 = compileSdk)
fetch android-all 14-robolectric-10818077              # baza linkowania aapt2 2.19 (czyta arsc do API 34)
fetch android-all-instrumented 15-robolectric-13954326-i7   # @Config(sdk = [35])
fetch android-all-instrumented 10-robolectric-5803371-i7    # @Config(sdk = [29])
fetch android-all-instrumented 9-robolectric-4913185-2-i7   # @Config(sdk = [28])
fetch android-all-instrumented 16-robolectric-13921718-i7   # domyślne SDK (targetSdk 36)

echo "== aapt2 z pakietu Ubuntu 'aapt' (rozpakowany lokalnie, bez instalacji)"
mkdir -p "$D/apt" && cd "$D/apt"
apt-get download aapt android-libaapt android-libandroidfw android-libbacktrace android-libbase \
  android-libcutils android-liblog android-libutils android-libziparchive libprotobuf32t64 >/dev/null
rm -rf root && mkdir root
for f in *.deb; do dpkg -x "$f" root; done

echo "== zamienniki androidx.test:monitor"
rm -rf "$D/monitor-src" "$D/monitor" && mkdir -p "$D/monitor"
python3 -I "$T/mkmonitor.py" "$D/monitor-src" >/dev/null
javac -nowarn -d "$D/monitor" -cp "$D/android/android-all-16-robolectric-13921718.jar" $(find "$D/monitor-src" -name '*.java')

echo "== mockable android.jar (jak AGP, returnDefaultValues=true)"
rm -rf "$D/mockgen" && mkdir -p "$D/mockgen"
javac -nowarn -cp "$D/libs/asm-9.8.jar" -d "$D/mockgen" "$T/MockableJar.java"
java -cp "$D/mockgen:$D/libs/asm-9.8.jar" MockableJar \
  "$D/android/android-all-16-robolectric-13921718.jar" "$D/android/mockable-android-36.jar"
echo "== gotowe: $D"
