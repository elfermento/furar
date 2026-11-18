// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** As entradas que se ven e as que van a «Máis». */
export interface Overflow<T> {
  readonly visible: readonly T[];
  readonly more: readonly T[];
}

/**
 * Reparte `items` nunha barra de como moito `max` sitios (docs/deseno.md §6: a barra inferior do
 * móbil leva 4 para que os nomes non partan a 320 px). Se caben todos, ningún vai a «Máis»; se
 * non, vense os `max − 1` primeiros e o último sitio é «Máis» co resto, na mesma orde.
 */
export function overflow<T>(items: readonly T[], max: number): Overflow<T> {
  if (items.length <= max) return { visible: items, more: [] };
  const shown = Math.max(0, max - 1);
  return { visible: items.slice(0, shown), more: items.slice(shown) };
}
