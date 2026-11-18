// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// postbuild: comproba o ngsw.json xerado (ADR-0014, ADR-0026). A veciñanza non debe precargar o
// código da xestión (ECharts, o editor de texto e os indicadores, entre outros): van no grupo
// `lazy`, que vai antes de `app` (cada ficheiro queda no primeiro grupo que o colle). E o marco sen conexión segue
// enteiro: index.html, main, os estilos e os chunks que main importa ao arrancar, en `prefetch`.
// Falla tamén se un patrón do grupo `lazy` xa non colle ningún ficheiro (un chunk renomeado
// volvería á precarga sen que ninguén o vise).
// Uso: node scripts/ngsw-check.mjs [--dist DIR]
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = fileURLToPath(new URL('..', import.meta.url));
const args = process.argv.slice(2);
const dist = args.includes('--dist') ? args[args.indexOf('--dist') + 1] : join(webRoot, 'dist/furar-web');
const browser = join(dist, 'browser');
const manifestPath = join(browser, 'ngsw.json');
const errors = [];

if (!existsSync(manifestPath)) {
  console.error(`ngsw-check: non existe ${manifestPath} (build sen serviceWorker?)`);
  process.exit(1);
}
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const config = JSON.parse(readFileSync(join(webRoot, 'ngsw-config.json'), 'utf8'));
const groups = manifest.assetGroups;
const lazy = groups.find((g) => g.name === 'lazy');
const app = groups.find((g) => g.name === 'app');

/** Glob de ngsw-config (`*` sen barras, `**` con elas) como expresión regular. */
const globToRegex = (glob) =>
  new RegExp(
    '^' +
      glob
        .split('**')
        .map((part) => part.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*'))
        .join('.*') +
      '$',
  );

if (!lazy || !app) {
  errors.push('faltan os grupos «lazy» e «app»');
} else {
  if (groups.indexOf(lazy) > groups.indexOf(app)) errors.push('o grupo «lazy» ten que ir antes de «app»');
  if (lazy.installMode !== 'lazy' || lazy.updateMode !== 'lazy') errors.push('«lazy» ten que ser lazy/lazy');
  // O editor de texto (TipTap) tampouco se precarga: só o pide «Datos do concello» ao escribir un texto propio.
  for (const required of [
    /^\/echarts-setup-[^/]+\.js$/,
    /^\/rich-text-setup-[^/]+\.js$/,
    /^\/indicators\.page-[^/]+\.js$/,
    /^\/indicadores\.css$/,
  ]) {
    if (!lazy.urls.some((url) => required.test(url))) errors.push(`${required} non está no grupo «lazy»`);
  }
  const patterns = config.assetGroups.find((g) => g.name === 'lazy')?.resources?.files ?? [];
  for (const pattern of patterns) {
    if (!lazy.urls.some((url) => globToRegex(pattern).test(url))) {
      errors.push(`o patrón ${pattern} do grupo «lazy» non colle ningún ficheiro`);
    }
  }
}

// Marco sen conexión: index.html, os seus estilos e scripts e o que main importa ao arrancar.
const index = readFileSync(join(browser, 'index.html'), 'utf8');
const initial = new Set(['/index.html']);
const pending = [...index.matchAll(/["']([\w.-]+\.(?:js|css))["']/g)].map((m) => m[1]);
while (pending.length > 0) {
  const file = pending.pop();
  if (initial.has(`/${file}`) || !existsSync(join(browser, file))) continue;
  initial.add(`/${file}`);
  if (file.endsWith('.js')) {
    // Só os imports estáticos (`from"./x.js"`, `import"./x.js"`), non os `import("./x.js")`.
    const code = readFileSync(join(browser, file), 'utf8');
    for (const m of code.matchAll(/(?:\bfrom|\bimport)\s*["']\.\/([\w.-]+\.js)["']/g)) pending.push(m[1]);
  }
}
if (![...initial].some((url) => /^\/main-[^/]+\.js$/.test(url))) errors.push('non se atopou main-*.js en index.html');
const prefetched = new Set(groups.filter((g) => g.installMode === 'prefetch').flatMap((g) => g.urls));
for (const url of initial) {
  if (!prefetched.has(url)) errors.push(`${url} é da carga inicial e non está nun grupo prefetch`);
}

const kib = (urls) => (urls.reduce((sum, url) => sum + statSync(join(browser, url)).size, 0) / 1024).toFixed(1);
if (errors.length > 0) {
  for (const error of errors) console.error(`ngsw-check: ${error}`);
  process.exit(1);
}
console.log(
  `ngsw-check: precarga ${kib([...prefetched])} kB (${prefetched.size} ficheiros); ` +
    `grupo lazy ${kib(lazy.urls)} kB (${lazy.urls.length}); marco inicial (${initial.size}) precargado.`,
);
