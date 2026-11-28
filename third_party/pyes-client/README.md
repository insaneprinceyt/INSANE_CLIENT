# INSANE CLIENT preset: PYES provenance

The optional **Fabric + INSANE CLIENT** preset includes a branded copy of the user-supplied PYES Client JAR. The launcher installs it only for **Minecraft 1.21.11, Fabric Loader 0.19.2 or newer, and Java 21 or newer**, together with its required Fabric API dependency.

The download filename is `PYES-Client-1.1.1.jar`; its `fabric.mod.json` declares **1.1.2**. We use the manifest version and preserve the upstream author, `PYES_KING (Owner/Developer)`, the `pyesclient` mod ID, dependencies, mixins and entrypoints.

The supplied manifest declares **MIT**. Upstream did not include a standalone PYES license file; [LICENSE](LICENSE) supplies the standard MIT text with the declared author's attribution. Upstream third-party notices are retained verbatim. Mouse Tweaks (BSD-3-Clause) and FlySpeed (CC0-1.0) remain byte-for-byte unchanged, including their embedded licenses.

## Reproduce the branded artifact

Download the exact upstream JAR from the [user-provided MediaFire page](https://www.mediafire.com/file/ailnuvks6xrtu9p/PYES-Client-1.1.1.jar/file), then run from the repository root:

```sh
python3 scripts/rebrand_pyes_client.py /path/to/PYES-Client-1.1.1.jar /path/to/INSANE-CLIENT-1.1.2.jar --report /path/to/branding-report.json
```

The script refuses any input whose SHA-256 or size differs from [UPSTREAM.json](UPSTREAM.json). It reads the archive and class constant pools without running client code. Fixed ZIP ordering, timestamps and permissions make repeated builds deterministic with the same Python/zlib toolchain. The launcher pins the exact distributed output hash before installation.

The client title, dashboard headings, launcher mod name and client controls category use INSANE CLIENT. Six logo, wordmark and panel textures reuse the launcher's existing IC mark; their original canvas dimensions and UI crop coordinates are preserved. Editable SVGs and their PNG exports are in [branding](branding/).

Only allowlisted display `String` constants change. Class names, code instructions, mixins, resource paths, settings files, module names and option identifiers remain intact. For example, `PYES Client Watermark`, `PYES Player` and other module labels remain because upstream stores their names as configuration keys. This preserves compatibility instead of silently resetting settings.

The upstream `UpdateClient` entrypoint also registers world GUI controls and the dashboard button. It is retained. The embedded `pyes-update-channel.json` has an empty manifest URL and public key, which make `connected()` false and gate automatic/manual update checks, promotion registration and the update timer. The upstream updater therefore cannot replace this branded preset through its normal lifecycle. The embedded helper JAR remains unchanged and inactive.

This is a **binary branding patch**, not a source fork of the game client: the supplied archive contains compiled classes, and no upstream Java source was provided. The launcher source, deterministic patch script, editable branding assets and provenance are included in this repository. This does not claim authorship of the upstream gameplay features.

## Verification and limits

[VERIFICATION.json](VERIFICATION.json) records a repeated byte-identical build, wrong-input rejection, unchanged class instructions and metadata compatibility, successful static `javap` parsing of all modified classes, and unchanged nested JAR hashes. [UPDATER-AUDIT.json](UPDATER-AUDIT.json) records the updater control-flow review.

These are archive and static bytecode checks. The client was not executed in the build environment. Android gameplay, client feature behavior, shader compatibility and rendering on physical devices remain unverified.
