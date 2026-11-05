"""Zasoby :app poza AGP: manifest jak po scaleniu (debug), games_ids.xml (debug, bez ID),
aapt2 compile+link -> resources.ap_ i prawdziwe R.java. Użycie: build_res.py <repo>"""
import os, re, subprocess, sys, shutil
repo = sys.argv[1]
D = os.environ.get("CW_OFFLINE_DIR", "/tmp/cw-offline")
A = D + "/apt/root"
env = dict(os.environ, LD_LIBRARY_PATH=f"{A}/usr/lib/x86_64-linux-gnu/android:{A}/usr/lib/x86_64-linux-gnu")
AAPT2 = A + "/usr/lib/android-sdk/build-tools/debian/aapt2"
ANDROID = os.environ.get("CW_AAPT_ANDROID", D + "/android/android-all-14-robolectric-10818077.jar")
R = D + "/res"
shutil.rmtree(R, ignore_errors=True)
os.makedirs(R + "/gen/values")
os.makedirs(D + "/cfg", exist_ok=True)

# Manifest: package z namespace, ${applicationId}, usunięcie węzłów tools:node="remove".
m = open(repo + "/app/src/main/AndroidManifest.xml", encoding="utf-8").read()
m = m.replace("<manifest ", '<manifest package="com.centurywar" ', 1)
m = m.replace("${applicationId}", "com.centurywar.game")
m = re.sub(r'<provider\b[^>]*tools:node="remove"\s*/>', "", m, flags=re.S)
open(D + "/cfg/AndroidManifest.xml", "w", encoding="utf-8").write(m)

# games_ids.xml jak PlayGamesIdsGenerator.render(..., release=false) przy REPLACE_ME.
keys = []
for line in open(repo + "/docs/store/play_games_ids.properties", encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        keys.append(line.split("=", 1)[0].strip())
x = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
for k in keys:
    name = "game_services_project_id" if k == "app_id" else k
    x.append(f'    <string name="{name}" translatable="false">REPLACE_ME</string>')
x += ['    <bool name="play_games_configured">false</bool>', "</resources>"]
open(R + "/gen/values/games_ids.xml", "w").write("\n".join(x) + "\n")

def run(*a):
    r = subprocess.run(a, env=env, capture_output=True, text=True)
    if r.returncode:
        print(r.stdout, r.stderr); sys.exit(1)
    if r.stderr.strip(): print(r.stderr.strip())
run(AAPT2, "compile", "--dir", repo + "/app/src/main/res", "-o", R + "/main.zip")
run(AAPT2, "compile", "--dir", R + "/gen", "-o", R + "/gen.zip")
os.makedirs(R + "/java")
# aapt2 2.19 (Ubuntu, wersja z Androida 14) nie czyta arsc API 35/36: baza linkowania to API 34.
run(AAPT2, "link", "-I", ANDROID, "--manifest", D + "/cfg/AndroidManifest.xml",
    "--min-sdk-version", "26", "--target-sdk-version", "36",
    "--version-code", "1", "--version-name", "0.1.0-dev", "--rename-manifest-package", "com.centurywar.game",
    "--custom-package", "com.centurywar", "--auto-add-overlay",
    "-A", repo + "/app/src/main/assets", "-0", "ogg",
    "--java", R + "/java", "-o", R + "/resources.ap_", R + "/main.zip", R + "/gen.zip")
print("res OK", os.path.getsize(R + "/resources.ap_"))
