# Changelog (LG webOS)

## 2.0.1
- Fix: the app list could come up empty (only Settings and Customize) and lose its saved order.
  The TV lists no apps while it is still starting up, and Marquee saved that empty list. It now
  waits and asks again, and apps missing from one listing keep their place in the order.
- Fix: the Home key change wasn't detected on TVs where `/usr/lib/qt5/qml` is a link to
  `/usr/lib/qml`, so a reinstall could leave an older change in place. The helper now replaces it.

## 2.0.0
- New app icon. App id `com.marquee.webos`.

## 1.3.1
- The TV stays on an HDMI input when a device (a console or Chromecast) turns it on over HDMI-CEC,
  instead of Marquee opening over it.

## 1.3.0
- The Home button opens Marquee directly, with no flash of LG's home, through a patched copy of
  LG's Home key script laid over the original (with safeguards; see the README).

## 1.2.0
- Customize > Behaviour: switches for "Home button opens Marquee" and "Open Marquee when the TV
  turns on".

## 1.1.1
- Fix: reopening Marquee from LG's home did nothing.

## 1.1.0
- HDMI inputs in their own column on the right, named after the connected device.
- Opens when the TV boots or wakes from standby.

## 1.0.1
- First webOS version, ported from the Android TV launcher.
