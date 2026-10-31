#!/usr/bin/env bash
set -euo pipefail

# Każdy błąd rozruchu musi zachować logcat do diagnozy.
reports="build/release-metrics"
mkdir -p "$reports"
diagnostics() {
  adb logcat -d -s AndroidRuntime:E > "$reports/androidruntime.log" 2>&1 || true
  adb logcat -d -t 600 > "$reports/logcat-last.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$reports/activities.txt" 2>&1 || true
}
trap diagnostics EXIT

adb wait-for-device
# sys.boot_completed nie gwarantuje, że usługa instalatora już działa.
for attempt in 1 2 3 4 5 6; do
  if adb shell service check package 2>/dev/null | grep -q 'Service package: found'; then
    break
  fi
  echo "Usługa package jeszcze niedostępna (próba $attempt)"
  sleep 5
done

if ! adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tee "$reports/install.txt"; then
  echo "Instalacja APK release nie powiodła się"
  exit 1
fi

adb logcat -c || true
adb shell am start -W -n com.centurywar.game/com.centurywar.MainActivity | tee "$reports/start.txt"
if ! grep -q '^Status: ok' "$reports/start.txt"; then
  echo "Activity nie potwierdziła rozruchu"
  exit 1
fi

sleep 8
diagnostics
if grep -q 'FATAL EXCEPTION' "$reports/androidruntime.log"; then
  cat "$reports/androidruntime.log"
  exit 1
fi
if ! adb shell pidof com.centurywar.game | tee "$reports/process.txt"; then
  echo "Proces aplikacji zakończył się po uruchomieniu:"
  tail -n 100 "$reports/logcat-last.txt"
  exit 1
fi
echo "Smoke-test release: OK"
