# INSANE CLIENT 2.2.4 for Android

INSANE CLIENT 2.2.4 adds a Fabric + INSANE CLIENT preset and fixes the Modrinth catalog. It retains the LTW renderer and improved graphics
capability detection, the emerald-and-silver IC branding from 2.2.2,
graphics-driver fixes from 2.2.1, optional World Host setup for friends and shared
Java worlds, and experimental Rust Edition support powered by Horizon / Starling.
Its Java launcher is a fork of Hyper Launcher v3, introduced in INSANE CLIENT 2.0.0.
It retains the INSANE name, public download site, application ID and development
signing identity. Hyper's source architecture and native launcher features are included.

## Upstream and license

Base: https://github.com/ashmeet-op/Hyper_Launcher_v3
Tag: `Mars_Build.5`, commit `80cb097c19053283d0c374612268f297bb0f49ca`.
The upstream LGPL-3.0 license, source notices, README files and third-party credits
are preserved. INSANE changes to this launcher are supplied under the same license.
Hyper Launcher is developed by Ashmeet and builds on MojoLauncher and PojavLauncher.
This fork is independently distributed and is not an official Hyper release.

## Changes in 2.2.4: client preset and Modrinth catalog

Select **Instances → Add → Fabric → Fabric + INSANE CLIENT**. This creates a private instance with client 1.1.2 and matching Fabric API. It supports Minecraft **1.21.11**, Fabric **0.19.2+** and Java **21+**. Plain Fabric remains available for other versions. Downloads are verified before the instance becomes visible; failure leaves no incomplete instance.

The supplied PYES JAR declares MIT licensing and version 1.1.2 despite its filename. The branded build preserves upstream authors, bundled licenses, internal mod IDs and saved module identifiers. Its upstream self-update channel is disabled. See [provenance and reproduction](third_party/pyes-client/README.md). The supplied binary was statically inspected, not rebuilt from upstream source; Android gameplay remains unverified.

Mods, resource packs and shaders now open on the Modrinth catalog without a search query. Any filters reset correctly, Minecraft 26.x and prerelease identifiers remain exact, and network failures are visible. Incompatible installs require confirmation; dependency selection cannot fall back to an unrelated Minecraft version.

## Changes in 2.2.3: LTW renderer availability

The 2.2.2 APK omitted `libltw.so`. The app now explicitly depends on the
`third_party/ltw` Android module, which builds the official
[MojoLauncher LTW renderer](https://github.com/MojoLauncher/LTW) from source commit
`3f1f4df6c80c925ea34067b1d943a84acce0184d` for ARMv7, ARM64 and x86_64. The renderer
source, build configuration, license and provenance ship in the repository and
source archive; see [third_party/ltw](third_party/ltw).

GPU probing tries additional EGL configurations, including depth-16 configurations,
when a preferred configuration cannot create a working context. It handles missing
GL strings safely and preserves the caller's EGL context during cleanup. Automatic
compatibility switches apply LTW’s game-option fixes and warnings to the selected
renderer.

Open **Settings → Video and renderer → Renderer**, or an instance's **Renderer**
settings, and choose **LTW (OpenGL ES 3, Minecraft 1.17+)**. LTW requires a working
**OpenGL ES 3.0 / ESSL 3.00 driver**. There is no GPU-brand allowlist, but Minecraft
versions, mods and shaders can require additional capabilities. GLES2-only hardware
cannot be made GLES3-capable by forcing the renderer selection.

Run `scripts/test_gpu_probe.sh` for capability-probe checks and
`python3 scripts/verify_ltw_apk.py <path-to-apk>` for the packaged libraries. Consult
the [2.2.3 build report](docs/releases/BUILD-REPORT-2.2.3.json) for completed results.
Physical-device rendering and gameplay remain unverified; this update does not
establish compatibility with every Android device or Minecraft setup.

## New in 2.2.2: upgraded app logo

The new IC monogram uses emerald and silver on a dark app-icon background. Adaptive
foreground artwork fits circular and rounded-square masks, and Android 13 themed
icons use a dedicated monochrome silhouette. About, default instance icons,
notification icons and store artwork share the same geometry. Loader-specific and
user-selected instance images are retained.

The editable SVG and Android asset generator are documented in
[assets/branding](assets/branding). The 2.2.2 release changed branding only. Physical-device
icon appearance remains unverified; consult the release report for completed checks.

## Changes in 2.2.1

The Mali-G68 crash log showed a failed Turnip preload followed by a native crash in
`liblinkerhook.so` during Zink startup. Two activity launch paths could enable Turnip
without the GPU check used by the later game runner.

All launch entry points now use the same driver policy. Turnip is enabled only when
the reported vendor and renderer identify **Qualcomm Adreno**, Android is **API 28 or
newer**, and **Use system Vulkan driver** is off. Every other selection explicitly
disables Turnip, clearing any state left by an earlier launch. The selected instance
renderer is retained; this change does not silently replace Zink with another renderer.

The native loader guards missing callbacks and namespaces and keeps callback-library
references valid after custom-driver setup fails. Failed custom-driver setup can fall
back to the system driver without leaving callbacks pointing into unloaded libraries.

Run `scripts/test_turnip_policy.sh` and `scripts/test_turnip_native.sh` for the focused
regression checks; consult the release build report for completed validation results.
The affected Android device still needs testing. This fix does not establish general
Zink/shader compatibility or address Simple Voice Chat's separate native audio-library
errors. For a continuing native crash, include the instance's `hs_err_pid*.log` along
with the launcher log.

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

Download `INSANE-CLIENT-2.2.4-android.apk` from
https://insane.insanehost.qzz.io/android and install it over the previous INSANE
CLIENT development build. The Java launcher requires **Android 8.1 / API 27 or newer**.
The universal APK contains ARMv7, ARM64 and x86_64 native libraries; 32-bit x86 is not supported by
this Hyper base. Minecraft and renderer support can have higher device requirements.

The package remains `com.insaneclient.launcher.debug`. The version is `2.2.4`, code
`21`. Launcher screens use landscape orientation. Select an account and installation,
then use Play. Java 8 runtime assets are included; newer Minecraft versions may need
a newer Java runtime installed from runtime settings. Minecraft downloads require
internet on first launch. Online servers require accounts accepted by those servers.

For Java Edition, use Installer to browse/download content. Choose the correct game
version and loader. Use Instances to create or edit a game installation. Select
Minecraft and loader versions in the Fabric or Quilt installer, then install. Add
Java mods through Installer.

For LTW, select **Settings → Video and renderer → Renderer → LTW**, or choose it
in the instance's Renderer settings. It requires a working OpenGL ES 3.0 / ESSL 3.00
driver and targets Minecraft 1.17+; individual versions and mods may require more.

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
The LTW renderer is compiled from the pinned source in `third_party/ltw` for all
three supported Java-launcher ABIs; a locally supplied LTW AAR is not required.
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

Output: `Hyper_Launcher_v3/build/outputs/apk/full/debug/INSANE-CLIENT-2.2.4-android.apk`.
Debug builds use your local Android debug signing identity. The publisher's private
signing key is excluded from source. Build configuration supports optional API keys
through local/environment settings; do not commit private keys or credential files.

## Verification limits

Read the accompanying build report for actual completed checks. Host-side compilation,
archive/signature checks and file-based migration tests do not establish successful
Android touch behavior, live provider sign-in or Minecraft gameplay. No connected
Android device is available in the build environment. This is a development build.
