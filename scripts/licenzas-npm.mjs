#!/usr/bin/env node
// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// D04 · Licenzas da web (ADR-0002). Le na entrada estándar o JSON de
// `license-checker-rseidelsohn --json` e avalía a expresión SPDX COMPLETA de cada paquete:
//   AND → todas as licenzas permitidas; OR → polo menos unha; «id WITH excepción» → a
//   parella exacta tal como está en scripts/allowed-licenses.txt.
// Falla con «*» (licenza adiviñada desde o README), UNKNOWN, UNLICENSED, «Custom:», «SEE
// LICENSE IN…» ou calquera identificador fóra da lista. (O --onlyAllow da ferramenta compara
// por subcadea: «MIT AND SSPL-1.0» ou «MIT*» pasarían; por iso non se usa.)
//
//   … --json | node scripts/licenzas-npm.mjs                      sen excepcións (execución)
//   … --json | node scripts/licenzas-npm.mjs --excepcions FICH    con excepcións nominais (desenvolvemento)
//   node scripts/licenzas-npm.mjs --autoproba                    proba que a garda falla de verdade
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const lerLista = (f) =>
  readFileSync(f, 'utf8').split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));

// Excepción: «paquete LICENZA: motivo». Só permite ESA licenza extra nese paquete.
function lerExcepcions(f) {
  const m = new Map();
  for (const l of lerLista(f)) {
    const r = /^(\S+)\s+([^:]+):\s*(.+)$/.exec(l);
    if (!r) throw new Error(`excepción mal formada (paquete LICENZA: motivo): ${l}`);
    m.set(r[1], r[2].trim());
  }
  return m;
}

// --- Analizador de expresións SPDX (AND, OR, WITH, parénteses; AND liga máis que OR) ----------
function tokens(expr) {
  const t = expr.replace(/[()]/g, ' $& ').trim().split(/\s+/).filter(Boolean);
  if (!t.length) throw new Error('expresión baleira');
  return t;
}
function analizar(expr) {
  const t = tokens(expr);
  let i = 0;
  const op = (nome) => t[i] && t[i].toUpperCase() === nome;
  const termo = () => {
    if (t[i] === '(') {
      i++;
      const n = ou();
      if (t[i++] !== ')') throw new Error(`falta «)» en «${expr}»`);
      return n;
    }
    const id = t[i++];
    if (!id || ['AND', 'OR', 'WITH', ')'].includes(id.toUpperCase())) throw new Error(`expresión inválida «${expr}»`);
    if (op('WITH')) {
      i++;
      const exc = t[i++];
      if (!exc) throw new Error(`falta a excepción en «${expr}»`);
      return { id: `${id} WITH ${exc}` };
    }
    return { id };
  };
  const e = () => {
    const xs = [termo()];
    while (op('AND')) { i++; xs.push(termo()); }
    return xs.length === 1 ? xs[0] : { and: xs };
  };
  const ou = () => {
    const xs = [e()];
    while (op('OR')) { i++; xs.push(e()); }
    return xs.length === 1 ? xs[0] : { or: xs };
  };
  const n = ou();
  if (i !== t.length) throw new Error(`sobra «${t.slice(i).join(' ')}» en «${expr}»`);
  return n;
}
const NON_SPDX = /^(UNKNOWN|UNLICENSED|Custom:|SEE LICENSE IN)/i;
function cumpre(nodo, permitidas) {
  if (nodo.and) return nodo.and.every((n) => cumpre(n, permitidas));
  if (nodo.or) return nodo.or.some((n) => cumpre(n, permitidas));
  return permitidas.has(nodo.id);
}

