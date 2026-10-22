# CleanFrame

A quieter home screen for **Google TV / Android TV** and **Samsung The Frame (Tizen)**. Your apps, a photograph, the time and optional date, with an interface made for the TV remote.

![CleanFrame browser preview using the bundled Manrope font](docs/images/cleanframe-dock.jpg)

Originally created by [nthnclrk](https://github.com/nthnclrk/CleanFrame) for Samsung The Frame (Tizen), this fork brings CleanFrame natively to **Google TV and Android TV** along with new clock, date, and launcher enhancements that work out of the box on any television.

---

## What's New in This Release

- **Native Google TV & Android TV App (`android/`)**:
  - Hardware-accelerated fullscreen WebView wrapper (`com.cleanframe.tv`) with zero-config `1920×1080` viewport scaling across 1080p and 4K TVs.
  - **Automatic First-Launch App Discovery**: Queries Android's `PackageManager` for all installed TV (`LEANBACK_LAUNCHER`) and standard (`LAUNCHER`) apps. On first launch, CleanFrame automatically populates your Home bar with the apps installed on your TV.
  - **Hybrid Icon Rendering**: Uses crisp monochrome vector SVGs for popular streaming apps (YouTube, Netflix, Disney+, Prime Video, Apple TV, Stremio, Plex, YouTube Music, Spotify, Max, Tubi, Apple Music, Downloader) and automatically extracts native app icons from Android `PackageManager` for any other installed app.
  - **Full Remote D-Pad & Back Key Bridge**: Native Android key interception translates D-pad navigation, Center/Enter, and Back/Escape directly to CleanFrame's spatial navigation engine.
  - **Zero Background Overhead**: Automatically suspends WebView rendering and clock timers when another app is in the foreground (`onStop` / `visibilitychange`).
- **12-Hour / 24-Hour Time Format & Optional Date Display**:
  - New **Clock & date** section under **Settings → Appearance → Customize**.
  - Switch between **12-hour** (`1:45`) and **24-hour** (`13:45`) time formats.
  - Toggle **Date** (`Off` / `On`) to display the formatted date (`Wed, Oct 22`) alongside the clock on the Home screen and in Art Mode.
- **Safe Default Launcher / Home Button & Screen-Wake Service (Android 14 / Google TV Compatible)**:
  - Includes an optional `HomeAccessibilityService` and `BootReceiver` that intercepts the physical **Home** button (`KEYCODE_HOME`), TV boot (`BOOT_COMPLETED`), and wake from sleep/screensaver (`ACTION_SCREEN_ON`, `ACTION_DREAMING_STOPPED`) to open CleanFrame immediately.
  - **No stock launcher disabling required**: Keeps Google TV Home (`com.google.android.apps.tv.launcherx`) and Android TV Home (`com.google.android.tvlauncher`) 100% intact and accessible anytime via **Settings → Open Google TV Home**.
- **Fullscreen Art Mode & 12-App Home Bar**:
  - Selecting the **Art** button on the Home bar transitions CleanFrame into a pure fullscreen photograph + clock/date display (press **Back** or **Enter/Select** on the remote to restore the dock/list).
  - Expanded Home bar capacity up to **12 apps**.

---

## Install on Google TV / Android TV

You can install the prebuilt `cleanframe.apk` from the [**Releases**](../../releases) page onto any Chromecast with Google TV, Google TV Streamer, Android TV box, or smart TV running Android 8.0+ (API 26+).

### 1. Enable Wireless Debugging (ADB) on Your TV

1. On your TV, open **Settings → System → About**.
2. Scroll down to **Android TV OS build** and press **Select** 7 times until *"You are now a developer!"* appears.
3. Go back to **Settings → System → Developer options**.
4. Enable **USB debugging** (or **Wireless debugging** on Android 12/14+).
5. Note your TV's IP address (and port if using Wireless Debugging).

### 2. Connect & Install the APK

From your computer with `adb` installed:

```sh
# Connect to your TV over Wi-Fi
adb connect <TV_IP_ADDRESS>:<PORT>

# Install CleanFrame
adb install -r cleanframe.apk

# Launch CleanFrame
adb shell am start -n com.cleanframe.tv/.MainActivity
```

*(Alternatively, you can sideload `cleanframe.apk` directly on the TV using the **Downloader** app or a USB drive.)*

### 3. Optional: Make CleanFrame Open on Home Button & TV Wake

On modern Google TV / Android TV (especially Android 12–14), disabling the stock Google TV launcher can break system Wi-Fi settings or account switching. Instead, CleanFrame includes a lightweight Accessibility Service that safely intercepts the remote's **Home** button and TV wake events while leaving the stock launcher enabled.

You can enable it in **Settings → System → Accessibility → CleanFrame Home Service** on your TV, or enable it instantly via ADB:

```sh
# Enable CleanFrame Home & Wake Service
adb shell settings put secure enabled_accessibility_services com.cleanframe.tv/com.cleanframe.tv.HomeAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Allow CleanFrame to launch from the background when waking or pressing Home
adb shell appops set com.cleanframe.tv SYSTEM_ALERT_WINDOW allow
```

To revert to the normal Google TV Home button behavior at any time:

```sh
adb shell settings delete secure enabled_accessibility_services
```

---

## Build the Android APK from Source

Requirements:
- Android SDK Platform 34 (`platforms/android-34/android.jar`) and Build Tools `34.0.0` (`aapt2`, `d8`, `zipalign`, `apksigner`)
- Java JDK (`javac`, `keytool`, `jar`)

```sh
# Set ANDROID_SDK_ROOT if not installed at /usr/lib/android-sdk
export ANDROID_SDK_ROOT=/path/to/android-sdk

# Build the signed APK (automatically bundles ./app into the APK assets)
./android/build.sh
```

The compiled and signed APK will be output to `android/out/cleanframe.apk`.

---

## Try the Interface in a Browser

You need Node.js 22+ and Python 3.10+ to run the local browser preview:

```sh
git clone https://github.com/mwatney-mars/CleanFrame.git
cd CleanFrame
npm ci
npm run dev:public
```

Open [http://localhost:4795/home/](http://localhost:4795/home/). Use the arrow keys, `Enter` and `Escape`, or click the controls. The public preview uses the included Manrope, Geist and Newsreader fonts.

---

## What You Can Customize in CleanFrame

- **Layout**: Choose a **List** or **Dock** layout, displaying icons, app names, or both.
- **Backdrop & Theme**: Use a curated photograph, or a minimal light or dark background.
- **Clock & Date**: Switch between **12-hour** and **24-hour** clock formats, and toggle the **Date** display (`Off` / `On`).
- **Materials & Typography**: Adjust glass material presets (`Soft`, `Blur`, `Frost`, `Clear`), focus styles, and typography (`Manrope`, `Geist`, `Newsreader`).
- **Apps**: Add, remove, and reorder up to 12 apps on the Home screen from all apps installed on your TV.
- **Wallpapers**: Choose from 15 bundled high-resolution photographs with pre-rendered optical glass materials and adaptive text ink.

---

## Samsung The Frame (Tizen) Setup

CleanFrame retains full compatibility with Samsung The Frame (Tizen). Use the staged [Samsung The Frame setup guide](docs/agent-tv-setup-guide.md) to adapt the Tizen widget build (`app/config.xml`) and certificates to your TV.

---

## Develop and Check

```sh
npm run check
npx playwright install chromium
CLEANFRAME_PUBLIC_FONTS=1 npm run check:browser
npm run check:public
npm run export:public
```

| Location | Purpose |
| --- | --- |
| `app/` | Packaged UI, runtime scripts, styles, and bundled assets |
| `android/` | Native Google TV / Android TV wrapper (`com.cleanframe.tv`) and build script |
| `scripts/` | Preview server, validation checks, asset generation, and reference TV tools |
| [Development](docs/development.md) / [testing](docs/testing.md) | Runtime map, preferences, checks, and device boundaries |
| [Assets](docs/assets.md) / [third-party notices](THIRD_PARTY_NOTICES.md) | Rendering recipes, font separation, and asset provenance |
| [Asset sources](docs/asset-sources.md) | Photo credits and verification of bundled third-party assets |

---

## Licence and Credits

CleanFrame's original code and documentation by [nthnclrk](https://github.com/nthnclrk/CleanFrame) use the [MIT licence](LICENSE). Fonts, photographs and logos retain their own terms; read [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). CleanFrame is an independent project with no Samsung, Google, or streaming-service affiliation.
