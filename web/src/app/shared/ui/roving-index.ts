// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Frechas en horizontal: dereita avanza, esquerda volve. */
export const HORIZONTAL: Readonly<Record<string, 1 | -1>> = { ArrowRight: 1, ArrowLeft: -1 };
/** Frechas en horizontal e en vertical (radiogroup): dereita e abaixo avanzan. */
export const BOTH_AXES: Readonly<Record<string, 1 | -1>> = { ...HORIZONTAL, ArrowDown: 1, ArrowUp: -1 };

/**
 * Índice ao que vai o foco nun grupo cunha soa parada de tabulador (tabindex itinerante, APG):
 * as frechas de `steps` dan a volta, Inicio e Fin van aos extremos. `null` se a tecla non é do grupo.
 */
export function rovingIndex(
  key: string,
  current: number,
  length: number,
  steps: Readonly<Record<string, 1 | -1>>,
): number | null {
  if (length === 0 || current < 0) return null;
  if (key === 'Home') return 0;
  if (key === 'End') return length - 1;
  const step = steps[key];
  return step === undefined ? null : (current + step + length) % length;
}
