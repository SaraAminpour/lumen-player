#!/bin/bash
# Download the third-party pieces of the Windows bundle into build/dl.
# Pinned to the versions Lumen 1.0 was built and tested with.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DL="$ROOT/build/dl"
PY=3.13.16
PYSIDE=6.12.0
PYMPV=1.0.8
MPV_TAG=20261014
MPV_FILE=mpv-dev-x86_64-20261014-git-ec38361c56.7z   # shinchiro builds rotate; pick a newer tag if this one is gone
mkdir -p "$DL/wheels" "$DL/libmpv"
cd "$DL"

[ -f "python-$PY-embed-amd64.zip" ] ||
  curl -fL -o "python-$PY-embed-amd64.zip" "https://www.python.org/ftp/python/$PY/python-$PY-embed-amd64.zip"

python3 -m pip download --quiet --no-deps --only-binary=:all: --platform win_amd64 --python-version 3.13 \
  -d wheels "pyside6-essentials==$PYSIDE" "shiboken6==$PYSIDE" "python-mpv==$PYMPV"

if [ ! -f libmpv/libmpv-2.dll ]; then
  [ -f "$MPV_FILE" ] ||
    curl -fL -o "$MPV_FILE" "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$MPV_TAG/$MPV_FILE"
  7z x -y -olibmpv "$MPV_FILE" >/dev/null
fi
echo "dependencies ready in $DL"
