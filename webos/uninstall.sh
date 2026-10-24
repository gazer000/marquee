#!/bin/sh
# Removes Marquee from a rooted LG webOS TV and puts LG's Home button back.
# Usage: sh uninstall.sh [TV IP]   (SSH asks for the TV's root password)
set -e
TV=$1
while [ -z "$TV" ]; do
    printf "TV's IP address: "
    read -r TV
done
ssh -o ConnectTimeout=10 root@"$TV" 'sh -s' <<'REMOTE'
# Stop the background helper and remove it, so nothing comes back at the next boot.
rm -f /var/lib/webosbrew/init.d/marquee-autostart
[ -f /tmp/marquee-autostart.pid ] && kill $(cat /tmp/marquee-autostart.pid) 2>/dev/null
echo 0 > /tmp/marquee-autostart.gen
pkill -f 'tvpower/power/getPowerState' 2>/dev/null
pkill -f 'applicationManager/getForegroundAppInfo' 2>/dev/null
pkill -f 'tail -f /dev/null' 2>/dev/null
# Put LG's Home key script back.
restart=0
for f in /usr/lib/qt5/qml/KeyFilters/systemUi.js /usr/lib/qml/WebOSCompositor/views/StarfishHomeView.qml; do
    if grep -q " $(readlink -f "$f") " /proc/mounts; then umount "$f"; restart=1; fi
done
rm -rf /var/lib/webosbrew/marquee /var/lib/webosbrew/marquee.conf
# Remove the app.
sleep 30 | luna-send -i luna://com.webos.appInstallService/dev/remove '{"id":"com.marquee.webos","subscribe":true}' > /tmp/marquee-remove.log 2>&1 &
i=0
while [ $i -lt 30 ] && [ -d /media/developer/apps/usr/palm/applications/com.marquee.webos ]; do sleep 1; i=$((i + 1)); done
pkill -f 'appInstallService/dev/remove' 2>/dev/null
if [ -d /media/developer/apps/usr/palm/applications/com.marquee.webos ]; then
    echo "Couldn't remove the Marquee app; remove it from the TV's app list instead."
else
    echo "Marquee app removed."
fi
if [ "$restart" = 1 ]; then
    echo "Restoring LG's Home button (the screen goes black for a few seconds)..."
    systemctl restart surface-manager
fi
echo "Done. LG's home screen is back."
REMOTE
