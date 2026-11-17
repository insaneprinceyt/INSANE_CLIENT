<p align="center">
  <img src="website/public/assets/logo.svg" width="112" height="112" alt="INSANE CLIENT logo">
</p>

<h1 align="center">INSANE CLIENT</h1>

<p align="center">
  A native Minecraft Java launcher for Android, with a dark landscape interface<br>
  and experimental Rust Edition support powered by Horizon / Starling.
</p>

<p align="center">
  <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.1.1"><img src="https://img.shields.io/badge/version-2.1.1-83d56f" alt="Version 2.1.1"></a>
  <img src="https://img.shields.io/badge/Android-8.1%2B-3DDC84?logo=android&logoColor=white" alt="Java launcher: Android 8.1 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-LGPL--3.0-blue" alt="LGPL-3.0 license"></a>
  <img src="https://img.shields.io/badge/status-development-orange" alt="Development build">
</p>

<p align="center">
  <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/download/v2.1.1/INSANE-CLIENT-2.1.1-android.apk"><strong>Download APK</strong></a>
  · <a href="https://insane.insanehost.qzz.io/android">Website</a>
  · <a href="https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.1.1">Release & checksums</a>
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
| **Accounts** | Microsoft, Ely.by and local account options for Java; skin and cape selection. |
| **Controls & graphics** | Touch controls, gamepad mapping, Java runtime management and renderer configuration. |
| **Appearance** | Dark landscape UI, INSANE branding, themes and custom backgrounds. |
| **Rust Edition** | Integrated Horizon / Starling engine, separate game folders, native touch controls, render scale and orientation settings. |

The dedicated **Hyper Client preset and updater are removed**. Regular Fabric and Quilt installation remains available.

## Install on Android

1. Download the APK from [GitHub Releases](https://github.com/insaneprinceyt/INSANE_CLIENT/releases/tag/v2.1.1) or the [website](https://insane.insanehost.qzz.io/android).
2. Open the downloaded file. If Android asks, allow your browser or file manager to install apps.
3. Install over the existing INSANE CLIENT development build, then open the launcher.
4. Select an account, create or select an instance, and press **Play**.

Package: `com.insaneclient.launcher.debug` · Version: **2.1.1** · Version code: **16**

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
Hyper_Launcher_v3/build/outputs/apk/full/debug/INSANE-CLIENT-2.1.1-android.apk
```

The first build downloads Gradle dependencies and declared runtime/component assets. GLFW, SDL, MojoExec and NoBlocklist are included as source directories in this repository; no recursive submodule checkout is needed. Their upstream origins are preserved in [.gitmodules.upstream](.gitmodules.upstream) and the [build report](docs/releases/BUILD-REPORT-2.1.1.json).

Private signing keys and API credentials are not included. The native Rust engine is packaged from its pinned AAR rather than compiled from Rust source by this build.

### Run focused checks

From the repository root with a JDK available:

```bash
scripts/test_horizon_install.sh
scripts/test_horizon_catalog.sh
scripts/test_horizon_assets.sh
scripts/test_legacy_ely.sh
scripts/test_legacy_migration.sh
scripts/test_service_keys.sh
```

The 2.1.1 installer, catalog and engine-asset suites passed **1,056 checks**. The Android build, APK signing identity, archive integrity, packaged native libraries and source archive were also verified. See the [build report](docs/releases/BUILD-REPORT-2.1.1.json) and [artifact checksums](docs/releases/SHA256SUMS-2.1.1.txt) for recorded evidence.

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
- Full launcher license: [LICENSE](LICENSE). Original upstream documentation and credits remain in this repository.

INSANE CLIENT is not an official Hyper, Horizon, Starling or Minecraft release and is not affiliated with Mojang, Microsoft or Ely.by.
