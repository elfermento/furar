// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Unha cela do CSV: texto tal cal, número (decimais con coma) ou baleira. */
export type CsvCell = string | number | null | undefined;

/** Unha fila; unha fila baleira (`[]`) separa os bloques dun ficheiro con varias táboas. */
export type CsvRow = readonly CsvCell[];

/** Marca de orde de bytes: Excel só le ben o UTF-8 (acentos, «€») se o ficheiro empeza por ela. */
const BOM = '﻿';
const SEPARATOR = ';';
const LINE_END = '\r\n';

/**
 * Inxección de fórmulas (OWASP «CSV Injection»): unha folla de cálculo executa as celas que
 * empezan por `=`, `+`, `-` ou `@`, e tamén as que empezan por tabulador ou retorno de carro.
 */
const FORMULA_START = /^[=+\-@\t\r]/;

/** Cela de texto: neutraliza a fórmula cun `'` diante e, se fai falta, comiñas (RFC 4180). */
function text(value: string): string {
  const safe = FORMULA_START.test(value) ? `'${value}` : value;
  return /[";\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

/**
 * Número sen separador de miles e coa coma decimal (a configuración española e galega das follas
 * de cálculo). Os números non se neutralizan: `-3` é un número, non unha fórmula.
 */
function number(value: number): string {
  return Number.isFinite(value) ? String(value).replace('.', ',') : '';
}

function cell(value: CsvCell): string {
  if (value === null || value === undefined) return '';
  return typeof value === 'number' ? number(value) : text(value);
}

/**
 * CSV para Excel e LibreOffice en galego ou castelán (ADR-0026): UTF-8 con BOM, `;` entre celas,
 * CRLF ao final de cada fila e comiñas RFC 4180. Os textos chegan xa traducidos e formatados.
 */
export function toCsv(rows: readonly CsvRow[]): string {
  return BOM + rows.map((row) => row.map(cell).join(SEPARATOR) + LINE_END).join('');
}

/**
 * Descarga o CSV no navegador (sen servidor): un `Blob` e un `<a download>`, como o `.ics` das
 * reservas. `fileName` leva a extensión (`indicadores-naron-2026-10.csv`).
 */
export function downloadCsv(document: Document, fileName: string, rows: readonly CsvRow[]): void {
  const view = document.defaultView;
  if (!view) return;
  const url = view.URL.createObjectURL(new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  view.setTimeout(() => view.URL.revokeObjectURL(url), 0);
}
