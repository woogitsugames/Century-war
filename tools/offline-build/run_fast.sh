#!/bin/bash
# Odpowiednik fastTest: wszystkie klasy *Test z app/src/test poza kategoriami screenshots/balance
# (gradle/test-categories.properties; klasy z wyjątkami metod pominięte w całości).
# Paczki po 25 klas na JVM (jak forkEvery). Użycie: run_fast.sh <repo>
REPO="$1"
D="${CW_OFFLINE_DIR:-/tmp/cw-offline}"
T="$(cd "$(dirname "$0")" && pwd)"
cd $D/out/apptest
find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort \
  | grep -Ev 'ScreenshotTest|ScreenshotGoldenTest|ArtGalleryTest|BackdropsTest|RendererStressTest|StoreGraphicsTest|LowEndScreensPerformanceTest|PrereleasePolish|Balance' \
  | grep -Ev '\.(AiPersonalityProfileTest|EndlessBossTest|Doctrine69BattleSimTest|BattleSimTest)$' > $D/fast_classes.txt
wc -l < $D/fast_classes.txt
rm -f $D/fast_*.log $D/fast_chunk_*
split -l 25 -d $D/fast_classes.txt $D/fast_chunk_
for c in $D/fast_chunk_*; do
  bash $T/run_tests.sh "$REPO" $(cat $c) > $D/fast_$(basename $c).log 2>&1
  echo "$(basename $c): $(grep -E '^OK|^Tests run' $D/fast_$(basename $c).log)"
done
