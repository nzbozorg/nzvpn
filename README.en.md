<p align="left"><a href="./README.md"><kbd>🌐 فارسی — خواندن نسخهٔ فارسی</kbd></a></p>

<p align="center"><img src="./logo/nzvpn-concept.png" width="156" alt="NZVPN logo" /></p>
<h1 align="center">NZVPN</h1>
<p align="center"><strong>A straightforward Android VPN client, powered by Xray and built by NZBOZORG.</strong></p>
<p align="center"><a href="https://github.com/nzbozorg/nzvpn/releases/download/v1.0.9/NZVPN-1.0.9-universal-release.apk"><kbd>⬇️ Download APK 1.0.9</kbd></a> · <a href="https://github.com/nzbozorg/nzvpn/releases/latest">Latest release</a> · <a href="./docs/build.md">Build guide</a> · <a href="./docs/github-file-format.md">Repository config format</a></p>

> **Latest version:** [NZVPN 1.0.9](https://github.com/nzbozorg/nzvpn/releases/tag/v1.0.9); get the APK from its **Assets**. NZVPN is a **client**, not a server provider: it reads the repository's [configuration file](./configs.txt), and you can also import your own subscription or configuration.

## Highlights

- **Xray-powered connections:** Imports `VLESS`, `VMess`, `Trojan`, `Shadowsocks`, `SOCKS`, and `HTTP` links as well as full Xray JSON configurations. After a tunnel starts, the app checks connectivity through the tunnel.
- **Smart connect:** Servers from every folder enter the same race. The **first fresh successful response** wins; cached ping values are not used to pick a winner. Native probes are limited to **six concurrent calls**, with remaining servers queued.
- **Per-folder ping tests and smart connect:** Up to **four servers at a time** get a realistic response window, with results shown progressively and responsive servers sorted by latency. Late native Xray results are ignored after their deadline. A separate per-folder smart-connect action picks the first fresh response from that folder.
- **Practical subscription management:** Import from the clipboard, a file, or a URL; each subscription gets its own folder. Sources refresh on each entry, while a failed fetch preserves existing servers and the selected/active connection. Settings can remove even default repository subscriptions; restoring them requires an explicit repository read. Original names and flags remain intact; only servers in repository subscriptions receive generic display names such as `سرور ۱` (“Server 1”).
- **Persian, right-to-left interface:** A dark copper theme, turquoise/red status colors, NZ folder icons, unframed local flag assets, and touch targets of at least 48dp.
- **Starred copies and custom folder order:** Star a server to keep an independent copy in Saved, even if its original subscription is removed. Folders start collapsed; long-press and drag to rearrange them, with the order saved.
- **Focused settings:** App information, subscription management, optional support, updates, and sharing in one place, with a separate share action on the home screen.
- **In-app updates:** Checks stable GitHub Releases on entry and roughly **every six hours** (subject to Android scheduling), offers a notification and update badges, and supports **pause/resume/cancel** during download. The APK is checked for its package, version, both architectures, and **the same signing certificate as the installed app**. Installation still requires your confirmation.
- **Free to use, with no ads or account required.** There is an optional app-sharing action and an entirely optional [coffee contribution](https://coffeebede.com/Amir_NoZohour). ☕

| Item | Value |
|---|---|
| Current source version | **1.0.9** (`versionCode 10`) |
| Android | 8.0+ (`minSdk 26`; `targetSdk 36`) |
| Application ID | `nzvpn.nzbozorg.ir` |
| Release package | **One APK** for `arm64-v8a` and `armeabi-v7a` |
| Stack | Kotlin · Jetpack Compose · Xray / libv2ray |
| Configuration and release repository | `github.com/nzbozorg/nzvpn` |

> **Version 1.0.9 validation:** All 29 JVM tests passed, and the APK's signing certificate and architectures were checked. Connection quality depends on your servers and network conditions.

## Quick start

1. Download the signed [`NZVPN-1.0.9-universal-release.apk`](https://github.com/nzbozorg/nzvpn/releases/download/v1.0.9/NZVPN-1.0.9-universal-release.apk) from the official release. A debug APK cannot update a signed release build.
2. Install it on Android 8.0 or newer and accept Android's VPN permission prompt when connecting.
3. On first launch, the app reads the repository's [configuration file](./configs.txt). You can also use the **+** button to import your own subscription, server link, or file.
4. Choose a server, or tap the smart-connect option to connect to the **first server that actually responds**.

> You are responsible for using your servers in accordance with the rules that apply where you live. This repository does not bundle VPN servers or accounts with the app.

## Add repository configurations

By default, the app reads [`configs.txt`](./configs.txt) from `https://github.com/nzbozorg/nzvpn`. To maintain or change the public list, edit that file at the root of the `main` branch; each line can follow this example:

```text
# One subscription URL per line
sub: https://example.com/subscription
```

Individual `vless://`, `vmess://`, `trojan://`, and other supported share links work too. The URL above is **only an example**, not a working server. See the [repository file format](./docs/github-file-format.md) for the complete syntax and fallback filenames, and [`configs.example.txt`](./configs.example.txt) for a longer sample. Never commit private subscriptions or credentials to a public repository.

## Build from source

You need **JDK 17**, **Android SDK Platform 36**, and **Build Tools 36.0.0**. The Xray binary dependency and the private release signing key are intentionally not included in the source archive.

```bash
git clone https://github.com/nzbozorg/nzvpn.git
cd nzvpn
bash tools/fetch-core.sh
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug -PnzAbis=arm64-v8a -PnzUniversalApk=false
```

To produce the dual-ABI release APK, use `bash tools/build-release.sh` with your signing environment configured as explained in the [build guide](./docs/build.md). **Keep the original signing key if you want to update previous installs**, and never commit keys, passwords, or tokens.

## GitHub-based updates

The stable [`v1.0.9` release](https://github.com/nzbozorg/nzvpn/releases/tag/v1.0.9) comes with a signed dual-ABI APK. The app checks this repository's latest stable release on entry and periodically; visit **Settings → App updates** to install an available update. Tapping an update notification opens that section.

For future releases, keep the `NZVPN-X.Y.Z-universal-release.apk` naming pattern, increase both the version and `versionCode`, and sign with **the same key**. Simply adding an APK to the repository's ordinary files does not enable in-app updates. Details: [build and publishing guide](./docs/build.md).

## Privacy and security

- No account, advertising, or behavioral analytics. Imported configurations and sources are stored in the app's private space on the device.
- The app contacts GitHub or URLs you supply to fetch subscriptions and check for releases. Connection tests send network requests through the servers being tested.
- An update APK is downloaded into private cache and checked for package name, version, and signing identity before Android's installer is opened. There is no silent installation.
- Android requests VPN permission when connecting; it may request permission to install from this source when you choose to install an update.

## Contributing and credits

Issues and pull requests are welcome. **Do not post private subscription links, passwords, signing keys, tokens, or sensitive logs in public issues.** Developer guides live in `docs/`, and the project's Android skills remain in `skills/`.

[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) provides the connection core. The skills in `skills/` come from [Android Skills](https://github.com/android/skills); their authors retain their respective copyrights.

## License and brand

**NZVPN's original code** is released under the [Apache License 2.0](./LICENSE); the full terms are in `LICENSE`. This license does **not** grant independent use of the **NZVPN/NZBOZORG name, logo, or brand identity**. These belong to NZBOZORG and require separate permission for reuse.

Flag assets come from [Twemoji](https://github.com/twitter/twemoji) under CC BY 4.0, and the fonts use OFL; see [`app/src/main/assets/licenses/`](./app/src/main/assets/licenses/) for their notices. Third-party assets, skills, and dependencies retain their own licenses and copyrights.

<p align="center">Made with ❤️ by <strong>Amir NoZohour</strong> · NZBOZORG</p>
