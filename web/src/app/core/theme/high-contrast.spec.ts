// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { ThemeColors } from './contrast';
import { contrastRatio } from './contrast';
import { ENHANCED_CONTRAST, HIGH_CONTRAST_VARIABLES, deriveHighContrast } from './high-contrast';
import { DEFAULT_THEME, deriveTokens, mix } from './theme-tokens';

/** Temas de exemplo: o por defecto, claros e escuros, e un no límite do que admite a API. */
const THEMES: ThemeColors[] = [
  DEFAULT_THEME,
  { primary: '#00529b', onPrimary: '#ffffff', accent: '#b3541e', background: '#ffffff', text: '#1a1a1a' },
  { primary: '#7a1f2b', onPrimary: '#ffffff', accent: '#1f5f7a', background: '#fbfaf7', text: '#222222' },
  { primary: '#949494', onPrimary: '#000000', accent: '#767676', background: '#ffffff', text: '#767676' },
  { primary: '#4493f8', onPrimary: '#0a0a0a', accent: '#f0b46a', background: '#0d1117', text: '#f0f6fc' },
  { primary: '#2e9e4f', onPrimary: '#000000', accent: '#e8a33d', background: '#000000', text: '#ffffff' },
];

describe('Alto contraste: cores reforzadas sobre o tema de cada concello', () => {
  it.each(THEMES.map((theme) => [theme.background, theme.primary, theme] as const))(
    'fondo %s, primary %s: textos a 7:1 (ou a tinta) e recheos a 7:1 coa súa letra',
    (_background, _primary, theme) => {
      const base = deriveTokens(theme);
      const high = deriveHighContrast(base);
      const best = Math.min(ENHANCED_CONTRAST, contrastRatio(base.ink, base.surface));
      for (const token of ['primaryText', 'accent', 'focus', 'danger', 'success', 'warn', 'info', 'muted'] as const) {
        expect(contrastRatio(high[token], base.surface), token).toBeGreaterThanOrEqual(best - 0.01);
      }
      expect(contrastRatio(high.onPrimary, high.primary)).toBeGreaterThanOrEqual(ENHANCED_CONTRAST);
      expect(contrastRatio(high.onPrimary, high.primaryHover)).toBeGreaterThanOrEqual(ENHANCED_CONTRAST);
      for (const [fill, letter] of [
        [high.dangerFill, base.onDanger],
        [high.successFill, base.onSuccess],
        [high.warnFill, base.onWarn],
        [high.infoFill, base.onInfo],
        [high.mutedFill, base.onMuted],
      ] as const) {
        expect(contrastRatio(letter, fill)).toBeGreaterThanOrEqual(ENHANCED_CONTRAST);
      }
      // O botón principal segue destacando sobre o fondo (1.4.11).
      expect(contrastRatio(high.primary, base.surface)).toBeGreaterThanOrEqual(3);
      for (const tone of [high.danger, high.success, high.warn, high.info, high.muted]) {
        const chip = mix(tone, base.surface, Number.parseFloat(high.chipMix) / 100);
        expect(contrastRatio(tone, chip)).toBeGreaterThanOrEqual(
          Math.min(ENHANCED_CONTRAST, contrastRatio(tone, base.surface)),
        );
      }
    },
  );

  it('conserva o matiz do concello: o tema por defecto xa cumpre e non cambia o primary', () => {
    const base = deriveTokens(DEFAULT_THEME);
    const high = deriveHighContrast(base);
    expect(high.primary).toBe(base.primary);
    expect(high.primaryText).toBe(base.primaryText);
  });

  it('os valores por defecto de tokens.css son os do tema por defecto', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8');
    const high = deriveHighContrast(deriveTokens(DEFAULT_THEME));
    for (const [token, variable] of Object.entries(HIGH_CONTRAST_VARIABLES) as [keyof typeof high, string][]) {
      const match = new RegExp(`${variable}:\\s*([^;]+);`).exec(css);
      expect(match?.[1]?.trim(), variable).toBe(high[token]);
    }
  });

  it('base.css usa cada variable de alto contraste só baixo data-contrast="more"', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/base.css'), 'utf8');
    const block = /html\[data-contrast='more'\] \{([^}]*)\}/.exec(css)?.[1] ?? '';
    for (const variable of Object.values(HIGH_CONTRAST_VARIABLES)) {
      expect(block, variable).toContain(`var(${variable})`);
    }
  });
});
