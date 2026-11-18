// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ThemeColors } from './contrast';
import { checkTheme, contrastRatio, luminance } from './contrast';
import { sequentialScale } from './sequential-scale';
import { DEFAULT_THEME, deriveTokens } from './theme-tokens';

const DARK: ThemeColors = {
  primary: '#4493f8',
  onPrimary: '#0a0a0a',
  accent: '#f0b46a',
  background: '#0d1117',
  text: '#f0f6fc',
};

/** Temas claros e escuros, tamén co primary xusto a 3:1 (o mínimo que admite a API). */
const THEMES: [string, ThemeColors][] = [
  ['por defecto', DEFAULT_THEME],
  [
    'límite gris',
    { primary: '#949494', onPrimary: '#000000', accent: '#767676', background: '#ffffff', text: '#767676' },
  ],
  [
    'crema e granate',
    { primary: '#7a1f2b', onPrimary: '#ffffff', accent: '#1f5f7a', background: '#fbfaf7', text: '#222222' },
  ],
  [
    'amarelo pálido',
    { primary: '#b3541e', onPrimary: '#ffffff', accent: '#8a4b00', background: '#fff7d6', text: '#1a1a1a' },
  ],
  ['escuro', DARK],
  [
    'negro e verde',
    { primary: '#2e9e4f', onPrimary: '#000000', accent: '#f0b46a', background: '#000000', text: '#ffffff' },
  ],
];

describe('sequentialScale', () => {
  it('os temas de proba son válidos para a API', () => {
    for (const [, theme] of THEMES) expect(checkTheme(theme).every((check) => check.ok)).toBe(true);
  });

  it.each(THEMES)('%s: monótona en luminancia, distinguible e co último paso a 3:1 sobre surface', (_name, theme) => {
    const tokens = deriveTokens(theme);
    const scale = sequentialScale(tokens, 6);
    expect(scale).toHaveLength(6);
    const darker = luminance(tokens.primary) < luminance(tokens.surface);
    const lums = scale.map(luminance);
    for (let i = 1; i < lums.length; i++) {
      const previous = lums[i - 1] as number;
      const current = lums[i] as number;
      if (darker) expect(current).toBeLessThan(previous);
      else expect(current).toBeGreaterThan(previous);
      // Dous pasos seguidos distínguense (tamén os dous primeiros, os máis próximos ao fondo).
      expect(contrastRatio(scale[i] as string, scale[i - 1] as string)).toBeGreaterThanOrEqual(1.1);
    }
    expect(contrastRatio(scale[0] as string, tokens.surface)).toBeGreaterThanOrEqual(1.1);
    expect(contrastRatio(scale[5] as string, tokens.surface)).toBeGreaterThanOrEqual(3);
  });

  it('o último paso é o primary do concello cando xa chega a 3:1', () => {
    const light = deriveTokens(DEFAULT_THEME);
    expect(sequentialScale(light, 6).at(-1)).toBe(light.primary);
    const dark = deriveTokens(DARK);
    expect(dark.colorScheme).toBe('dark');
    expect(sequentialScale(dark, 6).at(-1)).toBe(dark.primary);
  });

  it('en claro escurece cara ao primary e en escuro aclara', () => {
    const light = sequentialScale(deriveTokens(DEFAULT_THEME), 3);
    expect(luminance(light[0] as string)).toBeGreaterThan(luminance(light[2] as string));
    const dark = sequentialScale(deriveTokens(DARK), 3);
    expect(luminance(dark[0] as string)).toBeLessThan(luminance(dark[2] as string));
  });

  it('un primary que non chega a 3:1 sobre a superficie achégase ao extremo ata chegar', () => {
    const scale = sequentialScale({ primary: '#b0b0b0', surface: '#ffffff' }, 4);
    expect(contrastRatio(scale[3] as string, '#ffffff')).toBeGreaterThanOrEqual(3);
    expect(scale[3]).not.toBe('#b0b0b0');
  });

  it('casos de borde: un paso é o primary; cero ou un número non enteiro, nada', () => {
    const tokens = deriveTokens(DEFAULT_THEME);
    expect(sequentialScale(tokens, 1)).toEqual([tokens.primary]);
    expect(sequentialScale(tokens, 0)).toEqual([]);
    expect(sequentialScale(tokens, 2.5)).toEqual([]);
  });
});
