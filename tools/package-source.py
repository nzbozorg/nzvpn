#!/usr/bin/env python3
"""Bundle only NZVPN's actual source and licensed assets (never credentials).

Usage: python3 tools/package-source.py [1.0.9]
Output: releases/NZVPN-<version>-source.zip
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.0.9"
if not re.fullmatch(r"\d+\.\d+\.\d+", VERSION):
    raise SystemExit("Version must look like 1.0.9")

OUTPUT = ROOT / "releases" / f"NZVPN-{VERSION}-source.zip"
ROOT_FILES = (
    ".gitignore", "README.md", "README.en.md", "SOURCE-README.md", "LICENSE", "build.gradle.kts",
    "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "configs.example.txt",
)
FOLDERS = (
    "gradle", "app/src", "docs", "tools", "logo", "skills",
)
APP_FILES = ("app/build.gradle.kts", "app/proguard-rules.pro", "app/.gitignore")

files = [ROOT / name for name in ROOT_FILES + APP_FILES]
for name in FOLDERS:
    files += [p for p in (ROOT / name).rglob("*") if p.is_file()]
files = sorted(set(files), key=lambda p: p.relative_to(ROOT).as_posix())

for file in files:
    rel = file.relative_to(ROOT).as_posix()
    if file.is_symlink() or not file.exists():
        raise SystemExit(f"Missing/unsafe source file: {rel}")
    if any(part in {"build", "dist", ".gradle", "__pycache__", ".cache"} for part in file.parts):
        raise SystemExit(f"Generated file would enter source ZIP: {rel}")
    if file.suffix in (".jks", ".aar", ".apk") or "telegram.config" in rel:
        raise SystemExit(f"Private or binary artifact would enter source ZIP: {rel}")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for file in files:
        z.write(file, arcname=f"NZVPN-{VERSION}/{file.relative_to(ROOT).as_posix()}")

with zipfile.ZipFile(OUTPUT) as z:
    if z.testzip() is not None:
        raise SystemExit("Source archive failed ZIP integrity check")
    print(f"{OUTPUT.relative_to(ROOT)}: {len(z.namelist())} files, {OUTPUT.stat().st_size:,} bytes")
