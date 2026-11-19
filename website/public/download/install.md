# INSANE CLIENT 2.2.0 for Android

INSANE CLIENT 2.2.0 adds optional World Host setup for friends and shared Java worlds.
It retains experimental Rust Edition support powered by Horizon / Starling. Its Java launcher is a fork of Hyper
Launcher v3, introduced in INSANE CLIENT 2.0.0. It retains the INSANE name, IC logo,
public download site, application ID and development signing identity. Hyper's source
architecture and native launcher features are included.

## Upstream and license

Base: https://github.com/ashmeet-op/Hyper_Launcher_v3
Tag: `Mars_Build.5`, commit `80cb097c19053283d0c374612268f297bb0f49ca`.
The upstream LGPL-3.0 license, source notices, README files and third-party credits
are preserved. INSANE changes to this launcher are supplied under the same license.
Hyper Launcher is developed by Ashmeet and builds on MojoLauncher and PojavLauncher.
This fork is independently distributed and is not an official Hyper release.

## Changes in 2.2.0

**Home → Friends & Worlds** prepares a compatible Fabric Java instance for World Host.
Select a Microsoft account that owns Minecraft Java Edition, then install the mod.
The launcher queries Modrinth project `2AIZDfYo` for the exact game version and Fabric
loader. It selects a stable World Host release at least `0.5.0`, verifies the published
size and SHA-512 hash, validates the mod manifest and adds it without overwriting
existing mods. Existing, renamed or disabled World Host copies are detected and must
be reviewed in the instance's mod manager before another copy can be installed.

Tap **Launch Minecraft**, then use **Multiplayer → Friends → Add Friend**. Both players
must add each other. The host opens a singleplayer world and chooses **Open to Friends**
from the pause menu; the guest joins through the Friends tab. Both need the same game
version and matching required mods. Keep the host's world open. Enable **Whitelist
Joins** and keep **Secure** authentication for friends-only access.

World Host provides the friends list and invitations inside Minecraft. The native
launcher does not display a synchronized live friends list. This integration supports
compatible Fabric Java instances and Microsoft Java accounts; it does not support
Rust Edition, local accounts or Ely.by. No supported World Host release is currently
available for Minecraft 26.x. Setup reports incompatible versions rather than installing
a different game version or an old World Host security protocol.

