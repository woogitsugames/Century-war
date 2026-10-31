#!/usr/bin/env python3
"""Porównuje wykaz licencji z rozstrzygniętymi zależnościami Gradle."""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def modules_from_gradle(path):
    content = path.read_text(encoding="utf-8")
    modules = set()
    for line in content.splitlines():
        if "(c)" in line or "(n)" in line:
            continue
        matched = re.search(
            r"(?:\\---|\+---)\s+([\w.-]+):([\w.-]+):([\w.+-]+)(?:\s+->\s+([\w.+-]+))?",
            line,
        )
        if matched:
            group, artifact, requested, selected = matched.groups()
            modules.add(f"{group}:{artifact}:{selected or requested}")
    if not modules:
        raise ValueError(f"Brak zależności w {path}; nie wolno uznać audytu za wykonany")
    return modules


def modules_from_about():
    content = (ROOT / "app/src/main/java/com/centurywar/screens/AboutScreen.kt").read_text(
        encoding="utf-8"
    )
    modules = re.findall(r'^\s+"([\w.-]+:[\w.-]+:[\w.+-]+)"\),?\s*$', content, re.M)
    if len(modules) != len(set(modules)):
        raise ValueError("Powtórzone moduły w AboutScreen.kt")
    return set(modules)


def modules_from_doc():
    content = (ROOT / "docs/LICENSES.md").read_text(encoding="utf-8")
    app, server = content.split("## 2. Biblioteki backendu", 1)
    server = server.split("## 3. Jak odtworzyć", 1)[0]
    parse = lambda source: set(re.findall(r'^\| `([\w.-]+:[\w.-]+:[\w.+-]+)` \|', source, re.M))
    return parse(app), parse(server)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: python3 scripts/check_license_inventory.py app-tree.txt server-tree.txt")
    try:
        gradle_app = modules_from_gradle(pathlib.Path(sys.argv[1]))
        gradle_server = modules_from_gradle(pathlib.Path(sys.argv[2]))
        about = modules_from_about()
        docs_app, docs_server = modules_from_doc()
    except (OSError, ValueError) as error:
        raise SystemExit(f"Nie można zweryfikować licencji: {error}")
    print(f"releaseRuntimeClasspath: {len(gradle_app)} modułów")
    print(f"server runtimeClasspath: {len(gradle_server)} modułów")
    missing = [
        ("ekran O grze", gradle_app - about),
        ("docs/LICENSES.md (APK)", gradle_app - docs_app),
        ("docs/LICENSES.md (serwer)", gradle_server - docs_server),
    ]
    for title, modules in missing:
        if modules:
            print(f"BRAKUJĄCE LICENCJE: {title}:")
            for module in sorted(modules):
                print(f"  - {module}")
    if any(modules for _, modules in missing):
        raise SystemExit("Audyt niezaliczony; uzupełnij wszystkie moduły przed wydaniem")
    print("OK: każdy moduł release i serwera jest ujęty w inwentarzu")


if __name__ == "__main__":
    main()
