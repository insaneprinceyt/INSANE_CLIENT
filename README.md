<p align="center">
  <img src="website/public/assets/logo.svg" width="112" height="112" alt="INSANE CLIENT logo">
</p>

<h1 align="center">INSANE CLIENT</h1>

<p align="center">
  A native Minecraft Java launcher for Android, with a dark landscape interface<br>
  and an emerald-and-silver IC identity. Includes experimental Rust Edition<br>
  support powered by Horizon / Starling.
</p>

<p align="center">
  <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.2.4"><img src="https://img.shields.io/badge/version-2.2.4-55e5aa" alt="Version 2.2.4"></a>
  <img src="https://img.shields.io/badge/Android-8.1%2B-3DDC84?logo=android&logoColor=white" alt="Java launcher: Android 8.1 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-LGPL--3.0-blue" alt="LGPL-3.0 license"></a>
  <img src="https://img.shields.io/badge/status-development-orange" alt="Development build">
</p>

<p align="center">
  <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/download/v2.2.4/INSANE-CLIENT-2.2.4-android.apk"><strong>Download APK</strong></a>
  · <a href="https://insane.insanehost.qzz.io/android">Website</a>
  · <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.2.4">Release & checksums</a>
  · <a href="#build-from-source">Build from source</a>
  · <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/issues">Report an issue</a>
</p>

---

## What you can do

| Area | Included features |
| --- | --- |
| **Instances** | Create separate installations, choose game versions and edit per-instance settings. |
| **Java modding** | Fabric, Quilt, Forge, NeoForge and OptiFine installation flows; modpack import. |
| **Content** | Browse mods, modpacks, resource packs, shaders and worlds through the content installer; manage installed files. |
| **Friends & Worlds** | Optional World Host setup for compatible Fabric instances; manage friends and join shared worlds inside Minecraft. Experimental on Android. |
| **Accounts** | Microsoft, Ely.by and local account options for Java; skin and cape selection. |
| **Controls & graphics** | Touch controls, gamepad mapping, Java runtime management and renderer configuration. |
| **Appearance** | Dark landscape UI, INSANE branding, themes and custom backgrounds. |
| **Rust Edition** | Integrated Horizon / Starling engine, separate game folders, native touch controls, render scale and orientation settings. |

The dedicated Hyper Client option remains removed. **Fabric + INSANE CLIENT** is a separate opt-in preset based on the supplied MIT-licensed PYES Client, with its original attribution retained.

## Install on Android

