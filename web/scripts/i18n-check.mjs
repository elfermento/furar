// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Comprobación de textos (D25, ADR-0014). Falla se:
//  1. gl.json e es.json non teñen as mesmas claves, hai textos baleiros ou cambian os {{parámetros}};
//  2. falta errors.<code> para algún código de erro do contrato (Problem.code de api/openapi.json);
//  3. falta o texto dalgún valor de enumeración do contrato que a web pinta (tipos, visibilidade...);
//  4. o código usa unha clave que non existe, ou hai claves sen uso (errors.* queda fóra: veñen da API);
//  5. hai texto solto nun modelo (HTML ou template inline): entre etiquetas, en atributos de
//     texto ou como literal nun binding/interpolación que chega á pantalla (template-text.mjs).
// Uso: node scripts/i18n-check.mjs [--root DIR]
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseTemplate } from '@angular/compiler';
import { findLooseText } from './template-text.mjs';

const args = process.argv.slice(2);
const webRoot = args.includes('--root')
  ? args[args.indexOf('--root') + 1]
  : fileURLToPath(new URL('..', import.meta.url));
const LANGS = ['gl', 'es'];
const failures = [];
const fail = (message) => failures.push(message);

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
function flatten(node, prefix = '', out = new Map()) {
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object') {
      flatten(value, path, out);
    } else {
      out.set(path, value);
    }
  }
  return out;
}
const params = (text) =>
  [...String(text).matchAll(/\{\{\s*(\w+)\s*\}\}/g)]
    .map((m) => m[1])
    .sort()
    .join(',');

// 1. Paridade gl/es
const texts = Object.fromEntries(
  LANGS.map((lang) => [lang, flatten(readJson(join(webRoot, 'src/i18n', `${lang}.json`)))]),
);
for (const [a, b] of [
  ['gl', 'es'],
  ['es', 'gl'],
]) {
  for (const key of texts[a].keys()) {
    if (!texts[b].has(key)) fail(`${b}.json: falta a clave ${key} (está en ${a}.json)`);
  }
}
for (const lang of LANGS) {
  for (const [key, value] of texts[lang]) {
    if (typeof value !== 'string' || value.trim() === '') fail(`${lang}.json: ${key} está baleira ou non é texto`);
  }
}
for (const [key, value] of texts.gl) {
  if (texts.es.has(key) && params(value) !== params(texts.es.get(key))) {
    fail(`${key}: os parámetros {{…}} non coinciden entre gl e es`);
  }
}

// 2 e 3. Contrato
const openapi = readJson(join(webRoot, '../api/openapi.json'));
const schemas = openapi.components?.schemas ?? {};
const errorCodes = new Set([
  ...(schemas.Problem?.properties?.code?.enum ?? []),
  ...(schemas.FieldError?.properties?.code?.enum ?? []),
]);
if (errorCodes.size === 0) fail('openapi.json: non hai enum de códigos en Problem.code');
for (const code of errorCodes) {
  for (const lang of LANGS) {
    if (!texts[lang].has(`errors.${code}`)) fail(`${lang}.json: falta errors.${code} (código de erro do contrato)`);
  }
}
const enumOf = (schema, property) => schemas[schema]?.properties?.[property]?.enum ?? [];
const required = [
  ...enumOf('CustomFieldDefinitionResponse', 'type').map((v) => `customFields.type.${v}`),
  ...enumOf('CustomFieldDefinitionResponse', 'visibility').flatMap((v) => [
    `customFields.visibility.${v}`,
    `customFields.visibilityHelp.${v}`,
  ]),
  ...enumOf('FieldRulesResponse', 'format').map((v) => `customFields.format.${v}`),
  ...Object.keys(schemas.FieldRulesResponse?.properties ?? {}).map((v) => `customFields.rules.${v}`),
  ...Object.keys(schemas.ThemeResponse?.properties ?? {}).map((v) => `theme.${v}`),
  ...enumOf('MunicipalityResponse', 'defaultLanguage').flatMap((v) => [`lang.${v}`, `lang.endonym.${v}`]),
];
for (const key of required) {
  for (const lang of LANGS) {
    if (!texts[lang].has(key)) fail(`${lang}.json: falta ${key} (valor do contrato)`);
  }
}

// 4. Claves usadas no código
function files(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      if (path !== join(webRoot, 'src/app/api')) files(path, out);
    } else if (/\.(ts|html)$/.test(name) && !name.endsWith('.spec.ts')) {
      out.push(path);
    }
  }
  return out;
}
const sources = files(join(webRoot, 'src/app'));
const namespaces = new Set([...texts.gl.keys()].map((key) => key.split('.')[0]));
const used = new Set();
const prefixes = new Set();
for (const path of sources) {
  const source = readFileSync(path, 'utf8');
  for (const [, literal] of source.matchAll(/['`]([a-zA-Z][\w-]*(?:\.[\w-]+)*\.?)['`]/g)) {
    if (!literal.includes('.') || !namespaces.has(literal.split('.')[0])) continue;
    if (texts.gl.has(`errors.${literal}`)) continue; // código de erro (common.network...), non clave
    if (literal.endsWith('.')) {
      prefixes.add(literal);
    } else if (texts.gl.has(literal)) {
      used.add(literal);
    } else if (![...texts.gl.keys()].some((key) => key.startsWith(`${literal}.`))) {
      fail(`${relative(webRoot, path)}: a clave «${literal}» non existe en gl.json`);
    }
  }
}
for (const key of texts.gl.keys()) {
  if (key.startsWith('errors.') || required.includes(key) || used.has(key)) continue;
  if ([...prefixes].some((prefix) => key.startsWith(prefix))) continue;
  fail(`gl.json: a clave ${key} non se usa`);
}

// 5. Texto solto nos modelos (template-text.mjs): árbore do compilador de Angular, con
// texto entre etiquetas, atributos de texto e literais que chegan á saída nos bindings.
function templatesOf(path, source) {
  if (path.endsWith('.html')) return [{ template: source, line: 0 }];
  return [...source.matchAll(/template:\s*`([\s\S]*?)`,?\n\s*\}\)/g)].map((m) => ({
    template: m[1],
    line: source.slice(0, m.index).split('\n').length - 1,
  }));
}
const isKey = (literal) =>
  texts.gl.has(literal) || (literal.endsWith('.') && [...texts.gl.keys()].some((key) => key.startsWith(literal)));
for (const path of sources) {
  const source = readFileSync(path, 'utf8');
  for (const { template, line } of templatesOf(path, source)) {
    const parsed = parseTemplate(template, path, { preserveWhitespaces: false });
    for (const error of parsed.errors ?? []) fail(`${relative(webRoot, path)}: modelo non válido: ${error.msg}`);
    findLooseText(parsed.nodes, isKey, (message, span) =>
      fail(`${relative(webRoot, path)}:${line + span.start.line + 1}: ${message}`),
    );
  }
}

if (failures.length) {
  console.error(failures.map((f) => `✗ ${f}`).join('\n'));
  console.error(`\ni18n:check: ${failures.length} problema(s).`);
  process.exit(1);
}
console.log(
  `i18n:check: ${texts.gl.size} claves en gl e es, ${errorCodes.size} códigos de erro do contrato, sen texto solto.`,
);
