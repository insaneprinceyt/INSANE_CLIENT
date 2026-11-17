# Horizon / Starling native engine provenance

INSANE CLIENT 2.1.1 includes experimental Rust Edition support using the native
engine distributed by Hyper Launcher. The Java launcher base remains
`Mars_Build.5` (`80cb097c19053283d0c374612268f297bb0f49ca`); Rust integration was
selectively adapted from a newer Hyper revision.

## Imported binary

- Repository: https://github.com/ashmeet-op/Hyper_Launcher_v3
- Pinned revision: `4a5efbbf0421afaa36c0b925b0d9a702cdfe8de9`
- Upstream file: [Hyper_Launcher_v3/libs/libhorizon_android.aar](https://github.com/ashmeet-op/Hyper_Launcher_v3/blob/4a5efbbf0421afaa36c0b925b0d9a702cdfe8de9/Hyper_Launcher_v3/libs/libhorizon_android.aar)
- Local file: `Hyper_Launcher_v3/libs/libhorizon_android.aar`
- AAR size: `35,191,683` bytes
- AAR SHA-256: `63e7c3da46f1ca7671b68a7b2e71f48dfe01fecd64bf4c5b426299eac4793044`
- Bundled engine: `jni/arm64-v8a/libhorizon_android.so`
- Bundled resource archive: `assets/engine-data.zip`
- Resource archive size: `9,121,637` bytes
- Resource archive SHA-256: `3b68ff02893f8d1a380737d8008347b189b70796ef83679b12d4178a72ffde13`

The AAR is imported unchanged. Android packaging may strip native debug symbols.
This project packages the prebuilt binary and does not rebuild the Rust engine.
The binary's native Android API requirement is 28 and it contains only ARM64 code;
INSANE also checks for a 64-bit app process and Vulkan support before launching it.

## Source references

Hyper's pinned README attributes its Rust backend to
[horizon-rust/horizon](https://github.com/horizon-rust/horizon) through
[starling-client/starling](https://github.com/starling-client/starling), release
[v0.7.7](https://github.com/starling-client/starling/releases/tag/v0.7.7).

The minimal Android native activity and launch-contract reference were adapted from
Starling revision `80700a6f26afe53a368032d8b28bd364f7c3e7e4`, including
[packaging/android/java/dev/horizon/android/HorizonActivity.java](https://github.com/starling-client/starling/blob/80700a6f26afe53a368032d8b28bd364f7c3e7e4/packaging/android/java/dev/horizon/android/HorizonActivity.java).
The Hyper binary includes customized launcher integration. It has not been proven
byte-for-byte equivalent to a build of stock Starling v0.7.7; these source references
are attribution and integration references, not a reproducible-build claim.

INSANE's integration adds a separate Rust instance directory, capability checks,
verified resource staging, native touch hosting, render/orientation settings and
private launch/session files. Rust uses Horizon `.hmod` content; Java Fabric/Forge
mods and Java control layouts are not interchangeable with this engine. The exposed
engine target is Horizon 26.3, identified internally as `26.3-horizon`.

## License and credits

The imported AAR contains an MIT license credited to **Ludovic Marie and the Horizon
contributors**, plus third-party notices. These files are preserved verbatim here:

- [LICENSE.txt](LICENSE.txt), copied from `assets/LICENSE.txt`.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), copied from
  `assets/THIRD_PARTY_NOTICES.md` (including its upstream formatting).

The same notices remain bundled in the AAR and APK assets. Preserve them when
redistributing the engine. Hyper Launcher is developed by Ashmeet; its launcher
source remains under the upstream LGPL-3.0 license. INSANE CLIENT is independently
distributed and is not an official Hyper, Horizon, Starling or Minecraft release.

## Verification limits

Hash validation and host-side compilation/package checks do not prove successful
Android gameplay, graphics-driver compatibility, native input or live Microsoft
sign-in. No connected Android device is available in this build environment. Consult
the release build report for checks actually performed.
