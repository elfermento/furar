// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { mix } from './color';
import { NON_TEXT_CONTRAST, contrastRatio, luminance } from './contrast';
import type { DerivedTokens } from './theme-tokens';

/** Contraste reforzado (WCAG 1.4.6, AAA) para o texto con «Alto contraste». */
export const ENHANCED_CONTRAST = 7;

/**
 * Cores de «Alto contraste» (preferencia da persoa) sobre as do tema do concello: o
 * mesmo matiz, achegado á tinta (textos) ou afastado da súa letra (recheos) ata 7:1. Os fondos
 * quedan na superficie e os bordos e o texto suave na tinta (styles/base.css); se a tinta do
 * concello non chega a 7:1 sobre o seu fondo, os textos quedan na tinta, que é o máximo posible.
 */
export interface HighContrastTokens {
  readonly primary: string;
  readonly primaryHover: string;
  readonly onPrimary: string;
  readonly primaryText: string;
  readonly accent: string;
  readonly focus: string;
  readonly danger: string;
  readonly success: string;
  readonly warn: string;
  readonly info: string;
  readonly muted: string;
  readonly dangerFill: string;
  readonly successFill: string;
  readonly warnFill: string;
  readonly infoFill: string;
  readonly mutedFill: string;
  readonly softMix: string;
  readonly chipMix: string;
}

/** Variable CSS de cada un (en tokens.css, os do tema por defecto; base.css úsaas co axuste). */
export const HIGH_CONTRAST_VARIABLES: Readonly<Record<keyof HighContrastTokens, string>> = {
  primary: '--f-hc-primary',
  primaryHover: '--f-hc-primary-hover',
  onPrimary: '--f-hc-on-primary',
  primaryText: '--f-hc-primary-text',
  accent: '--f-hc-accent',
  focus: '--f-hc-focus',
  danger: '--f-hc-danger',
  success: '--f-hc-success',
  warn: '--f-hc-warn',
  info: '--f-hc-info',
  muted: '--f-hc-muted',
  dangerFill: '--f-hc-danger-fill',
  successFill: '--f-hc-success-fill',
  warnFill: '--f-hc-warn-fill',
  infoFill: '--f-hc-info-fill',
  mutedFill: '--f-hc-muted-fill',
  softMix: '--f-hc-soft-mix',
  chipMix: '--f-hc-chip-mix',
};

/** Achega `nominal` á tinta ata `minimum` sobre o fondo (ou a tinta, se nin así). */
function towardInk(ink: string, nominal: string, surface: string, minimum: number): string {
  for (let step = 0; step <= 50; step++) {
    const candidate = mix(ink, nominal, step / 50);
    if (contrastRatio(candidate, surface) >= minimum) return candidate;
  }
  return ink;
}

/** Afasta un recheo da súa letra (cara ao negro ou ao branco) ata `minimum`. */
function awayFrom(fill: string, letter: string, minimum: number): string {
  const extreme = luminance(letter) > 0.18 ? '#000000' : '#ffffff';
  for (let step = 0; step <= 50; step++) {
    const candidate = mix(extreme, fill, step / 50);
    if (contrastRatio(letter, candidate) >= minimum) return candidate;
  }
  return extreme;
}

/** A maior porcentaxe (desde `nominal`) que cumpre `fits`. */
function percent(nominal: number, fits: (weight: number) => boolean): string {
  let value = nominal;
  while (value > 0 && !fits(value / 100)) value--;
  return `${value}%`;
}

/**
 * Botón principal a 7:1 coa súa letra e a 3:1 co fondo: afástase da letra do concello; se así
 * deixase de destacar sobre o fondo (un primary claro con letra negra sobre branco), cambia de
 * letra e vai cara ao outro lado.
 */
function primaryFill(tokens: DerivedTokens): { primary: string; onPrimary: string } {
  const kept = awayFrom(tokens.primary, tokens.onPrimary, ENHANCED_CONTRAST);
  if (contrastRatio(kept, tokens.surface) >= NON_TEXT_CONTRAST) return { primary: kept, onPrimary: tokens.onPrimary };
  const onPrimary = luminance(tokens.onPrimary) > 0.18 ? '#000000' : '#ffffff';
  return { primary: awayFrom(tokens.primary, onPrimary, ENHANCED_CONTRAST), onPrimary };
}

export function deriveHighContrast(tokens: DerivedTokens): HighContrastTokens {
  const { ink, surface } = tokens;
  const text = (nominal: string) => towardInk(ink, nominal, surface, ENHANCED_CONTRAST);
  const states = {
    danger: text(tokens.danger),
    success: text(tokens.success),
    warn: text(tokens.warn),
    info: text(tokens.info),
    muted: text(tokens.muted),
  };
  const { primary, onPrimary } = primaryFill(tokens);
  const extreme = luminance(onPrimary) > 0.18 ? '#000000' : '#ffffff';
  const tones = Object.values(states);
  return {
    primary,
    primaryHover: mix(extreme, primary, 0.15),
    onPrimary,
    primaryText: text(tokens.primaryText),
    accent: text(tokens.accent),
    focus: text(tokens.primaryText),
    ...states,
    dangerFill: awayFrom(tokens.dangerFill, tokens.onDanger, ENHANCED_CONTRAST),
    successFill: awayFrom(tokens.successFill, tokens.onSuccess, ENHANCED_CONTRAST),
    warnFill: awayFrom(tokens.warnFill, tokens.onWarn, ENHANCED_CONTRAST),
    infoFill: awayFrom(tokens.infoFill, tokens.onInfo, ENHANCED_CONTRAST),
    mutedFill: awayFrom(tokens.mutedFill, tokens.onMuted, ENHANCED_CONTRAST),
    // Avisos: a tinta a 7:1 sobre o fondo suave e a icona do ton a 3:1.
    softMix: percent(8, (weight) =>
      tones.every((tone) => {
        const soft = mix(tone, surface, weight);
        return (
          contrastRatio(ink, soft) >= Math.min(ENHANCED_CONTRAST, contrastRatio(ink, surface)) &&
          contrastRatio(tone, soft) >= NON_TEXT_CONTRAST
        );
      }),
    ),
    // Chip de filtro sen marcar: a letra do ton a 7:1 sobre o seu fondo pálido.
    chipMix: percent(9, (weight) =>
      tones.every(
        (tone) =>
          contrastRatio(tone, mix(tone, surface, weight)) >= Math.min(ENHANCED_CONTRAST, contrastRatio(tone, surface)),
      ),
    ),
  };
}
