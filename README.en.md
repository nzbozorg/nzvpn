<p align="left"><a href="./README.md"><kbd>🌐 فارسی — خواندن نسخهٔ فارسی</kbd></a></p>

<p align="center"><img src="./logo/nzvpn-concept.png" width="156" alt="NZVPN logo" /></p>
<h1 align="center">NZVPN</h1>
<p align="center"><strong>A straightforward Android VPN client, powered by Xray and built by NZBOZORG.</strong></p>
<p align="center"><a href="https://github.com/nzbozorg/nzvpn/releases">Releases</a> · <a href="./docs/build.md">Build guide</a> · <a href="./docs/github-file-format.md">Repository config format</a></p>

> **Getting started:** NZVPN is a **client**, not a server provider. Import your own subscription or configuration. To populate the in-app repository list, add a `configs.txt` file to the repository. The Releases download link will become useful once the first official release is published.

## Highlights

- **Xray-powered connections:** Imports `VLESS`, `VMess`, `Trojan`, `Shadowsocks`, `SOCKS`, and `HTTP` links as well as full Xray JSON configurations. After a tunnel starts, the app checks connectivity through the tunnel.
- **Smart connect:** Servers from every folder enter the same race. The **first fresh successful response** wins; cached ping values are not used to pick a winner. Native probes are limited to **six concurrent calls**, with remaining servers queued.
- **Fast per-folder testing:** Servers are checked in order, with up to **one second for each result to appear in the UI**. Responsive servers are shown and sorted by latency as results arrive. An Xray native call may continue after the one-second deadline; its late result is ignored.
- **Practical subscription management:** Import from the clipboard, a file, or a URL; each subscription gets its own folder. Refreshing one folder does not erase its previous servers if fetching fails. Original names and flags are preserved; only servers imported from the repository's subscriptions receive generic display names such as `سرور ۱` (“Server 1”).
- **Persian, right-to-left interface:** A dark copper theme, turquoise/red status colors, NZ folder icons, unframed local flag assets, and touch targets of at least 48dp.
- **Focused settings:** App information, optional support, updates, and sharing in one place, with a separate share action on the home screen.
- **In-app updates:** Checks stable GitHub Releases, downloads the dual-ABI APK, and validates its package name, version, both architectures, and **the same signing certificate as the installed app**. Installation requires user confirmation in Android's installer.
- **Free to use, with no ads or account required.** There is an optional app-sharing action and an entirely optional [coffee contribution](https://coffeebede.com/Amir_NoZohour). ☕

| Item | Value |
|---|---|
| Current source version | **1.0.8** (`versionCode 9`) |
| Android | 8.0+ (`minSdk 26`; `targetSdk 36`) |
| Application ID | `nzvpn.nzbozorg.ir` |
| Release package | **One APK** for `arm64-v8a` and `armeabi-v7a` |
| Stack | Kotlin · Jetpack Compose · Xray / libv2ray |
| Configuration and release repository | `github.com/nzbozorg/nzvpn` |

> **Version 1.0.8 validation:** All 24 JVM tests passed, and the APK's signature and architectures were checked. Connection and update installation have not yet been tested on a physical device for this version.

## Quick start

1. Once the first release is published, download the signed APK from [Releases](https://github.com/nzbozorg/nzvpn/releases). A debug APK is not an update for a signed release build.
2. Install it on Android 8.0 or newer and accept Android's VPN permission prompt when connecting.
3. On first launch, the app tries to read repository configurations. If `configs.txt` has not been added yet, use the **+** button to import your own subscription, server link, or file.
4. Choose a server, or tap the smart-connect option to connect to the **first server that actually responds**.

> You are responsible for using your servers in accordance with the rules that apply where you live. This repository does not bundle VPN servers or accounts with the app.

## Add repository configurations

The app reads `https://github.com/nzbozorg/nzvpn` by default. The simplest setup is a `configs.txt` at the root of the `main` branch, for example:

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
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug -PnzAbis=arm64-v8a -PnzUniversalApk=false
```

To produce the dual-ABI release APK, use `bash tools/build-release.sh` with your signing environment configured as explained in the [build guide](./docs/build.md). **Keep the original signing key if you want to update previous installs**, and never commit keys, passwords, or tokens.

## Enable GitHub-based updates

Putting an APK in the repository is not sufficient. Create a **stable GitHub Release** tagged `v1.0.8` in the public repository, and attach the signed `NZVPN-1.0.8-universal-release.apk` under that exact name. For future versions, keep the `NZVPN-X.Y.Z-universal-release.apk` naming pattern, use a higher version code, and sign with **the same key**.

The app checks the latest stable release under **Settings → App updates**. Until a release exists, there is no update to download. See the [build and publishing guide](./docs/build.md) for the full workflow.

## Privacy and security

- No account, advertising, or behavioral analytics. Imported configurations and sources are stored in the app's private space on the device.
- The app contacts GitHub or URLs you supply to fetch subscriptions and check for releases. Connection tests send network requests through the servers being tested.
- An update APK is downloaded into private cache and checked for package name, version, and signing identity before Android's installer is opened. There is no silent installation.
- Android requests VPN permission when connecting; it may request permission to install from this source when you choose to install an update.

## Contributing and credits

Issues and pull requests are welcome. **Do not post private subscription links, passwords, signing keys, tokens, or sensitive logs in public issues.** Developer guides live in `docs/`, and the project's Android skills remain in `skills/`.

The app logo belongs to **NZBOZORG**. [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) provides the connection core. Flag assets are from [Twemoji](https://github.com/twitter/twemoji) (CC BY 4.0), and the fonts use OFL; see [`app/src/main/assets/licenses/`](./app/src/main/assets/licenses/) for their notices.

**There is no root-level `LICENSE` for this project's code yet.** Before describing its reuse or contribution terms as open source, choose a license and add a `LICENSE` file. Third-party assets and dependencies retain their respective terms.

<p align="center">Made with ❤️ by <strong>Amir NoZohour</strong> · NZBOZORG</p>
