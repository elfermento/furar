// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inflateSync } from 'node:zlib';

/**
 * Lectura mínima dun PDF de Chromium (`page.pdf`) para os e2e, sen dependencias: as etiquetas da
 * árbore de estrutura (`/S /Table`, `/Figure`…) e o texto real das páxinas (glifos traducidos
 * polo `/ToUnicode` de cada fonte). Abonda para comprobar que o PDF ten texto e táboas de
 * verdade e non unha imaxe; non é un lector de PDF xeral.
 */

interface PdfObject {
  readonly dict: string;
  readonly stream: Buffer | null;
}

function objects(pdf: Buffer): Map<number, PdfObject> {
  const source = pdf.toString('latin1');
  const found = new Map<number, PdfObject>();
  const pattern = /(\d+) 0 obj\s*([\s\S]*?)endobj/g;
  for (let match = pattern.exec(source); match; match = pattern.exec(source)) {
    const body = match[2]!;
    const at = body.search(/stream\r?\n/);
    if (at < 0) {
      found.set(Number(match[1]), { dict: body, stream: null });
      continue;
    }
    const dict = body.slice(0, at);
    const start = at + (body[at + 6] === '\r' ? 8 : 7);
    const end = body.lastIndexOf('endstream');
    let data = Buffer.from(body.slice(start, end).replace(/\r?\n$/, ''), 'latin1');
    if (/\/FlateDecode/.test(dict)) {
      try {
        data = inflateSync(data);
      } catch {
        data = Buffer.alloc(0);
      }
    }
    found.set(Number(match[1]), { dict, stream: data });
  }
  return found;
}

/** Os tipos da árbore de estrutura do PDF etiquetado (`Table`, `TH`, `TD`, `Figure`, `P`…). */
export function pdfTags(pdf: Buffer): Set<string> {
  const tags = new Set<string>();
  for (const { dict } of objects(pdf).values()) {
    if (!/\/StructElem/.test(dict) && !/\/S\s*\/\w+\s*\/P\s+\d+ 0 R/.test(dict)) continue;
    const kind = /\/S\s*\/(\w+)/.exec(dict);
    if (kind) tags.add(kind[1]!);
  }
  return tags;
}

interface CMap {
  readonly bytes: number;
  readonly map: Map<string, string>;
}

function utf16(hex: string): string {
  let out = '';
  for (let i = 0; i + 4 <= hex.length; i += 4) out += String.fromCharCode(parseInt(hex.slice(i, i + 4), 16));
  return out;
}

function parseCMap(source: string): CMap {
  const map = new Map<string, string>();
  const range = /begincodespacerange\s*<([0-9a-fA-F]+)>/.exec(source);
  const bytes = range ? range[1]!.length / 2 : 2;
  for (const block of source.matchAll(/beginbfchar([\s\S]*?)endbfchar/g)) {
    for (const pair of block[1]!.matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>/g)) {
      map.set(pair[1]!.toLowerCase(), utf16(pair[2]!));
    }
  }
  for (const block of source.matchAll(/beginbfrange([\s\S]*?)endbfrange/g)) {
    for (const line of block[1]!.matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>\s*(<[0-9a-fA-F]+>|\[[^\]]*\])/g)) {
      const low = parseInt(line[1]!, 16);
      const high = parseInt(line[2]!, 16);
      const width = line[1]!.length;
      const target = line[3]!;
      for (let code = low; code <= high; code++) {
        const key = code.toString(16).padStart(width, '0');
        if (target.startsWith('[')) {
          const items = [...target.matchAll(/<([0-9a-fA-F]+)>/g)].map((m) => m[1]!);
          if (items[code - low]) map.set(key, utf16(items[code - low]!));
        } else {
          const base = target.slice(1, -1);
          const last = parseInt(base.slice(-4), 16) + (code - low);
          map.set(key, utf16(base.slice(0, -4) + last.toString(16).padStart(4, '0')));
        }
      }
    }
  }
  return { bytes, map };
}

function decode(hex: string, cmap: CMap | undefined): string {
  if (!cmap) return '';
  const step = cmap.bytes * 2;
  let out = '';
  for (let i = 0; i + step <= hex.length; i += step) out += cmap.map.get(hex.slice(i, i + step).toLowerCase()) ?? '';
  return out;
}

/**
 * O texto das páxinas, sen espazos (Chromium coloca cada palabra por separado e non sempre
 * escribe o espazo): compárase co texto esperado tamén sen espazos (`squash`).
 */
export function pdfText(pdf: Buffer): string {
  const all = objects(pdf);
  // Fonte (número de obxecto) → CMap.
  const cmaps = new Map<number, CMap>();
  for (const [id, { dict }] of all) {
    const ref = /\/ToUnicode\s+(\d+) 0 R/.exec(dict);
    const stream = ref ? all.get(Number(ref[1]))?.stream : null;
    if (stream) cmaps.set(id, parseCMap(stream.toString('latin1')));
  }
  // Os nomes das fontes (/F3) son de cada páxina ou de cada formulario: cada contido co seu mapa.
  const fontsOf = (dict: string, depth = 0): Map<string, number> => {
    const names = new Map<string, number>();
    const inline = /\/Font\s*<<([^>]*)>>/.exec(dict);
    const ref = /\/Font\s+(\d+) 0 R/.exec(dict);
    const source = inline ? inline[1]! : ref ? (all.get(Number(ref[1]))?.dict ?? '') : null;
    if (source !== null) {
      for (const pair of source.matchAll(/\/([\w.+-]+)\s+(\d+) 0 R/g)) names.set(pair[1]!, Number(pair[2]));
      return names;
    }
    const resources = /\/Resources\s+(\d+) 0 R/.exec(dict);
    if (resources && depth < 2) return fontsOf(all.get(Number(resources[1]))?.dict ?? '', depth + 1);
    return names;
  };
  const owner = new Map<number, Map<string, number>>();
  for (const { dict } of all.values()) {
    if (!/\/Type\s*\/Page\b/.test(dict)) continue;
    const fonts = fontsOf(dict);
    const contents = /\/Contents\s*(\[[^\]]*\]|\d+ 0 R)/.exec(dict);
    for (const ref of contents?.[1]?.matchAll(/(\d+) 0 R/g) ?? []) owner.set(Number(ref[1]), fonts);
  }
  let text = '';
  for (const [id, { dict, stream }] of all) {
    if (!stream) continue;
    const content = stream.toString('latin1');
    if (!/\bTf\b/.test(content)) continue;
    const names = /\/Resources/.test(dict) ? fontsOf(dict) : (owner.get(id) ?? new Map<string, number>());
    let font: CMap | undefined;
    const tokens = /\/([\w.+-]+)\s+[\d.]+\s+Tf|<([0-9a-fA-F]*)>|\bET\b/g;
    for (let m = tokens.exec(content); m; m = tokens.exec(content)) {
      if (m[1]) font = cmaps.get(names.get(m[1]) ?? -1);
      else if (m[2] !== undefined) text += decode(m[2], font);
      else text += ' ';
    }
  }
  return squash(text);
}

/** Sen espazos de ningún tipo, para comparar co texto do PDF. */
export function squash(value: string): string {
  return value.replace(/\s+/g, '');
}
