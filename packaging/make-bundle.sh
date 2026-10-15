#!/bin/bash
# Assemble build/stage/Lumen from build/dl: embedded Python + trimmed PySide6 + python-mpv + libmpv.
#   build/stage/Lumen/Lumen.exe           launcher (embeds app\python313.dll and runs app\lumen\main.py)
#   build/stage/Lumen/Install Lumen.exe   same launcher; the name switches it into installer mode
#   build/stage/Lumen/app/...             runtime and Lumen's code
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DL="$ROOT/build/dl"
STAGE="$ROOT/build/stage/Lumen"
rm -rf "$STAGE" && mkdir -p "$STAGE/app/Lib/site-packages"
cd "$STAGE/app"

unzip -q "$DL"/python-*-embed-amd64.zip
rm -f python.exe pythonw.exe python.cat _sqlite3.pyd sqlite3.dll winsound.pyd
printf 'python313.zip\r\n.\r\nLib\\site-packages\r\nlumen\r\n' > python313._pth
printf 'app\\python313.zip\r\napp\r\napp\\Lib\\site-packages\r\napp\\lumen\r\n' > ../Lumen._pth

# PySide6 ships ~2500 files (designer, tools, every QML module); keep only what Lumen loads.
TMP=$(mktemp -d)
for w in "$DL"/wheels/*.whl; do unzip -q -o "$w" -d "$TMP"; done
mv "$TMP"/mpv.py "$TMP"/*.dist-info Lib/site-packages/
while IFS= read -r f; do
  mkdir -p "$(dirname "$f")"
  cp "$TMP/${f#Lib/site-packages/}" "$f"
done < "$ROOT/packaging/keep-pyside6.txt"
rm -rf "$TMP"

cp "$DL/libmpv/libmpv-2.dll" .
echo "bundle staged in $STAGE ($(du -sh "$STAGE" | cut -f1))"
