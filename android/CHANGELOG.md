# Changelog

## 2.0.0 (versionCode 20)
- New app icon and TV banner. Package name `com.marquee.launcher`.
- No other changes from 1.7.1.

## 1.7.1 (versionCode 12)
- The Remotes tile on Marquee's bar only appears when Marquee may open that page. Google TV's
  Remotes & Accessories page is a settings slice that needs WRITE_SECURE_SETTINGS, so on most
  devices the tile is hidden; use All settings > Remotes & Accessories.
- If a shortcut fails to open, Marquee falls back to main Settings instead of crashing.
- Holding Home opens the bar after 0.5 s while the button is still held, instead of on release.

## 1.6 (versionCode 10)
- Fix: OK on a shortcut tile (Wi-Fi, Remotes, All settings) used to just close the bar.
- Remotes opens Google TV's Remotes & Accessories page (falls back to Bluetooth settings).
- Narrower shortcut tiles, so the bar's clock is no longer cut off.
- Sleep timer steps: 10, 20, 30, 45 min, 1h, 1h30, 2h, 3h.
- A long Home press is measured from the key's own timestamps.

## 1.5 (versionCode 8)
- Holding Home for 0.5 s opens the quick-settings bar (the TV maker's bar on TCL TVs, Marquee's own
  bar elsewhere). A short press still opens Marquee.
- Marquee's bar adds Wi-Fi and Remotes shortcuts next to the sleep timer and All settings.
- The YouTube (KEYCODE_BUTTON_3) and Netflix (KEYCODE_BUTTON_4) buttons on Google-made remotes
  (vendor 0x18d1, not gamepads) open those apps. Game controller buttons are left alone.

## 1.4.1 (versionCode 7)
- The remote's Config/notification button opens a quick-settings bar over any app, through the
  Home button helper (accessibility service).

## 1.0 to 1.4
- First releases: big app name list, brand-colour backdrops pre-rendered per app and cross-faded,
  clock and network status, Customize screen, Home button helper for TVs whose stock launcher
  keeps grabbing Home.
