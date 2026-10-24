# Marquee

A minimal TV home screen. No ads, no content rows, no "recommended for you": just your apps in big
type, on a soft background that takes its colours from the app you're on.

![Marquee](android/docs/screenshots/home-youtube.png)

Marquee comes in two versions that look and work the same way:

| | Runs on | Install | Details |
| --- | --- | --- | --- |
| **Android TV / Google TV** | Android TV 12 or newer: TCL, Sony, Hisense and other Google TVs, Chromecast with Google TV, Nvidia Shield | Over ADB from a computer, no root | [android/](android/README.md) |
| **LG webOS** | LG TVs rooted with Homebrew Channel (tested on webOS 6.5) | `install.sh` over SSH from a Mac or Linux computer | [webos/](webos/README.md) |

On both:

- Up/Down cycles through your apps, OK opens one. Hold OK to **Customize**: choose which apps show,
  and in what order.
- Top right: time, date and network status.
- The Home button opens Marquee, and Marquee opens when the TV turns on. On webOS your HDMI inputs
  get their own column, and a console or Chromecast that turns the TV on keeps it on its input.
- No internet access, no analytics. Everything stays on the TV.

## Downloads

Prebuilt files are on the [Releases](../../releases) page: `marquee-launcher.apk` for Android TV and
`marquee-webos-<version>.zip` for LG. Each folder's README covers installing and building from
source.

## Repository layout

```
android/   Android TV launcher (Java, no Gradle needed): build.sh, docs/INSTALL.md, scripts/
webos/     LG webOS app (ES5 web app) and its root helper: build.sh, package.sh, install.sh
```

GitHub Actions builds both on every push (`.github/workflows/build.yml`).

## License

MIT, see [LICENSE](LICENSE). The Inter font is under the SIL Open Font License.
