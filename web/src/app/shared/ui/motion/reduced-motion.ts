// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Se hai que evitar o movemento: o que di `data-motion` en `<html>` (a preferencia da persoa ou,
 * sen ela, a do sistema; core/preferences/) e, se aínda non está, `prefers-reduced-motion`.
 * Para o movemento que fai o código (Web Animations, desprazamentos, gráficos, mapas); o CSS le
 * o atributo directamente (styles/base.css).
 */
export function reducedMotion(view: Window | null | undefined): boolean {
  const chosen = view?.document?.documentElement?.getAttribute('data-motion');
  if (chosen === 'reduce') return true;
  if (chosen === 'no-preference') return false;
  return view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}
