// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { ThemeColors } from './contrast';
import { checkTheme, contrastRatio } from './contrast';
import type { DerivedTokens } from './theme-tokens';
import { DEFAULT_THEME, TOKEN_VARIABLES, deriveTokens, isCompleteTheme, mix } from './theme-tokens';

/** Primeira mestura de `from` cara a `to` que dá polo menos `minimum` sobre `background`. */
function atLimit(from: string, to: string, background: string, minimum: number): string | null {
  for (let step = 0; step <= 400; step++) {
    const candidate = mix(to, from, step / 400);
    if (contrastRatio(candidate, background) >= minimum) return candidate;
  }
  return null;
}

/** Grella de temas no límite do que admite a API: texto e accent a 4,5:1 xustos, primary a 3:1. */
function limitThemes(): ThemeColors[] {
  const themes: ThemeColors[] = [];
  for (const background of ['#ffffff', '#f4f1e8', '#fff7d6', '#16181d', '#000000', '#2b2b2b']) {
    const dark = contrastRatio('#000000', background) < contrastRatio('#ffffff', background);
    const far = dark ? '#ffffff' : '#000000';
    for (const hue of ['#808080', '#3a7bd5', '#d53a3a', '#2e9e4f', '#b3541e']) {
      const text = atLimit(background, far, background, 4.5);
      const accent = atLimit(hue, far, background, 4.5);
      const primary = atLimit(hue, far, background, 3);
      if (!text || !accent || !primary) continue;
      const onPrimary = contrastRatio('#ffffff', primary) >= 4.5 ? '#ffffff' : '#000000';
      themes.push({ primary, onPrimary, accent, background, text });
    }
  }
  return themes.filter((theme) => checkTheme(theme).every((check) => check.ok));
}

const EXAMPLES: ThemeColors[] = [
  DEFAULT_THEME,
  { primary: '#00529b', onPrimary: '#ffffff', accent: '#b3541e', background: '#ffffff', text: '#1a1a1a' },
  { primary: '#7a1f2b', onPrimary: '#ffffff', accent: '#1f5f7a', background: '#fbfaf7', text: '#222222' },
  { primary: '#949494', onPrimary: '#000000', accent: '#767676', background: '#ffffff', text: '#767676' },
  { primary: '#4493f8', onPrimary: '#0a0a0a', accent: '#f0b46a', background: '#0d1117', text: '#f0f6fc' },
];

/** Os mínimos da táboa §3.1 de docs/deseno.md. */
function failures(tokens: DerivedTokens): string[] {
  const { page, surface, tint } = tokens;
  const pairs: [keyof DerivedTokens, string[], number][] = [
    ['ink', [page, surface, tint], 4.5],
    ['inkSoft', [page, surface, tint], 4.5],
    ['line', [page, surface, tint], 3],
    ['primaryText', [page, surface, tint], 4.5],
    ['accent', [page, surface], 4.5],
    ['focus', [page, surface, tint], 3],
    ['onPrimary', [tokens.primary, tokens.primaryHover], 4.5],
    ['danger', [page, surface, tint], 4.5],
    ['success', [page, surface, tint], 4.5],
    ['warn', [page, surface, tint], 4.5],
    ['info', [page, surface, tint], 4.5],
    ['muted', [page, surface, tint], 4.5],
    // Chip «solid» (regra de chips, §8 P1-9): recheo do ton con letra branca (paleta do 2026-10-26).
    ['onDanger', [tokens.dangerFill], 4.5],
    ['onSuccess', [tokens.successFill], 4.5],
    ['onWarn', [tokens.warnFill], 4.5],
    ['onInfo', [tokens.infoFill], 4.5],
    ['onMuted', [tokens.mutedFill], 4.5],
  ];
  const failed: string[] = [];
  for (const [token, backgrounds, minimum] of pairs) {
    for (const background of backgrounds) {
      const ratio = contrastRatio(tokens[token] as string, background);
      if (ratio < minimum) failed.push(`${token} ${tokens[token]} / ${background} = ${ratio.toFixed(2)}`);
    }
  }
  // Círculo do estado de erro (status-state.css): color-mix(danger 12%, surface) fronte á icona.
  const dangerBadge = mix(tokens.danger, surface, 0.12);
  const badge = contrastRatio(tokens.danger, dangerBadge);
  if (badge < 3) failed.push(`danger / dangerBadge ${dangerBadge} = ${badge.toFixed(2)}`);
  // Avisos (callout.css): fondo suave do ton coa mestura derivada.
  for (const tone of ['danger', 'success', 'warn', 'info'] as const) {
    const soft = mix(tokens[tone], surface, Number.parseFloat(tokens.softMix) / 100);
    for (const [name, ratio, minimum] of [
      [`ink / ${tone} soft`, contrastRatio(tokens.ink, soft), 4.5],
      [`${tone} / ${tone} soft`, contrastRatio(tokens[tone], soft), 3],
    ] as const) {
      if (ratio < minimum) failed.push(`${name} = ${ratio.toFixed(2)}`);
    }
  }
  // Chip de filtro sen marcar (filter-chip.css): letra no ton sobre o seu fondo pálido.
  for (const tone of ['danger', 'success', 'warn', 'info', 'muted'] as const) {
    const pale = mix(tokens[tone], surface, Number.parseFloat(tokens.chipMix) / 100);
    const ratio = contrastRatio(tokens[tone], pale);
    if (ratio < 4.5) failed.push(`${tone} / ${tone} chip = ${ratio.toFixed(2)}`);
  }
  return failed;
}

