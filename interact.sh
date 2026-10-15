#!/bin/bash
# start app in background; then run xdotool steps passed via stdin lines: "key X", "move x y", "click", "sleep s", "shot file"
cd "$(dirname "$0")"
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 timeout -s KILL 120 .venv/bin/python app/lumen/main.py --new-window "$@" > /tmp/lumen_run.log 2>&1 &
sleep 0.5; pid=$(pgrep -n -f "^.venv/bin/python app/lumen/main.py")
sleep 4
export DISPLAY=:99
WID=$(xdotool search --name "Lumen" | head -1)
xdotool windowactivate --sync $WID 2>/dev/null; xdotool windowfocus $WID 2>/dev/null
while read -r cmd a b; do
  case $cmd in
    key) xdotool key $a ;;
    move) xdotool mousemove $((160+a)) $((120+b)) ;;
    click) xdotool click ${a:-1} ;;
    dclick) xdotool click --repeat 2 --delay 80 1 ;;
    sleep) sleep $a ;;
    shot) import -window root -crop 1280x760+160+120 +repage "$a" ;;
  esac
done
kill -9 $pid 2>/dev/null; wait $pid 2>/dev/null
grep -E "qml:|TypeError|ReferenceError|Traceback|failed" /tmp/lumen_run.log | grep -vE "of null|border-style" | head -10
