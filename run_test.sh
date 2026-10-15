#!/bin/bash
# usage: run_test.sh out.png delay_ms [args...]  — captures the Xvfb screen (includes popups)
out=$1; delay=$2; shift 2
cd "$(dirname "$0")"
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 timeout -s KILL 60 .venv/bin/python app/lumen/main.py --new-window "$@" > /tmp/lumen_run.log 2>&1 &
sleep 0.5; pid=$(pgrep -n -f "^.venv/bin/python app/lumen/main.py")
sleep $(python3 -c "print($delay/1000)")
DISPLAY=:99 import -window root -crop 1280x760+160+120 +repage "$out"
kill -9 $pid 2>/dev/null; wait $pid 2>/dev/null
grep -E "qml:|TypeError|ReferenceError|unavailable|Traceback|Error:" /tmp/lumen_run.log | grep -v "of null" | head -8
echo "shot $out"
