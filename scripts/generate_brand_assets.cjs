#!/usr/bin/env node
// Run from any directory: node scripts/generate_brand_assets.cjs
// Requires the sharp Node.js package. Only raster export needs sharp;
// Android vector resources all derive from the same checked-in SVG geometry.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const exportsPending = [];
const root = path.resolve(__dirname, '..');
const main = path.join(root, 'Hyper_Launcher_v3/src/main');
const res = path.join(main, 'res');
const master = fs.readFileSync(path.join(root, 'assets/branding/insane-mark.svg'), 'utf8');
const layers = [...master.matchAll(/<path fill="([^"]+)" d="([^"]+)"\s*\/>/g)]
  .map(([, fill, d]) => ({ fill, d }));
if (layers.length !== 8) throw new Error('Review the master SVG before exporting changed geometry.');
const background = '#101916';
const xml = '<?xml version="1.0" encoding="utf-8"?>\n';
const xmlns = 'xmlns:android="http://schemas.android.com/apk/res/android"';
function write(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
}
function paths(monochrome = false) {
  return (monochrome ? layers.slice(0, 2) : layers)
    .map(({ fill, d }) => `    <path android:fillColor="${monochrome ? '#FFFFFF' : fill}" android:pathData="${d}" />`).join('\n');
}
function vector(size, viewport, transform, monochrome = false) {
  return `${xml}<vector ${xmlns} android:width="${size}dp" android:height="${size}dp" android:viewportWidth="${viewport}" android:viewportHeight="${viewport}">\n  <group ${transform}>\n${paths(monochrome)}\n  </group>\n</vector>\n`;
}
// Maximum radius of the mark is <33dp around (54,54), inside the circular
// adaptive-icon safe zone. The background remains full bleed for all masks.
const adaptiveTransform = 'android:translateX="27.68" android:translateY="36.08" android:scaleX="0.56" android:scaleY="0.56"';
write(path.join(res, 'drawable/insane_app_foreground.xml'), vector(108, 108, adaptiveTransform));
write(path.join(res, 'drawable/insane_app_monochrome.xml'), vector(108, 108, adaptiveTransform, true));
write(path.join(res, 'drawable/ic_hyper_full.xml'), vector(32, 96, 'android:translateX="1" android:translateY="16"'));
for (const qualifier of ['drawable', 'drawable-night']) {
  write(path.join(res, qualifier, 'ic_launcher_foreground_themed.xml'), vector(108, 108, adaptiveTransform));
}
for (const api of [26, 33]) {
  const mono = api >= 33 ? '    <monochrome android:drawable="@drawable/insane_app_monochrome"/>\n' : '';
  const icon = `${xml}<adaptive-icon ${xmlns}>\n    <background android:drawable="@color/insane_icon_background"/>\n    <foreground android:drawable="@drawable/insane_app_foreground"/>\n${mono}</adaptive-icon>\n`;
  for (const name of ['ic_launcher', 'ic_launcher_round']) write(path.join(res, `mipmap-anydpi-v${api}/${name}.xml`), icon);
}
write(path.join(res, 'values/insane_brand.xml'), `${xml}<resources>\n    <color name="insane_icon_background">${background}</color>\n</resources>\n`);
for (const qualifier of ['values', 'values-night']) {
  write(path.join(res, qualifier, 'ic_launcher_background.xml'), `${xml}<resources>\n    <color name="ic_launcher_background">${background}</color>\n</resources>\n`);
}
function svgPaths(monochrome) {
  return (monochrome ? layers.slice(0, 2) : layers).map(({ fill, d }) => `<path fill="${monochrome ? '#FFFFFF' : fill}" d="${d}"/>`).join('');
}
function svg(body, size = 108) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">${body}</svg>`;
}
function adaptiveSvg(monochrome = false) {
  return svg(`<g transform="translate(27.68 36.08) scale(.56)">${svgPaths(monochrome)}</g>`);
}
function legacySvg(round = false) {
  const bg = round ? `<circle cx="54" cy="54" r="54" fill="${background}"/>` : `<rect width="108" height="108" rx="24" fill="${background}"/>`;
  return svg(`${bg}<g transform="translate(12.64 25.84) scale(.88)">${svgPaths(false)}</g>`);
}
function raster(source, pixels, file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  let output = sharp(Buffer.from(source), { density: 384 }).resize(pixels, pixels);
  output = file.endsWith('.webp') ? output.webp({ lossless: true }) : output.png();
  exportsPending.push(output.toFile(file));
}
for (const [density, scale] of [['mdpi', 1], ['hdpi', 1.5], ['xhdpi', 2], ['xxhdpi', 3], ['xxxhdpi', 4]]) {
  const mipmap = path.join(res, `mipmap-${density}`);
  raster(legacySvg(), 48 * scale, path.join(mipmap, 'ic_launcher.webp'));
  raster(legacySvg(true), 48 * scale, path.join(mipmap, 'ic_launcher_round.webp'));
  raster(adaptiveSvg(), 108 * scale, path.join(mipmap, 'ic_launcher_foreground.webp'));
  raster(adaptiveSvg(true), 108 * scale, path.join(mipmap, 'ic_launcher_monochrome.webp'));
  raster(svg(`<g transform="translate(2.208 5.344) scale(.208)">${svgPaths(true)}</g>`, 24), 24 * scale, path.join(res, `drawable-${density}/notif_icon.png`));
}
raster(svg(`<rect width="108" height="108" fill="${background}"/><g transform="translate(12.64 25.84) scale(.88)">${svgPaths(false)}</g>`), 512, path.join(main, 'ic_launcher-playstore.png'));
write(path.join(root, 'assets/branding/insane-icon.svg'), legacySvg() + '\n');
// This tracked copy is synchronized to the public website during release.
write(path.join(root, 'website/public/assets/logo.svg'), svg(`<title>INSANE CLIENT — emerald IC monogram</title><g transform="translate(1 16)">${svgPaths(false)}</g>`, 96) + '\n');
Promise.all(exportsPending).then(() => {
  console.log('Exported Android vector, adaptive, themed, notification and store icons from insane-mark.svg.');
}).catch(error => { console.error(error.message); process.exitCode = 1; });
