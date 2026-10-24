#!/usr/bin/env bash
# Optional speed tweaks for any Android TV / Google TV device, over ADB. Marquee does not need them.
#
# Usage: scripts/tune.sh [-s DEVICE] <command> [extra packages...]
#   status   show current animation scales and how YouTube is compiled
#   apply    save current settings, then set animations to 0.5x and compile YouTube for speed
#            (extra packages given after "apply" are compiled for speed too)
#   undo     restore the saved animation scales and reset compiled apps to the system default
#
# DEVICE is an adb serial or a wireless-debugging address like 192.168.1.50:37001. Leave it out
# when only one device is connected. Nothing is uninstalled or disabled.
# The saved settings live in scripts/state/<device>.txt; keep that file until you run undo.
set -euo pipefail

DEVICE=""
if [ "${1:-}" = "-s" ]; then DEVICE=${2:?device after -s}; shift 2; fi
CMD=${1:-}; shift || true

case "$CMD" in status|apply|undo) ;; *) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 1;; esac
command -v adb >/dev/null || { echo "adb not found: install Android platform-tools first"; exit 1; }

if [ -n "$DEVICE" ]; then
  case "$DEVICE" in *:*) adb connect "$DEVICE" >/dev/null || true;; esac
  ADB=(adb -s "$DEVICE")
else
  ADB=(adb)
fi
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "No device (or more than one). Use -s DEVICE; see 'adb devices'."; exit 1; }

sh_() { "${ADB[@]}" shell "$@" | tr -d '\r'; }
SERIAL=$("${ADB[@]}" get-serialno | tr -d '\r')
STATE_DIR="$(cd "$(dirname "$0")" && pwd)/state"
STATE="$STATE_DIR/$(printf %s "$SERIAL" | tr -c 'A-Za-z0-9._-' '_').txt"
SCALES="window_animation_scale transition_animation_scale animator_duration_scale"
YOUTUBE="com.google.android.youtube.tv"

installed() { sh_ pm path "$1" 2>/dev/null | grep -q '^package:'; }
filter() { sh_ dumpsys package "$1" | grep -m1 -o '\[status=[^]]*\]' || echo "(unknown)"; }

case "$CMD" in
status)
  echo "Device: $SERIAL ($(sh_ getprop ro.product.model), Android $(sh_ getprop ro.build.version.release))"
  for k in $SCALES; do echo "  $k = $(sh_ settings get global $k)"; done
  for p in "$YOUTUBE" "$@"; do
    if installed "$p"; then echo "  $p compile $(filter "$p")"; else echo "  $p not installed"; fi
  done
  [ -f "$STATE" ] && echo "Saved state: $STATE" || echo "No saved state for this device."
  ;;
apply)
  if [ -f "$STATE" ]; then
    echo "Keeping the settings saved earlier in $STATE (run undo first to save fresh ones)."
  else
    mkdir -p "$STATE_DIR"
    for k in $SCALES; do echo "scale $k $(sh_ settings get global $k)"; done > "$STATE"
  fi
  for k in $SCALES; do sh_ settings put global $k 0.5; echo "set $k 0.5"; done
  for p in "$YOUTUBE" "$@"; do
    if ! installed "$p"; then echo "skip $p (not installed)"; continue; fi
    echo "compiling $p for speed (can take a minute)..."
    if out=$(sh_ cmd package compile -m speed -f "$p" 2>&1) && ! grep -qiE 'fail|error|exception' <<<"$out"; then
      grep -qx "compiled $p" "$STATE" || echo "compiled $p" >> "$STATE"
      echo "  done"
    else
      echo "  failed: $out"
    fi
  done
  echo "Done. App updates from the Play Store undo the compile step; run apply again after one."
  ;;
undo)
  [ -f "$STATE" ] || { echo "No saved state for $SERIAL; nothing to undo."; exit 0; }
  while read -r kind a b; do
    case "$kind" in
      scale)
        if [ "$b" = null ] || [ -z "$b" ]; then sh_ settings delete global "$a" >/dev/null; echo "cleared $a"
        else sh_ settings put global "$a" "$b"; echo "restored $a = $b"; fi;;
      compiled)
        installed "$a" && { sh_ cmd package compile --reset "$a" >/dev/null; echo "reset $a"; };;
    esac
  done < "$STATE"
  rm -f "$STATE"
  echo "Undone."
  ;;
esac