// Devolve a lista de problemas (baleira = todo correcto).
export function revisar(informe, permitidas, excepcions = new Map()) {
  const problemas = [];
  for (const [chave, datos] of Object.entries(informe)) {
    const nome = chave.replace(/@[^@]+$/, '');
    const lic = datos.licenses;
    const exprs = Array.isArray(lic) ? lic : [lic ?? 'UNKNOWN'];
    // Varias licenzas en lista (campo antigo «licenses»): esíxense todas, coma un AND.
    const expr = exprs.map((x) => `(${x})`).join(' AND ');
    const extra = excepcions.get(nome);
    const aceptadas = extra ? new Set([...permitidas, extra]) : permitidas;
    if (exprs.some((x) => typeof x !== 'string' || x.includes('*') || NON_SPDX.test(x.trim()))) {
      problemas.push(`${chave}: licenza non declarada en SPDX («${exprs.join(', ')}»)`);
      continue;
    }
    try {
      if (!cumpre(analizar(expr), aceptadas)) problemas.push(`${chave}: ${exprs.join(', ')} non está permitida`);
    } catch (err) {
      problemas.push(`${chave}: ${err.message}`);
    }
  }
  // Unha excepción que xa non se usa debe borrarse (non deixar portas abertas).
  for (const nome of excepcions.keys()) {
    if (!Object.keys(informe).some((k) => k.replace(/@[^@]+$/, '') === nome)) {
      problemas.push(`excepción sen uso en license-exceptions.txt: ${nome}`);
    }
  }
  return problemas;
}

function autoproba() {
  const permitidas = new Set(['MIT', 'Apache-2.0', 'BSD-3-Clause', 'GPL-2.0-only WITH Classpath-exception-2.0']);
  const casos = [
    ['MIT', true],
    ['(MIT OR SSPL-1.0)', true],
    ['(MIT AND Apache-2.0)', true],
    ['GPL-2.0-only WITH Classpath-exception-2.0', true],
    ['(MIT AND SSPL-1.0)', false],
    ['MIT AND Commons-Clause', false],
    ['(BUSL-1.1 AND MIT)', false],
    ['MIT*', false],
    ['MIT-0', false], // subcadea de MIT: aquí non está permitida
    ['UNKNOWN', false],
    ['UNLICENSED', false],
    ['Custom: LICENSE.md', false],
    ['GPL-2.0-only', false],
    ['(MIT OR', false],
  ];
  let mal = 0;
  for (const [lic, esperado] of casos) {
    const ok = revisar({ [`falso@1.0.0`]: { licenses: lic } }, permitidas).length === 0;
    console.log(`${ok === esperado ? '✓' : '✗'} ${lic} → ${ok ? 'acepta' : 'rexeita'}`);
    if (ok !== esperado) mal++;
  }
  // Excepción nominal: só ese paquete e só esa licenza.
  const exc = new Map([['datos', 'CC-BY-3.0']]);
  const r1 = revisar({ 'datos@1.0.0': { licenses: '(MIT AND CC-BY-3.0)' } }, permitidas, exc).length === 0;
  const r2 = revisar({ 'datos@1.0.0': { licenses: '(MIT AND CC-BY-4.0)' } }, permitidas, exc).length === 0;
  const r3 = revisar({ 'outro@1.0.0': { licenses: 'CC-BY-3.0' } }, permitidas, new Map()).length === 0;
  for (const [txt, obtido, esperado] of [
    ['excepción aplicada ao seu paquete', r1, true],
    ['excepción con outra licenza', r2, false],
    ['excepción noutro paquete', r3, false],
  ]) {
    console.log(`${obtido === esperado ? '✓' : '✗'} ${txt}`);
    if (obtido !== esperado) mal++;
  }
  if (mal) { console.error(`autoproba: ${mal} casos mal`); process.exit(1); }
  console.log('autoproba OK');
}

const args = process.argv.slice(2);
if (args[0] === '--autoproba') {
  autoproba();
} else {
  const i = args.indexOf('--excepcions');
  const excepcions = i >= 0 ? lerExcepcions(args[i + 1]) : new Map();
  const permitidas = new Set(lerLista(join(aqui, 'allowed-licenses.txt')));
  const informe = JSON.parse(readFileSync(0, 'utf8'));
  const n = Object.keys(informe).length;
  if (n === 0) { console.error('informe baleiro: non se comprobou nada'); process.exit(1); }
  const problemas = revisar(informe, permitidas, excepcions);
  if (problemas.length) {
    console.error(problemas.join('\n'));
    process.exit(1);
  }
  console.log(`${n} paquetes con licenza permitida${excepcions.size ? ` (${excepcions.size} excepcións nominais)` : ' (sen excepcións)'}`);
}
