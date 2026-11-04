// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// postbuild: completa dist/furar-web/3rdpartylicenses.txt cos paquetes que Angular non lista
// porque non entran no bundle JS pero si se redistribúen: as tipografías de @fontsource
// (OFL-1.1 §2 esixe acompañalas da licenza) e a folla de Leaflet (`leaflet.css`, cargada por
// furar-map; BSD-2-Clause tamén pide o aviso). Engade tamén o NOTICE de Apache ECharts
// (Apache-2.0 §4.d) e a licenza de d3 que ECharts leva dentro, cando Angular lista ECharts
// (é dicir, cando o seu JS vai nalgún chunk). Falla se falta o ficheiro de Angular ou se hai
// unha tipografía en media/ que non vén de ningunha dependencia @fontsource (LIC-3).
// Uso: node scripts/third-party-notices.mjs [--dist DIR]
import { appendFileSync, existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = fileURLToPath(new URL('..', import.meta.url));
const args = process.argv.slice(2);
const dist = args.includes('--dist') ? args[args.indexOf('--dist') + 1] : join(webRoot, 'dist/furar-web');
const notices = join(dist, '3rdpartylicenses.txt');
const media = join(dist, 'browser/media');

if (!existsSync(notices)) {
  console.error(`third-party-notices: non existe ${notices} (extractLicenses desactivado?)`);
  process.exit(1);
}
const listed = readFileSync(notices, 'utf8');
const fonts = existsSync(media) ? readdirSync(media).filter((f) => /\.(woff2?|ttf|otf)$/.test(f)) : [];
const { dependencies = {} } = JSON.parse(readFileSync(join(webRoot, 'package.json'), 'utf8'));
const attributed = new Set();
const added = [];

function attribute(name) {
  if (listed.includes(`Package: ${name}\n`)) return;
  const dir = join(webRoot, 'node_modules', name);
  const { license } = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'));
  const text = readFileSync(join(dir, 'LICENSE'), 'utf8').trim();
  appendFileSync(notices, `\n${'-'.repeat(80)}\nPackage: ${name}\nLicense: "${license}"\n\n${text}\n`);
  added.push(`${name} (${license})`);
}

for (const name of Object.keys(dependencies).filter((d) => d.startsWith('@fontsource/'))) {
  const family = name.slice('@fontsource/'.length);
  const shipped = fonts.filter((f) => f.startsWith(`${family}-`));
  if (shipped.length === 0) continue;
  shipped.forEach((f) => attributed.add(f));
  attribute(name);
}

// A folla de Leaflet (e as súas imaxes en media/) sae aínda que ningunha ruta de produción
// use o mapa; o JS, cando entre nun chunk, xa o lista Angular.
if ('leaflet' in dependencies && existsSync(join(dist, 'browser/leaflet.css'))) {
  attribute('leaflet');
}

// Apache-2.0 §4.d: quen redistribúe a obra ten que levar o NOTICE. Angular só copia o LICENSE,
// que remite a licenses/LICENSE-d3 para os ficheiros de d3 que ECharts inclúe.
if (listed.includes('Package: echarts\n')) {
  const dir = join(webRoot, 'node_modules/echarts');
  const notice = readFileSync(join(dir, 'NOTICE'), 'utf8').trim();
  const d3 = readFileSync(join(dir, 'licenses/LICENSE-d3'), 'utf8').trim();
  appendFileSync(
    notices,
    `\n${'-'.repeat(80)}\nPackage: echarts (NOTICE)\n\n${notice}\n\n` +
      `${'-'.repeat(80)}\nPackage: echarts (licenses/LICENSE-d3, BSD-3-Clause)\n\n${d3}\n`,
  );
  added.push('echarts (NOTICE e LICENSE-d3)');
}

const orphans = fonts.filter((f) => !attributed.has(f));
if (orphans.length) {
  console.error(`third-party-notices: tipografías sen paquete nin licenza: ${orphans.join(', ')}`);
  process.exit(1);
}
console.log(`third-party-notices: ${added.length ? `engadido ${added.join(', ')}` : 'nada que engadir'}.`);
