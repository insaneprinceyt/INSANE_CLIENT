import {readFile, readdir, mkdir, rm, writeFile, copyFile} from 'node:fs/promises';
import {join, relative, extname} from 'node:path';
const types = {'.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf', '.webp': 'image/webp', '.md': 'text/plain; charset=utf-8', '.txt': 'text/plain; charset=utf-8'};
const assets = {};
async function visit(directory) {
  for (const item of await readdir(directory, {withFileTypes: true})) {
    const path = join(directory, item.name);
    if (item.isDirectory()) await visit(path);
    else assets['/' + relative('public', path)] = {type: types[extname(path)] || 'application/octet-stream', body: (await readFile(path)).toString('base64')};
  }
}
await visit('public');
const releases = await readFile('worker/releases.json', 'utf8');
const routes = await readFile('worker/routes.js', 'utf8');
await rm('dist', {recursive: true, force: true});
await mkdir('dist/server', {recursive: true});
await mkdir('dist/.openai', {recursive: true});
await writeFile('dist/server/index.js', `const ASSETS = ${JSON.stringify(assets)};\nconst RELEASES = ${releases};\n${routes}`);
await copyFile('.openai/hosting.json', 'dist/.openai/hosting.json');
const entry = await readFile('dist/server/index.js', 'utf8');
const module = await import(`data:text/javascript;base64,${Buffer.from(entry).toString('base64')}`);
if (typeof module.default?.fetch !== 'function') throw new Error('Worker must export fetch');
console.log(`Built Worker with ${Object.keys(assets).length} embedded assets.`);
