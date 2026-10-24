# Installing Marquee

This works on Android TV and Google TV devices running Android TV 12 or newer: smart TVs (TCL,
Sony, Hisense, Philips and others), Chromecast with Google TV, Google TV Streamer, Nvidia Shield
and similar boxes. It is all reversible, and nothing here needs root.

You need:

- The Marquee APK (from the Releases page, or built with `./build.sh`).
- A computer on the same network as the TV, with **ADB** (Android platform-tools):
  - Windows: `winget install Google.PlatformTools`
  - macOS: `brew install android-platform-tools`
  - Linux: `sudo apt install adb` (or your distro's `android-tools` package)

Commands below are typed on the computer. `TV_IP` stands for your TV's address, like `192.168.1.50`.

> **Before you start, read [Warnings](#warnings).** In particular: re-enable anything you disabled
> before a factory reset, and check who owns your Wi-Fi network before disabling setup apps.

## 1. Turn on developer options and wireless debugging

1. On the TV: **Settings > System > About**, scroll to **Android TV OS build** (or **Build**) and
   press OK seven times, until it says you are a developer.
2. Go back to **Settings > System > Developer options**. Turn on **USB debugging**, then
   **Wireless debugging**.

Some devices name or place these slightly differently. If yours has no Wireless debugging, see
[No wireless debugging](#no-wireless-debugging).

## 2. Pair and connect

1. In **Wireless debugging**, choose **Pair device with pairing code**. The TV shows an address with
   a port and a six-digit code. Leave this screen open.
2. On the computer:
   ```sh
   adb pair TV_IP:PAIRING_PORT 123456
   ```
3. Close the pairing screen. The main Wireless debugging screen shows a **different** port. Connect
   to that one:
   ```sh
   adb connect TV_IP:PORT
   adb devices        # should list the TV as "device"
   ```
   If the TV asks whether to allow debugging, choose **Always allow**.

Pairing is needed once. The connect port can change after the TV sleeps or restarts; read the
current one from the Wireless debugging screen and run `adb connect` again.

If more than one device is connected, add `-s TV_IP:PORT` after `adb` in every command below.

## 3. Install Marquee

```sh
adb install -r marquee-launcher.apk
```

Marquee now shows up in the TV's app list. You can open it from there to try it without changing
anything else.

## 4. Make Marquee the home screen

```sh
adb shell cmd package set-home-activity com.marquee.launcher/.MainActivity
```

Press Home on the remote. If Marquee opens, you're done with this step. On many Google TV devices the
stock home screen still answers Home, because the system treats it specially. Step 5 fixes that.

## 5. Turn on the Marquee Home button helper (recommended)

The helper is an accessibility service inside Marquee. It sends Home to Marquee even when the stock
launcher tries to take it, and adds: hold Home for the quick-settings bar, the Config button's bar
on TV remotes that have one, the YouTube and Netflix buttons on Google remotes, and the sleep timer.
See the [README](../README.md#privacy) for exactly what it can see.

**On the TV:** Settings > System > Accessibility > **Marquee Home button** > turn it on.

**Or from the computer.** First see what's already on, so you don't switch off another service:

```sh
adb shell settings get secure enabled_accessibility_services
```

- If it prints `null` or nothing:
  ```sh
  adb shell settings put secure enabled_accessibility_services com.marquee.launcher/.HomeRedirectService
  ```
- If it prints something, keep it and add Marquee after a colon:
  ```sh
  adb shell settings put secure enabled_accessibility_services "WHAT_IT_PRINTED:com.marquee.launcher/.HomeRedirectService"
  ```

Then:

```sh
adb shell settings put secure accessibility_enabled 1
```

> **After every Marquee update or reinstall, check this again.** Android switches an app's
> accessibility service off when the app is reinstalled, and on some devices the master switch
> (`accessibility_enabled`) goes off too.

## 6. Optional: disable the stock home screen

With the helper on, the stock home screen can still flash briefly before Marquee appears, and it keeps
running in the background. Disabling it stops both. It is fully reversible, and nothing is
uninstalled.

Find the home apps on your device:

```sh
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Common ones:

| Package | What it is |
| --- | --- |
| `com.google.android.apps.tv.launcherx` | Google TV home screen |
| `com.google.android.tvlauncher` | Classic Android TV home screen |
| `com.google.android.tungsten.setupwraith` | Google TV setup app (also registers as a home screen) |

Disable the stock launcher first and test Home:

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Only disable the setup app if Home still lands somewhere other than Marquee, and **do the Wi-Fi check
below first**.

What you lose when Google TV's home screen is off: its Apps and Library tabs, the profile switcher,
the Google TV dashboard (Marquee's bar replaces the parts most people use), and the "For you"
rows. Apps, the Play Store and casting keep working.

### Wi-Fi check before disabling a setup app

When an app is disabled, Android deletes the Wi-Fi networks that app saved. Networks you joined
during first-time setup are usually saved by the setup app, so disabling it **drops the TV off
Wi-Fi** (and your ADB connection with it).

Check who saved each network:

```sh
adb shell dumpsys wifi | grep -E 'SSID: |cname='
```

If a network shows `cname=com.google.android.tungsten.setupwraith` (or another setup app), make it
system-owned first: on the TV, go to Settings > Network & Internet, **forget** that network and
**join it again** (you'll need the password). Run the check again; the setup app should no longer
appear next to it. Then disable the setup app:

```sh
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

If you are on Ethernet, or the TV is connected through a network you added in Settings yourself,
this doesn't affect you, but checking takes ten seconds.

## Optional speed tweaks

`scripts/tune.sh` works on any Android TV device and doesn't need Marquee. It runs on macOS, Linux and
Windows (WSL or Git Bash):

```sh
scripts/tune.sh -s TV_IP:PORT status     # what's set now
scripts/tune.sh -s TV_IP:PORT apply      # animations 0.5x, compile YouTube for speed
scripts/tune.sh -s TV_IP:PORT undo       # put both back
```

- **Animations 0.5x** halves the length of the system's window and transition animations, so menus
  and app switches feel snappier. Saved values are restored by `undo`.
- **YouTube compiled for speed** compiles the YouTube app fully ahead of time instead of only its
  "hot" code, which shortens cold launches. It costs some storage. Play Store updates to YouTube
  reset it, so run `apply` again after one. Add more apps by package name:
  `scripts/tune.sh -s TV_IP:PORT apply com.netflix.ninja`.

`apply` saves your original settings in `scripts/state/` on the computer; keep that file until you
run `undo`.

## Undo everything

Order matters: re-enable the stock apps **before** uninstalling Marquee, so the TV always has a home
screen.

```sh
adb shell pm enable com.google.android.tungsten.setupwraith      # if you disabled it
adb shell pm enable com.google.android.apps.tv.launcherx         # or whichever launcher you disabled
adb uninstall com.marquee.launcher
scripts/tune.sh -s TV_IP:PORT undo                               # if you ran the tweaks
```

To check nothing is left disabled: `adb shell pm list packages -d`.

## Warnings

- **Before a factory reset, or before removing and re-adding your Google account,** re-enable the
  stock launcher and the setup app (see [Undo](#undo-everything)). The setup and sign-in flows run
  through them. A factory reset normally clears the disabled state itself, but re-enabling first
  is the safe order and costs nothing.
- **Check Wi-Fi ownership before disabling any setup app** ([how](#wi-fi-check-before-disabling-a-setup-app)).
  This applies to anything else you disable too: if an app saved a network, disabling it deletes it.
- **Re-enable the Home button helper after each Marquee update** ([step 5](#5-turn-on-the-marquee-home-button-helper-recommended)).
- **Updates must be signed with the same key.** If `adb install -r` fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the new APK was signed with a different key. Uninstall Marquee
  first (this resets your app order), then install. If you disabled the stock launcher, re-enable it
  before uninstalling.
- Some TV makers' settings or apps assume their own launcher. If something stops working after
  step 6, re-enable the launcher and see if it comes back.
- Don't grant Marquee extra permissions such as `WRITE_SECURE_SETTINGS`. It doesn't need them.

## Troubleshooting

**Home opens Google TV, then Marquee a moment later.** The helper is doing its job; the stock launcher
is still enabled. Do step 6 to remove the flash.

**Home opens Google TV and stays there.** The helper is off. Check step 5, especially after an update.

**Holding Home or the YouTube button does nothing.** These need the helper (step 5). The YouTube and
Netflix buttons only work on Google-made remotes, and only if those apps are installed and enabled.

**`adb connect` fails or times out.** The port changed. Read the current one on the Wireless
debugging screen. If the TV's IP changed too, reserving it in your router stops that.

**`failed to authenticate` or `unauthorized`.** Pair again (step 2), and accept the prompt on the TV.

### No wireless debugging

Some devices only have **Network debugging** or **ADB debugging** in Developer options. Turn it on
and connect to port 5555 without pairing:

```sh
adb connect TV_IP:5555
```

Accept the prompt on the TV. On boxes with a USB-C or USB-A port, a USB cable to the computer also
works (`adb devices` with no connect step).
