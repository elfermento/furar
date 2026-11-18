// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * NIF da entidade responsable, coas mesmas regras ca `EntityNif` da API: letra de entidade, 7
 * díxitos e o carácter de control, díxito ou a súa letra equivalente (`JABCDEFGHI`) para todas as
 * letras. Un DNI ou NIE dá un erro propio.
 */
const FORMAT = /^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$/;
const PERSON = /^(?:[0-9]{8}[A-Z]|[XYZKLM][0-9]{7}[A-Z])$/;
const CONTROL_LETTERS = 'JABCDEFGHI';

/** Os códigos de erro, os mesmos ca os da API (o de forma é propio da web, coma nos outros campos). */
export const NIF_ERRORS = {
  personal: 'municipality.nif-personal',
  format: 'legal.nif',
  checkDigit: 'municipality.nif-check-digit',
} as const;
export type NifError = (typeof NIF_ERRORS)[keyof typeof NIF_ERRORS];

/** Lonxitude dun NIF: con ela xa se comproba mentres se escribe. */
export const NIF_LENGTH = 9;

/** Maiúsculas e sen espazos, puntos nin guións. */
export function normaliseNif(value: string): string {
  return value.replace(/[\s.-]/g, '').toUpperCase();
}

function control(digits: string): number {
  let sum = 0;
  for (let i = 0; i < digits.length; i++) {
    const n = Number(digits[i]);
    if (i % 2 === 0) {
      const doubled = 2 * n;
      sum += Math.floor(doubled / 10) + (doubled % 10);
    } else {
      sum += n;
    }
  }
  return (10 - (sum % 10)) % 10;
}

/** O erro do NIF (normalízao antes), ou `null` se vale ou está baleiro. */
export function nifError(value: string): NifError | null {
  const nif = normaliseNif(value);
  if (!nif) return null;
  if (PERSON.test(nif)) return NIF_ERRORS.personal;
  if (!FORMAT.test(nif)) return NIF_ERRORS.format;
  const c = control(nif.slice(1, 8));
  const given = nif[8];
  return given === String(c) || given === CONTROL_LETTERS[c] ? null : NIF_ERRORS.checkDigit;
}
