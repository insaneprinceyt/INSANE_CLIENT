# LTW renderer

INSANE builds the official [MojoLauncher LTW](https://github.com/MojoLauncher/LTW)
renderer from source. The exact upstream commit and native tree are pinned in
[UPSTREAM.json](UPSTREAM.json). `native/` is an unchanged copy of that upstream
native tree, including the GLSL optimizer and copyright/license notices.

The Android library module builds `libltw.so` for ARMv7, ARM64 and x86-64 using
the launcher NDK. Its helper libraries and C++ runtime are linked statically,
and its ELF load segments support 16 KB page alignment. The app depends on this
module explicitly; an ignored, locally supplied `ltw-release.aar` is not needed.

LTW requires a working **OpenGL ES 3.0 / ESSL 3.00** driver. There is no GPU-brand
allowlist. Minecraft versions, shaders and mods can require additional features;
GLES2-only devices cannot gain GLES3 capabilities through this renderer.
Native gameplay still requires testing on real Android devices.

Build it as part of the app:

```sh
./gradlew :Hyper_Launcher_v3:assembleFullDebug --max-workers=3
```

Or build only the renderer module with `./gradlew :ltw:assembleDebug`.
LGPL-3.0 applies; see [LICENSE](LICENSE) and the source notices. The complete
renderer source is included in INSANE's repository and source release archive.