World Host is an optional [MIT-licensed project](https://github.com/Gaming32/world-host),
downloaded separately from Modrinth when requested. Its default connection service is
`world-host.jemnetworks.com`, operated by a third party. Android two-device hosting,
friend invitations and world joining remain unverified. See [docs/FRIENDS.md](docs/FRIENDS.md)
for the complete setup and troubleshooting guide. Host-side installer checks are run
with `scripts/test_world_host.sh`; consult the release build report for actual results.

## Changes in 2.1.1

Fixed the Rust setup error caused by treating the native installer's startup return
as completed installation. The native engine starts a background worker. INSANE now
keeps the progress dialog and single-install guard active until its completion
callback, then verifies resources before creating or launching an instance. A true
startup return no longer causes an immediate readiness failure. Download failures
retain their reported cause and allow retry after the worker finishes.

**Instances → Add → Rust Edition** now opens a searchable engine version list with
All releases / In INSANE filters and Refresh. It retrieves the official Android
release history from Starling's current and legacy repositories, handles pagination,
and caches the history for offline access. The integrated build remains available
when the catalog cannot be fetched.

The bundled custom **v0.7.7 engine for Minecraft 26.3** can be installed inside INSANE.
Other official versions are clearly marked **not integrated**, with a Release info
link. They are not silently installed as other Android apps or relabeled as the
bundled engine. These are Rust engine releases, not Minecraft Java game versions;
this update does **not** make every historical engine installable inside INSANE.

Rust requires **Android 9 / API 28 or newer, an ARM64 device running the 64-bit app,
and Vulkan support**. It uses a separate game directory and native touch controls.
Local and Microsoft account launch paths remain; Ely.by is available for Java.
Microsoft launch refreshes credentials and supplies a private, short-lived session.
Rust uses Horizon `.hmod` content, not Fabric/Forge Java `.jar` mods.

The imported engine's provenance and license notices are documented in
[third_party/horizon/README.md](third_party/horizon/README.md). Physical-device Rust
installation, gameplay, touch controls and live Microsoft sign-in remain unverified.

## Retained changes from 2.0.1

The dedicated Hyper Client setup, version picker and update shortcut are removed.
Fabric and Quilt install the selected loader normally. Existing installations and
user-installed mods are kept. The launcher opens in dark mode on fresh installs
and once after updating; Appearance settings remain available for later changes.

## Java launcher features

- Native Hyper home/navigation, instance selector/editor and instance directory.
- Friends & Worlds setup for the optional World Host mod on compatible Fabric instances.
- Local, Microsoft and Ely.by accounts; skin/cape selection and offline cosmetics.
- Fabric, Quilt, Forge, NeoForge and other upstream loader/install flows.
- Modrinth/CurseForge discovery for mods, modpacks, resource packs, shaders and worlds.
- Installed-file management and local file/modpack import.
- Custom touchscreen controls, gamepad mapping and per-instance settings.
- Java runtime management, renderer/driver selection and plugin support.
- Themes, custom launcher backgrounds, recording gallery, logs and optional diagnostics.

Some upstream integrations require their own setup: official CurseForge access can
use a user API key; keyless CurseForge uses the upstream public mirror. AI crash
analysis needs your Gemini API key. Discord presence requires user configuration.
No private upstream API credentials are included in this build.

## Install and use

Download `INSANE-CLIENT-2.2.0-android.apk` from
https://insane.insanehost.qzz.io/android and install it over the previous INSANE
CLIENT development build. The Java launcher requires **Android 8.1 / API 27 or newer**.
The universal APK contains ARMv7, ARM64 and x86_64 native libraries; 32-bit x86 is not supported by
this Hyper base. Minecraft and renderer support can have higher device requirements.

The package remains `com.insaneclient.launcher.debug`. The version is `2.2.0`, code
`17`. Launcher screens use landscape orientation. Select an account and installation,
then use Play. Java 8 runtime assets are included; newer Minecraft versions may need
a newer Java runtime installed from runtime settings. Minecraft downloads require
internet on first launch. Online servers require accounts accepted by those servers.

For Java Edition, use Installer to browse/download content. Choose the correct game
version and loader. Use Instances to create or edit a game installation. Select
Minecraft and loader versions in the Fabric or Quilt installer, then install. Add
Java mods through Installer.

For Rust Edition, open **Instances → Add → Rust Edition**. Select the integrated
v0.7.7 build and tap Install. Wait for resource installation to finish, then save
the new instance. Select a local or Microsoft account and press Play. Existing Rust
instances can retry Play after updating; their files do not need to be deleted. Unsupported Android/CPU/Vulkan configurations show
an explanation before the native engine is loaded. Rust instances keep their own
saves, resource packs and `horizon-mods` folder; existing Java game data is retained.

## Upgrade data

The upgrade includes explicit conversion of the previous INSANE account and profile
schemas. Original account/profile metadata is backed up inside private app storage.
Migrated installation entries refer to the original game folders so existing worlds,
mods, resource packs and configurations do not need to be moved or deleted.
The standalone 1.x skin-library files remain on disk; the new launcher uses Hyper's
skin/cape selection workflow. Keep a backup of important worlds before any app upgrade.
Detailed migration verification and any limits are recorded in the build report.

## Build from source

The repository and source archive include GLFW, SDL, MojoExec and NoBlocklist as
vendored source directories, with local compatibility changes where required by the
build toolchain. Their upstream references are preserved in `.gitmodules.upstream`
and the build report; a recursive submodule checkout is not required.
The Rust engine is supplied as the upstream prebuilt `libhorizon_android.aar`;
this build packages it without compiling Rust. The archive includes the AAR, launcher
integration source and attribution files, not a verified rebuild of the native engine.
See the provenance document above for exact upstream versions and binary hashes.

NoBlocklist is pinned to `f4209d4755d6af6a19fedbe578313e29ebfc45ff` because the upstream
release references this module without a committed gitlink.

Tools: JDK 21, Android SDK platform 37.1, Android build tools 37.0.0,
NDK 30.0.16248370, CMake 3.22.1 and the included Gradle 9.6.0 wrapper.
The project uses AGP 9.4.1 and Kotlin 2.4.21. Gradle downloads the declared dependencies
and Java/cacio component assets. Configure `ANDROID_HOME` or a local `sdk.dir` value.

```sh
chmod +x gradlew
./gradlew :Hyper_Launcher_v3:assembleFullDebug --max-workers=3
```

Output: `Hyper_Launcher_v3/build/outputs/apk/full/debug/INSANE-CLIENT-2.2.0-android.apk`.
Debug builds use your local Android debug signing identity. The publisher's private
signing key is excluded from source. Build configuration supports optional API keys
through local/environment settings; do not commit private keys or credential files.

## Verification limits

Read the accompanying build report for actual completed checks. Host-side compilation,
archive/signature checks and file-based migration tests do not establish successful
Android touch behavior, live provider sign-in or Minecraft gameplay. No connected
Android device is available in the build environment. This is a development build.
