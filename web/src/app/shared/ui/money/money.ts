// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Importes en euros (prezos dos usos e das reservas). A API fala en céntimos enteiros; a web
 * escribe «30,00 €» (gl e es: coma decimal e o símbolo despois, cun espazo que non parte) sen
 * depender de `Intl`, que nalgúns navegadores non ten galego.
 */
/** Espazo que non parte entre o importe e o símbolo. */
const NBSP = String.fromCharCode(0xa0);

export function formatCents(cents: number): string {
  const sign = cents < 0 ? '-' : '';
  const abs = Math.abs(Math.round(cents));
  const euros = Math.floor(abs / 100)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${sign}${euros},${String(abs % 100).padStart(2, '0')}${NBSP}€`;
}

/** O importe no campo de texto, sen símbolo: «30,00». */
export function centsToInput(cents: number | null | undefined): string {
  return cents === null || cents === undefined ? '' : formatCents(cents).slice(0, -2);
}

/**
 * Céntimos do que escribiu a persoa («30», «30,5», «1.000,00», «12.50 €») ou `null` se non é un
 * importe con ata dous decimais. Baleiro tamén é `null`: quen o usa decide se é obrigatorio.
 */
export function parseCents(text: string | null | undefined): number | null {
  let value = (text ?? '').replace(/[\s€]/g, '');
  if (!value) return null;
  if (value.includes(',')) value = value.replace(/\./g, '').replace(',', '.');
  else if (/^\d{1,3}(\.\d{3})+$/.test(value)) value = value.replace(/\./g, '');
  if (!/^\d+(\.\d{1,2})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}
