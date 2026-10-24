#!/bin/sh
# Builds the release zip, build/marquee-webos-<version>.zip: the .ipk, the helper, install.sh,
# uninstall.sh, README and screenshots, ready to unzip and run on a Mac or Linux computer.
set -e
cd "$(dirname "$0")"
IPK=$(sh build.sh | tail -n 1)
VERSION=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' app/appinfo.json)
NAME=marquee-webos-$VERSION
STAGE=build/release/$NAME
rm -rf build/release
mkdir -p "$STAGE/files" "$STAGE/docs/screenshots"
cp "$IPK" tv/marquee-autostart "$STAGE/files/"
cp install.sh uninstall.sh CHANGELOG.md ../LICENSE "$STAGE/"
sed 's|](../LICENSE)|](LICENSE)|' README.md > "$STAGE/README.md"
cp docs/screenshots/*.png "$STAGE/docs/screenshots/"
(cd build/release && rm -f "../$NAME.zip" && zip -qr "../$NAME.zip" "$NAME")
echo "build/$NAME.zip"
