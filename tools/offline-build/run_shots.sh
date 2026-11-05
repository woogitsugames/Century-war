#!/bin/bash
# Kategoria screenshots (gradle/test-categories.properties), jedna JVM na klasę.
# PNG trafiają do app/build/screenshots, różnice wzorców do app/build/screenshots/diffs.
REPO="$1"
D="${CW_OFFLINE_DIR:-/tmp/cw-offline}"
T="$(cd "$(dirname "$0")" && pwd)"
rm -rf "$REPO/app/build/screenshots"
cd $D/out/apptest
find . -name '*.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort \
  | grep -E 'ScreenshotTest|ScreenshotGoldenTest|ArtGalleryTest|BackdropsTest|RendererStressTest|StoreGraphicsTest|LowEndScreensPerformanceTest|PrereleasePolish' \
  | grep -Ev 'Base$|ScreenshotGolden$' > $D/shot_classes.txt
wc -l < $D/shot_classes.txt
mkdir -p $D/shotlogs; rm -f $D/shotlogs/*
for c in $(cat $D/shot_classes.txt); do
  s=$(date +%s)
  bash $T/run_tests.sh "$REPO" "$c" > $D/shotlogs/$c.log 2>&1
  echo "$c: $(grep -E '^OK|^Tests run' $D/shotlogs/$c.log) ($(( $(date +%s) - s ))s)"
done
echo DONE
