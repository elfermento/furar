// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { mix, onFill } from './color';
import { TEXT_CONTRAST, contrastRatio } from './contrast';

/**
 * Paleta de estados (docs/deseno.md §3.1, 2026-10-26): tons de partida sen tema, todos con letra
 * branca a ≥ 4,5:1. Como texto, a derivación achégaos á tinta (claro) ou sóbeos de luz (escuro);
 * como recheo (`*Fill`), quedan no ton ou escurécense ata que a letra branca dea 4,5:1.
 */
export const STATE_TONES = {
  danger: '#c9372c',
  success: '#2a7d3f',
  warn: '#a05d0e',
  info: '#2c64cc',
  muted: '#5f646b',
} as const;

/** Porcentaxe nominal do ton no fondo pálido do chip de filtro. */
const CHIP_MIX = 9;

/**
 * Canto ton leva o fondo pálido do chip de filtro sen marcar (filter-chip.css, a única excepción
 * a P1-9): 9 % nominal, e baixa ata que a letra no ton siga a 4,5:1 sobre el en todos os tons.
 */
export function chipMix(tones: Readonly<Record<string, string>>, surface: string) {
  const fits = (percent: number) =>
    Object.values(tones).every((tone) => contrastRatio(tone, mix(tone, surface, percent / 100)) >= TEXT_CONTRAST);
  let percent = CHIP_MIX;
  while (percent > 0 && !fits(percent)) percent--;
  return `${percent}%`;
}

/** Recheo do ton: o da paleta, escurecido só se a letra branca non chegase a 4,5:1. */
function fill(nominal: string): string {
  for (let step = 0; step <= 50; step++) {
    const candidate = mix('#000000', nominal, step / 50);
    if (contrastRatio('#ffffff', candidate) >= TEXT_CONTRAST) return candidate;
  }
  return '#000000';
}

/**
 * Recheos dos estados e a súa letra (chips `solid`, chip de filtro marcado). Non dependen do tema:
 * a letra branca vai sobre o ton tamén en escuro, onde os tons como texto sóbense de luz.
 */
export function fills() {
  const tones = {
    dangerFill: fill(STATE_TONES.danger),
    successFill: fill(STATE_TONES.success),
    warnFill: fill(STATE_TONES.warn),
    infoFill: fill(STATE_TONES.info),
    mutedFill: fill(STATE_TONES.muted),
  };
  return {
    ...tones,
    onDanger: onFill(tones.dangerFill),
    onSuccess: onFill(tones.successFill),
    onWarn: onFill(tones.warnFill),
    onInfo: onFill(tones.infoFill),
    onMuted: onFill(tones.mutedFill),
  };
}
