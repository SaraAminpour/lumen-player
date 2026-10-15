#!/bin/bash
# Build the Windows release on Linux:
#   build/Lumen-Player-<ver>-win64-portable.zip   portable folder (unzip and run Lumen.exe)
#   build/Lumen-Setup-<ver>.exe                   single-file installer
# Needs: mingw-w64, nsis, zip, p7zip, python3 (+pip). First run: packaging/fetch-deps.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION=$(sed -n 's/^VERSION = "\(.*\)"/\1/p' "$ROOT/app/lumen/main.py")
STAGE="$ROOT/build/stage/Lumen"

# launcher (Lumen.exe): tiny GUI exe that loads the bundled Python
cd "$ROOT/launcher"
x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher_res.o
x86_64-w64-mingw32-gcc -O2 -municode -mwindows -static -static-libgcc launcher.c launcher_res.o -o Lumen.exe -lshell32

# runtime bundle (only rebuilt when missing or with --clean)
if [ ! -d "$STAGE/app/Lib" ] || [ "${1:-}" = "--clean" ]; then
  [ -f "$ROOT"/build/dl/libmpv/libmpv-2.dll ] || "$ROOT/packaging/fetch-deps.sh"
  "$ROOT/packaging/make-bundle.sh"
fi

# app code
cd "$STAGE/app"
rm -rf lumen && cp -r "$ROOT/app/lumen" lumen
find lumen -name "__pycache__" -prune -exec rm -rf {} \;
PY="${PYTHON:-$ROOT/.venv/bin/python}"; [ -x "$PY" ] || PY=python3
"$PY" -m compileall -q lumen >/dev/null || true
cp "$ROOT/launcher/Lumen.exe" ../Lumen.exe
cp "$ROOT/launcher/Lumen.exe" "../Install Lumen.exe"
cp "$ROOT/LICENSE" ../LICENSE.txt
cp "$ROOT/THIRD-PARTY-NOTICES.md" ../THIRD-PARTY-NOTICES.md

# portable zip (also kept under its old name for existing deploy scripts)
cd "$ROOT/build/stage"
rm -f ../Lumen-Player-win64.zip "../Lumen-Player-$VERSION-win64-portable.zip"
zip -q -r -9 "../Lumen-Player-$VERSION-win64-portable.zip" Lumen
cp "../Lumen-Player-$VERSION-win64-portable.zip" ../Lumen-Player-win64.zip

# single-file setup
makensis -V2 -DVERSION="$VERSION" -DSTAGE="$STAGE" -DOUT="$ROOT/build/Lumen-Setup-$VERSION.exe" "$ROOT/packaging/lumen-setup.nsi"
ls -la "$ROOT/build/Lumen-Player-$VERSION-win64-portable.zip" "$ROOT/build/Lumen-Setup-$VERSION.exe"
