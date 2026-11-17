# INSANE CLIENT website

The public Android download site and its Cloudflare Worker, plus the earlier browser preview. The native launcher source is in the repository root; see the [main README](../README.md).

Open `public/index.html` in a browser, or preview the pages using `python -m http.server 8765 --directory public`. The APK endpoints require the deployed Worker and its R2 binding.

## Working features

- Create, select, edit, and delete local game profiles
- Choose Minecraft version, mod loader, and memory allocation
- Edit the local player name
- Browse, search, and save community servers; copy server addresses
- Persist resolution, fullscreen, background, and motion preferences in localStorage
- Export the current configuration as JSON
- Responsive desktop, tablet, and mobile layouts; keyboard-accessible dialogs

This is a browser preview. It does not authenticate Microsoft accounts, download Minecraft, install mods, or start Java. The Play dialog explicitly explains this and offers configuration export and the official Minecraft download link. Memory and resolution settings are saved configuration values, not changes applied to a running game.

## Assets

The block-world landscape and green block icon are original vector artwork, shared with the Android launcher. System fonts are used. Server entries are curated suggestions; no live player counts or availability are claimed.

## Validation

Browser workflow checks exercise profile creation/edit/deletion, Java version display, saved memory and fullscreen preferences, player rename, server creation/search, launch disclosure, JSON download contents, last-profile protection, mobile overflow, and JavaScript runtime errors.

## Android downloads

The public `/android` page provides the signed Android development APK and corresponding source. Release binaries are held in the Sites-provisioned R2 `BUCKET` binding; they are streamed to the browser with attachment headers and byte-range support. See `worker/releases.json` for exact filenames, sizes and SHA-256 digests. Device and gameplay testing remain unverified, as described in the included build report.

The original browser preview lives in `public/`. Run `npm run build` to package the preview, download page and Worker routes into `dist/server/index.js` and `dist/.openai/hosting.json`. The build has no package dependencies. The hosting manifest preserves the same public Sites project and uses its R2 bucket.