describe('deriveTokens: AA garantido para calquera tema admitido', () => {
  const grid = limitThemes();

  it('a grella de temas límite é ampla e todos pasan a validación da API', () => {
    expect(grid.length).toBeGreaterThanOrEqual(20);
    expect(grid.some((theme) => theme.background === '#000000')).toBe(true);
  });

  it.each([...EXAMPLES, ...grid].map((theme) => [theme.background, theme.primary, theme] as const))(
    'fondo %s, primary %s: todos os pares cumpren',
    (_background, _primary, theme) => {
      expect(failures(deriveTokens(theme))).toEqual([]);
    },
  );

  it('a paleta de estados queda como recheo con letra branca e o chip de filtro co 9 %', () => {
    const base = deriveTokens(DEFAULT_THEME);
    expect([base.successFill, base.warnFill, base.dangerFill, base.infoFill, base.mutedFill]).toEqual([
      '#2a7d3f',
      '#a05d0e',
      '#c9372c',
      '#2c64cc',
      '#5f646b',
    ]);
    expect([base.onSuccess, base.onWarn, base.onDanger, base.onInfo, base.onMuted]).toEqual(Array(5).fill('#ffffff'));
    expect(base.chipMix).toBe('9%');
  });

  it('o tema por defecto conserva os tintes nominais; o tema límite apágaos sen perder AA', () => {
    const base = deriveTokens(DEFAULT_THEME);
    expect(base.page).not.toBe(DEFAULT_THEME.background);
    expect(base.tint).toBe(mix(DEFAULT_THEME.primary, DEFAULT_THEME.background, 0.1));
    expect(base.glass).toBe('rgb(255 255 255 / 0.9)');
    const limit = deriveTokens(EXAMPLES[3] as ThemeColors);
    expect(limit.inkSoft).toBe(limit.ink);
    expect(limit.glass).toBe('#ffffff');
  });

  it('o fondo escuro dá color-scheme dark e os estados sobense de luz', () => {
    const dark = deriveTokens(EXAMPLES[4] as ThemeColors);
    expect(dark.colorScheme).toBe('dark');
    expect(contrastRatio(dark.danger, '#c9372c')).toBeGreaterThan(1.5);
    // Os recheos non se sobem: a letra branca vai sobre o ton da paleta tamén en escuro.
    expect(dark.successFill).toBe('#2a7d3f');
    expect(dark.onSuccess).toBe('#ffffff');
    expect(deriveTokens(DEFAULT_THEME).colorScheme).toBe('light');
  });

  const darkThemes = [...EXAMPLES, ...grid].filter((theme) => deriveTokens(theme).colorScheme === 'dark');

  it.each(darkThemes.map((theme) => [theme.background, theme.primary, theme] as const))(
    'escuro %s, primary %s: cards sobre a páxina, tons lexibles e botón que destaca',
    (_background, _primary, theme) => {
      const t = deriveTokens(theme);
      const backgrounds = [t.page, t.surface, t.tint];
      const lowest = (color: string) => Math.min(...backgrounds.map((b) => contrastRatio(color, b)));
      expect(lowest(t.primary)).toBeGreaterThanOrEqual(4.5);
      for (const tone of [t.primaryText, t.danger, t.success, t.warn, t.info, t.muted]) {
        expect(lowest(tone)).toBeGreaterThanOrEqual(6);
      }
      expect(contrastRatio(t.onPrimary, t.primary)).toBeGreaterThanOrEqual(4.5);
      if (contrastRatio(theme.text, theme.background) >= 5) expect(t.surface).not.toBe(t.page);
    },
  );

  it('hai temas escuros na grella', () => {
    expect(darkThemes.length).toBeGreaterThanOrEqual(5);
  });

  it('só deriva temas completos con cores hex', () => {
    expect(isCompleteTheme(DEFAULT_THEME)).toBe(true);
    expect(isCompleteTheme({ ...DEFAULT_THEME, text: 'azul' })).toBe(false);
  });

  it('os valores por defecto de tokens.css son os de deriveTokens(tema por defecto)', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8');
    const derived = deriveTokens(DEFAULT_THEME);
    for (const [token, variable] of Object.entries(TOKEN_VARIABLES) as [keyof DerivedTokens, string][]) {
      if (token === 'colorScheme') continue;
      const match = new RegExp(`${variable}:\\s*([^;]+);`).exec(css);
      expect(match?.[1]?.trim(), variable).toBe(derived[token]);
    }
  });
});
