#!/bin/sh
# Installs Marquee on a rooted LG webOS TV over SSH, sets it to open at power-on and on the Home
# button, and opens it.
#
# Usage: sh install.sh [TV IP] [--no-autostart]
#   Asks for the TV's IP address if you don't give one. SSH asks for the TV's root password twice
#   (Homebrew Channel's default is "alpine").
#   --no-autostart installs only the app: no power-on or Home button takeover.
set -e
TV=""
AUTOSTART=1
for a in "$@"; do
    case "$a" in
    --no-autostart) AUTOSTART=0 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) TV=$a ;;
    esac
done
while [ -z "$TV" ]; do
    printf "TV's IP address (Settings > Network on the TV, e.g. 192.168.1.10): "
    read -r TV
done
case "$TV" in
*[!0-9.]*|*..*|.*|*.) echo "That doesn't look like an IP address: $TV"; exit 1 ;;
esac

HERE="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$HERE/files" ]; then
    # A release zip: the app and the helper are ready in files/.
    DIR="$HERE/files"
else
    # A checkout of the repository: build the app, then stage it with the helper.
    command -v python3 >/dev/null || { echo "Building from source needs python3."; exit 1; }
    echo "Building Marquee..."
    BUILT=$(sh "$HERE/build.sh" | tail -n 1)
    DIR="$HERE/build/stage"
    mkdir -p "$DIR"
    find "$DIR" -name '*.ipk' -exec rm -f {} +
    cp "$HERE/$BUILT" "$HERE/tv/marquee-autostart" "$DIR/"
fi
IPK=$(cd "$DIR" && ls com.marquee.webos_*_all.ipk 2>/dev/null | tail -n 1)
VERSION=${IPK#com.marquee.webos_}
VERSION=${VERSION%_all.ipk}
for f in "$IPK" marquee-autostart; do
    [ -n "$f" ] && [ -f "$DIR/$f" ] || { echo "Missing the app or marquee-autostart in $DIR"; exit 1; }
done

echo "Copying Marquee to the TV at $TV..."
# Leave out macOS file tags (the TV's tar warns about them) where this tar can.
TARX=--no-xattrs
tar --no-xattrs -cf /dev/null -C "$DIR" marquee-autostart 2>/dev/null || TARX=""
COPYFILE_DISABLE=1 tar $TARX -C "$DIR" -cf - "$IPK" marquee-autostart | ssh -o ConnectTimeout=10 root@"$TV" 'mkdir -p /tmp/marquee-inst && tar -C /tmp/marquee-inst -xf -'
echo "Installing..."
ssh -o ConnectTimeout=10 root@"$TV" "AUTOSTART=$AUTOSTART WANT=$VERSION sh -s" <<'REMOTE'
cd /tmp/marquee-inst
APP=/media/developer/apps/usr/palm/applications/com.marquee.webos/appinfo.json
# luna-send -i quits when its input ends, which would cut the install short, so feed it a sleep.
sleep 60 | luna-send -i luna://com.webos.appInstallService/dev/install "{\"id\":\"testing\",\"ipkUrl\":\"/tmp/marquee-inst/$(ls *.ipk)\",\"subscribe\":true}" > /tmp/marquee-inst/install.log 2>&1 &
i=0
while [ $i -lt 60 ]; do
    sleep 1; i=$((i + 1))
    grep -q '"state": *"installed"\|install failed' /tmp/marquee-inst/install.log && break
done
pkill -f 'appInstallService/dev/install' 2>/dev/null
GOT=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$APP" 2>/dev/null)
if [ "$GOT" = "$WANT" ]; then
    echo "Marquee $GOT is installed."
else
    echo "Install failed: the TV has version '${GOT:-none}', expected $WANT. Installer output:"
    tail -c 1500 /tmp/marquee-inst/install.log
    exit 1
fi
if [ "$AUTOSTART" = 1 ]; then
    mkdir -p /var/lib/webosbrew/init.d /var/lib/webosbrew/marquee
    cp marquee-autostart /var/lib/webosbrew/init.d/marquee-autostart
    chmod 755 /var/lib/webosbrew/init.d/marquee-autostart
    # Only this run's lines of the helper's log count.
    LOGSTART=$(($(wc -l < /tmp/marquee-autostart.log 2>/dev/null || echo 0) + 1))
    /var/lib/webosbrew/init.d/marquee-autostart
    echo "Marquee will open when the TV turns on."
    echo "Setting up the Home button (the screen may go black for a few seconds)..."
    i=0
    while [ $i -lt 40 ]; do
        RESULT=$(tail -n +$LOGSTART /tmp/marquee-autostart.log 2>/dev/null | grep "home patch: \(active\|already active\|compositor not running\|LG's file\|switched off\|last attempt\|didn't come out\)" | tail -n 1)
        [ -n "$RESULT" ] && break
        sleep 1; i=$((i + 1))
    done
    case "$RESULT" in
    *"patch: active"*|*"already active"*) PATCHED=1 ;;
    *) PATCHED=0 ;;
    esac
    if [ "$PATCHED" = 1 ]; then
        echo "Home button: opens Marquee."
    else
        echo "Home button: this TV's version isn't supported for the direct Home change, so Marquee"
        echo "replaces LG's home a moment after it opens instead. Details: /tmp/marquee-autostart.log"
    fi
else
    rm -f /var/lib/webosbrew/init.d/marquee-autostart
fi
cd /tmp && rm -rf /tmp/marquee-inst
luna-send -n 1 luna://com.webos.applicationManager/launch '{"id":"com.marquee.webos"}' >/dev/null && echo "Opened Marquee."
REMOTE
