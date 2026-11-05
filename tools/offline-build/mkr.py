"""Generuje zaślepki com.centurywar.R i BuildConfig (Java) z app/src/main/res,
play_games_ids.properties (generator PGS) i odwołań R.x.y w kodzie app/src."""
import os, re, sys, glob
repo, out = sys.argv[1], sys.argv[2]
res = os.path.join(repo, "app/src/main/res")
types = {}
def add(t, n):
    types.setdefault(t, set()).add(n.replace(".", "_"))
for d in os.listdir(res):
    base = d.split("-")[0]
    for f in os.listdir(os.path.join(res, d)):
        if base == "values":
            txt = open(os.path.join(res, d, f), encoding="utf-8").read()
            for m in re.finditer(r'<(string|color|bool|integer|dimen|style|array|string-array|plurals)\s+name="([^"]+)"', txt):
                t = {"string-array": "array"}.get(m.group(1), m.group(1))
                add(t, m.group(2))
            for m in re.finditer(r'<item\s+type="(\w+)"\s+name="([^"]+)"', txt):
                add(m.group(1), m.group(2))
        else:
            add(base, f.split(".")[0])
props = os.path.join(repo, "docs/store/play_games_ids.properties")
for line in open(props, encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k = line.split("=", 1)[0].strip()
        add("string", "game_services_project_id" if k == "app_id" else k)
add("bool", "play_games_configured")
for f in glob.glob(os.path.join(repo, "app/src/**/*.kt"), recursive=True):
    for m in re.finditer(r'(?<![\w.])R\.(\w+)\.(\w+)', open(f, encoding="utf-8").read()):
        add(m.group(1), m.group(2))
pkg = os.path.join(out, "com/centurywar")
os.makedirs(pkg, exist_ok=True)
lines = ["package com.centurywar;", "public final class R {"]
for ti, t in enumerate(sorted(types)):
    lines.append(f"  public static final class {t} {{")
    for ni, n in enumerate(sorted(types[t])):
        lines.append(f"    public static final int {n} = 0x7f{ti+1:02x}{ni:04x};")
    lines.append("  }")
lines.append("}")
open(os.path.join(pkg, "R.java"), "w").write("\n".join(lines) + "\n")
open(os.path.join(pkg, "BuildConfig.java"), "w").write("""package com.centurywar;
public final class BuildConfig {
  public static final boolean DEBUG = true;
  public static final String APPLICATION_ID = "com.centurywar.game";
  public static final String BUILD_TYPE = "debug";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "0.1.0-dev";
  public static final String CW_SERVER_URL = "";
  public static final String CW_HMAC_KEY = "";
  public static final String CW_PRIVACY_URL = "";
  public static final String PRIVACY_URL = "";
  public static final boolean TESTER_SURVEY = true;
  public static final String TESTER_FEEDBACK = "";
}
""")
print({t: len(v) for t, v in types.items()})
