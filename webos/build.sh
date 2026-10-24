#!/bin/sh
# Builds build/com.marquee.webos_<version>_all.ipk from app/ with plain ar and tar (no ares-cli needed).
set -e
cd "$(dirname "$0")"
ID=com.marquee.webos
VERSION=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' app/appinfo.json)
OUT=build/${ID}_${VERSION}_all.ipk
rm -rf build/pkg && mkdir -p build/pkg/data/usr/palm/applications/$ID build/pkg/data/usr/palm/packages/$ID build/pkg/control
tar -C app --exclude=preview.html -cf - . | tar -C build/pkg/data/usr/palm/applications/$ID -xf -
cat > build/pkg/data/usr/palm/packages/$ID/packageinfo.json <<JSON
{
  "app": "$ID",
  "id": "$ID",
  "loc_name": "Marquee",
  "package_format_version": 2,
  "vendor": "Marquee contributors",
  "version": "$VERSION"
}
JSON
SIZE=$(du -sk build/pkg/data | cut -f1)
cat > build/pkg/control/control <<CTRL
Package: $ID
Version: $VERSION
Section: misc
Priority: optional
Architecture: all
Installed-Size: $SIZE
Maintainer: Marquee contributors
Description: Marquee, a minimal home screen
webOS-Package-Format-Version: 2
webOS-Packager-Version: x.y.x
CTRL
printf '2.0\n' > build/pkg/debian-binary
tar --format=ustar --owner=0 --group=0 --numeric-owner -C build/pkg/control -czf build/pkg/control.tar.gz control
tar --format=ustar --owner=0 --group=0 --numeric-owner -C build/pkg/data -czf build/pkg/data.tar.gz usr
rm -f "$OUT"
python3 mkar.py "$OUT" build/pkg/debian-binary build/pkg/control.tar.gz build/pkg/data.tar.gz
echo "$OUT"
