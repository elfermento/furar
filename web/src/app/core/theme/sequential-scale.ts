// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { mix } from './color';
import { NON_TEXT_CONTRAST, contrastRatio, luminance } from './contrast';
import type { DerivedTokens } from './theme-tokens';

/** Contraste do primeiro paso sobre a superficie: pouco, pero distinto do fondo. */
const FIRST_CONTRAST = 1.15;
/** Finura da busca da mestura (1/400 de paso). */
const STEPS = 400;

/**
 * Escala secuencial para cantidades nos gráficos (docs/deseno.md §3.1, ADR-0026): `n` cores que
 * van desde case a superficie ata o `primary` do concello, nesa orde. O último paso é o
 * `primary` (ou, se non chegase, o `primary` achegado ao extremo ata dar 3:1 sobre `surface`,
 * WCAG 1.4.11). Os pasos intermedios repártense en progresión xeométrica do contraste sobre a
 * superficie: así a luminancia é monótona e dous pasos seguidos sepáranse sempre na mesma razón,
 * en tema claro e en escuro. Os estados nunca usan esta escala.
 */
export function sequentialScale(tokens: Pick<DerivedTokens, 'primary' | 'surface'>, n: number): string[] {
  if (!Number.isInteger(n) || n < 1) return [];
  const { surface } = tokens;
  const top = strongest(tokens.primary, surface);
  if (n === 1) return [top];
  const darker = luminance(top) < luminance(surface);
  const topContrast = contrastRatio(top, surface);
  const first = Math.min(FIRST_CONTRAST, topContrast);
  const scale: string[] = [];
  for (let i = 0; i < n - 1; i++) {
    const target = first * (topContrast / first) ** (i / (n - 1));
    scale.push(stepAt(top, surface, target, darker));
  }
  scale.push(top);
  return scale;
}

/** O `primary` se xa dá 3:1 sobre a superficie; se non, achegado ao negro (claro) ou ao branco (escuro). */
function strongest(primary: string, surface: string): string {
  if (contrastRatio(primary, surface) >= NON_TEXT_CONTRAST) return primary;
  const far = luminance(surface) < 0.18 ? '#ffffff' : '#000000';
  for (let step = 1; step <= STEPS; step++) {
    const candidate = mix(far, primary, step / STEPS);
    if (contrastRatio(candidate, surface) >= NON_TEXT_CONTRAST) return candidate;
  }
  return far;
}

/** A primeira mestura de `top` sobre `surface` que chega a `target`, sempre do mesmo lado ca `top`. */
function stepAt(top: string, surface: string, target: number, darker: boolean): string {
  const surfaceLuminance = luminance(surface);
  for (let step = 1; step <= STEPS; step++) {
    const candidate = mix(top, surface, step / STEPS);
    const sameSide = darker ? luminance(candidate) < surfaceLuminance : luminance(candidate) > surfaceLuminance;
    if (sameSide && contrastRatio(candidate, surface) >= target) return candidate;
  }
  return top;
}