1. Download the APK from [GitHub Releases](https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.2.4) or the [website](https://insane.insanehost.qzz.io/android).
2. Open the downloaded file. If Android asks, allow your browser or file manager to install apps.
3. Install over the existing INSANE CLIENT development build, then open the launcher.
4. Select an account, create or select an instance, and press **Play**.

Package: `com.insaneclient.launcher.debug` · Version: **2.2.4** · Version code: **21**

The published APK uses the same development signing identity as previous INSANE releases. A locally built APK uses your own debug key, so it may not install over the published APK. Preserve your game data before changing signing identities.

### Device requirements

| | Java launcher | Integrated Rust Edition |
| --- | --- | --- |
| Android | 8.1 / API 27 or newer | 9 / API 28 or newer |
| Architecture | ARMv7, ARM64 or x86_64 | ARM64, running in a 64-bit app process |
| Graphics | Depends on game version and renderer | Vulkan required |
| Runtime | Java 8 assets included; newer games may need a newer Java runtime | Bundled native engine |
| Accounts | Microsoft, Ely.by or local | Microsoft or local |

First-time game/resource downloads require an internet connection. Local accounts work for offline play and servers that accept them; they do not grant access to premium services. Official CurseForge access and optional Gemini crash analysis may require your own API keys.

## New in 2.2.4: INSANE CLIENT preset and content browsing

Open **Instances → Add → Fabric**, select **Fabric + INSANE CLIENT**, then tap **Install INSANE CLIENT**. This creates a separate instance with the branded client **1.1.2** and a verified, compatible Fabric API. The supplied file is named PYES-Client-1.1.1.jar, but its internal release is 1.1.2 and supports **Minecraft 1.21.11**, **Fabric 0.19.2+**, and **Java 21+**. Other Minecraft versions remain available through the regular Fabric option.

The preset keeps the upstream mod ID and saved module identifiers, retains original authors and bundled licenses, and disables the upstream self-update channel so it cannot replace this branded build. See [client provenance and reproduction](third_party/pyes-client/README.md). Android gameplay with this client remains unverified.

The content installer opens directly on the **Modrinth catalog** with no search required. Mods, resource packs and shaders have working **Any** filters. Exact Minecraft versions, including **26.x**, snapshots and prereleases, are preserved; network failures now appear as errors instead of empty results. Release selection checks compatibility before installation, and required dependencies no longer fall back to an unrelated game version.

## Fixed in 2.2.3: LTW renderer availability

The 2.2.2 APK omitted `libltw.so`. LTW is now an explicit build dependency, compiled from the official [MojoLauncher LTW source](https://github.com/MojoLauncher/LTW) for **ARMv7, ARM64 and x86_64**. GPU detection tries additional EGL configurations when the first fails, and automatic compatibility switches apply LTW’s game-option fixes and warnings to the selected renderer.

Choose **Settings → Video and renderer → Renderer → LTW**, or select **LTW** in an instance's Renderer settings. LTW is intended for **Minecraft 1.17+** and requires a working **OpenGL ES 3.0 / ESSL 3.00 driver**. Availability is based on graphics capabilities, with no GPU-brand allowlist. Minecraft versions, mods and shaders may need more; GLES2-only hardware cannot be forced to support GLES3.

Source provenance and build details are in [third_party/ltw](third_party/ltw). Consult the [2.2.3 build report](docs/releases/BUILD-REPORT-2.2.3.json) for completed validation. Rendering and gameplay still require physical-device testing; this update does not guarantee compatibility with every device or game setup.

## New in 2.2.2: upgraded app logo

A new emerald-and-silver **IC monogram** replaces the older logo assets. The dark adaptive icon supports circular and rounded-square home-screen masks, with a monochrome version for Android 13 themed icons. The same mark appears in About, default instance icons, notifications and store artwork.

Editable vector artwork and the asset generator are in [assets/branding](assets/branding). The 2.2.2 release updated branding and retained the graphics-driver fixes from 2.2.1. Appearance on a physical Android device remains unverified.

## Fixed in 2.2.1: graphics-driver startup

Turnip selection now uses one policy across all launch entry points. It is enabled only for **Qualcomm Adreno on Android 9 / API 28 or newer**, with **Use system Vulkan driver** turned off. Mali, other GPUs and unknown graphics devices explicitly keep Turnip disabled, including when a previous launch enabled it.

The native loader guards missing callbacks and namespaces, and preserves callback-library lifetime after a failed custom-driver setup. These changes address the Zink/Turnip startup path identified in a Mali-G68 crash log.

**The affected phone still needs a launch test.** This fix does not establish general Zink or shader compatibility, and does not repair Simple Voice Chat's separate native audio-library failures. If a native crash continues, include the instance's `hs_err_pid*.log` with the launcher log.

## New in 2.2.0: Friends & Worlds

Open **Home → Friends & Worlds**, select a compatible **Fabric Java instance** and a **Microsoft account that owns Java Edition**, then choose **Install World Host**. The launcher finds a supported release for that exact Minecraft version and verifies its size and SHA-512 hash before adding it to the instance.

1. Tap **Launch Minecraft**, then open **Multiplayer → Friends → Add Friend**. Both players must add each other.
2. The host opens a singleplayer world, pauses and chooses **Open to Friends**.
3. The guest opens **Multiplayer → Friends** and joins the shared world. The host must keep Minecraft and the world running.

Use the same Minecraft version and matching required mods. In World Host settings, enable **Whitelist Joins** for friends-only access and keep security set to **Secure**.

The friends list and invitations belong to World Host's **in-game UI**. This setup does not support Rust Edition, local accounts or Ely.by accounts. No supported release is currently available for Minecraft 26.x. Existing or disabled World Host copies must be reviewed in **Manage installed mods** before installing another copy.

World Host is an optional [MIT-licensed mod](https://github.com/Gaming32/world-host), downloaded from [Modrinth](https://modrinth.com/mod/world-host). Its default connection service is the third-party relay `world-host.jemnetworks.com`. **Two-device hosting and joining on Android remain unverified.** See the [friends setup guide](docs/FRIENDS.md) for compatibility and troubleshooting.

## Rust Edition: versions and setup

Open **Instances → Add → Rust Edition**, choose the **integrated v0.7.7 build**, and tap **Install**. Wait for resources to finish, save the instance, then select a local or Microsoft account and press **Play**.

The picker provides search, **All releases / In INSANE** filters, Refresh and cached release history. It retrieves official Android engine releases from Starling's current and legacy repositories; 27 official releases were returned during verification.

> **Compatibility:** only the bundled custom v0.7.7 engine for **Minecraft 26.3** currently installs inside INSANE. Other engine releases are listed as **not integrated**, with release-information links. This is not a list of playable Minecraft Java versions, and it does not make every Rust engine release internally installable.

Rust keeps separate worlds and resources. It uses Horizon `.hmod` content; Java Fabric/Forge `.jar` mods are not compatible. The imported engine is a prebuilt AAR. Its source references, exact hashes and license notices are in [third_party/horizon](third_party/horizon/README.md).

## What's fixed in 2.1.1

- **Premature Rust setup failure:** installation now waits for the native completion callback before checking resources. Starting a background download no longer immediately reports “Resources could not be prepared.”
- **Install coordination:** progress remains active, overlapping installs are blocked, and download errors are retained for retry.
- **Version picker:** Rust Edition opens a searchable release list instead of immediately creating a fixed instance.
- **Background completion:** successful installation saves the new instance and opens its editor when the launcher resumes.

Existing Rust instances can **retry Play after updating**; deleting the instance is not required for this fix.

## Build from source

### Toolchain

| Tool | Version used for the published build |
| --- | --- |
| JDK | 21 |
| Android SDK platform | 37.1 |
| Android Build Tools | 37.0.0 |
| NDK | 30.0.16248370 |
| CMake | 3.22.1 |
| Gradle wrapper | 9.6.0 |
| Android Gradle Plugin / Kotlin | 9.4.1 / 2.4.21 |

Install the matching Android tools and configure `JAVA_HOME` and `ANDROID_HOME`, or set `sdk.dir` in your local `local.properties`.

```bash
git clone https://github.com/insaneprinceyt/INSANE_CLIENT.git
cd INSANE_CLIENT
chmod +x gradlew
./gradlew :Hyper_Launcher_v3:assembleFullDebug --max-workers=3
```

Output:

```text
Hyper_Launcher_v3/build/outputs/apk/full/debug/INSANE-CLIENT-2.2.3-android.apk
```

The first build downloads Gradle dependencies and declared runtime/component assets. GLFW, SDL, MojoExec and NoBlocklist are included as source directories in this repository; no recursive submodule checkout is needed. Their upstream origins are preserved in [.gitmodules.upstream](.gitmodules.upstream) and the [build report](docs/releases/BUILD-REPORT-2.1.1.json).

Private signing keys and API credentials are not included. The native Rust engine is packaged from its pinned AAR rather than compiled from Rust source by this build.

### Run focused checks

From the repository root with a JDK available:

```bash
scripts/test_gpu_probe.sh
scripts/test_turnip_policy.sh
scripts/test_turnip_native.sh
scripts/test_world_host.sh
scripts/test_horizon_install.sh
scripts/test_horizon_catalog.sh
scripts/test_horizon_assets.sh
scripts/test_legacy_ely.sh
scripts/test_legacy_migration.sh
scripts/test_service_keys.sh
```

For 2.2.3, `scripts/test_gpu_probe.sh` passed **21 scenarios / 236 checks**, and the existing Turnip policy passed **8 scenarios / 91 checks**. The full Android build and LTW package checks passed. Verify the packaged LTW libraries with `python3 scripts/verify_ltw_apk.py <path-to-apk>`; it checks all three supported ABIs, exports, dependencies, page alignment and bundled provenance. See the [release build report](docs/releases/BUILD-REPORT-2.2.3.json) for completed results.

For 2.2.2, the full Android build, package/signature checks, adaptive safe-area check, density sizes and monochrome/notification transparency checks passed. See the [logo verification](docs/releases/LOGO-VERIFICATION-2.2.2.json), [build report](docs/releases/BUILD-REPORT-2.2.2.json) and [checksums](docs/releases/SHA256SUMS-2.2.2.txt).

For 2.2.1, the Turnip policy suite passed **8 scenarios / 91 checks**. Native tests passed **7 hook scenarios and 12 loader scenarios**, including failure, retry and concurrent initialization; all 12 loader scenarios also passed ASan/UBSan. The full Android build and package checks passed. See the [driver verification](docs/releases/DRIVER-VERIFICATION-2.2.1.json), [build report](docs/releases/BUILD-REPORT-2.2.1.json) and [checksums](docs/releases/SHA256SUMS-2.2.1.txt).

For 2.2.0, the World Host suite passed **11 scenarios and 94 checks**, covering release selection, download verification and safe installation failures. A live Modrinth download/install check and the full Android build also passed. See the [2.2.0 verification record](docs/releases/FRIENDS-VERIFICATION-2.2.0.json), [build report](docs/releases/BUILD-REPORT-2.2.0.json) and [checksums](docs/releases/SHA256SUMS-2.2.0.txt).

For the previous 2.1.1 release, the installer, catalog and engine-asset suites passed **1,056 checks**. The Android build, APK signing identity, archive integrity, packaged native libraries and source archive were also verified. See the [build report](docs/releases/BUILD-REPORT-2.1.1.json) and [artifact checksums](docs/releases/SHA256SUMS-2.1.1.txt) for recorded evidence.

> This is a development release. Host-side checks do not prove device behavior: native resource downloading, touch/UI lifecycle, live account sign-in and Minecraft gameplay still need testing on a physical Android device.

## Repository guide

```text
Hyper_Launcher_v3/       Android application, Java launch backend and Rust integration
ColorPicker/            Shared Android UI component
glfw/  sdl/  mojoexec/   Native dependencies, included as source
NoBlocklist/            Java agent source
forge_installer/        Forge installation agent
scripts/                Build helpers and focused regression checks
third_party/horizon/    Native engine provenance, license and notices
third_party/ltw/        LTW renderer source, Android build module and provenance
docs/FRIENDS.md          World Host setup, compatibility and troubleshooting
docs/releases/          Build report and artifact checksums
website/                Download website, older browser preview and Worker routes
INSANE_CLIENT.md        Detailed installation and implementation notes
README_UPSTREAM.md      Original upstream project README
```

### Website source

```bash
cd website
npm run build
```

This produces `dist/server/index.js`, a Cloudflare Worker that serves the download pages and streams release files from the `BUCKET` R2 binding. The hosting manifest in `website/.openai/hosting.json` identifies the existing Sites project. Building the website does not upload the APK or provision storage.

The older browser preview is separate from the native APK and cannot launch Minecraft. See [website/README.md](website/README.md) for local preview and hosting details.

## Reporting problems

Open an [issue](https://github.com/insaneprinceyt/INSANE_CLIENT/issues) with your phone model, Android version, INSANE version, Java/Rust selection and steps to reproduce. Include the visible error or a relevant launcher-log excerpt, with account tokens and personal information removed.

## Credits and license

INSANE CLIENT is an independent **LGPL-3.0** fork of [Hyper Launcher](https://github.com/ashmeet-op/Hyper_Launcher_v3) by Ashmeet, based on [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).

- Launcher base: `Mars_Build.5`, commit `80cb097c19053283d0c374612268f297bb0f49ca`.
- Rust integration references: [Horizon](https://github.com/horizon-rust/horizon) and [Starling](https://github.com/starling-client/starling); imported AAR provenance is documented separately.
- The native engine's MIT license and third-party notices are preserved in [third_party/horizon](third_party/horizon/README.md). Other components retain their own licenses.
- Optional friends integration: [World Host](https://github.com/Gaming32/world-host), MIT licensed and downloaded separately on request.
- Full launcher license: [LICENSE](LICENSE). Original upstream documentation and credits remain in this repository.

INSANE CLIENT is not an official Hyper, Horizon, Starling or Minecraft release and is not affiliated with Mojang, Microsoft or Ely.by.
