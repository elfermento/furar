// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import type { ThemeColors } from '../../../core/theme/contrast';
import { contrastRatio } from '../../../core/theme/contrast';
import { DEFAULT_THEME, deriveTokens, mix } from '../../../core/theme/theme-tokens';

/**
 * Trinquete da regra dos chips (docs/deseno.md P1-9): un chip con cor leva ou o recheo do ton con
 * letra branca ou negra, ou a superficie con bordo e letra no ton. Falla se unha regra de chip
 * (tag, chip, badge, pill, filter-chip) pinta un fondo tinguido ou mesturado, ou se pon o recheo
 * do ton cunha letra que non sexa a súa «on». Excepcións: a variante `soft` (o `tint` da marca coa
 * letra na tinta, nunca no ton) e, SÓ no chip de filtro (un botón con `aria-pressed`, 2026-10-26),
 * o fondo pálido do ton coa letra no ton e o recheo en degradado coa letra «on».
 */
const COMPONENTS = resolve(process.cwd(), 'src/styles/components');
const CHIP_SELECTOR = /\.f-(tag|chip|badge|pill|filter-chip)\b/;
const FILTER_CHIP = /^\.f-filter-chip\b/;
const ALLOWED_BACKGROUNDS = new Set(['var(--f-surface)', 'var(--f-tag-fill)']);
const ON_COLORS = /^var\(--f-(on-[a-z]+|surface|ink)\)$/;
/** Fondo pálido do chip de filtro sen marcar: o ton ao `--f-chip-mix` (9 %) sobre a superficie. */
const PALE = 'color-mix(in srgb, var(--f-chip-tone) var(--f-chip-mix), var(--f-surface))';
/** Recheo do chip de filtro marcado: do ton a un 15 % máis escuro. */
const GRADIENT = 'linear-gradient(var(--f-chip-fill), color-mix(in srgb, var(--f-chip-fill) 85%, #000000))';

interface Rule {
  readonly selector: string;
  readonly declarations: ReadonlyMap<string, string>;
}

/** Reglas folla `selector { … }`; dentro dun @media colle o bloque máis interior. */
function parse(css: string): Rule[] {
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '');
  return [...clean.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selector = '', body = '']) => ({
    selector: selector.trim(),
    declarations: new Map(
      body
        .split(';')
        .map((line) => line.split(/:(.*)/s).map((part) => part.trim()))
        .filter(([property, value]) => property && value)
        .map(([property, value]) => [property as string, value as string]),
    ),
  }));
}

export function chipViolations(css: string): string[] {
  const found: string[] = [];
  for (const { selector, declarations } of parse(css)) {
    if (!CHIP_SELECTOR.test(selector)) continue;
    const background = declarations.get('background') ?? declarations.get('background-color');
    const color = declarations.get('color');
    if (FILTER_CHIP.test(selector) && (background === PALE || background === GRADIENT)) {
      const expected = background === PALE ? 'var(--f-chip-tone)' : 'var(--f-chip-on)';
      if (color !== expected) found.push(`${selector}: chip de filtro con letra «${color}» (só ${expected})`);
      continue;
    }
    const softTint = background === 'var(--f-tint)' && declarations.get('color') === 'var(--f-ink)';
    if (background && !softTint && !ALLOWED_BACKGROUNDS.has(background) && !/^(Canvas|Highlight)/.test(background)) {
      found.push(`${selector}: fondo «${background}» (só superficie ou o ton enteiro)`);
    }
    for (const variable of ['--f-tag-on', '--f-chip-on']) {
      const on = declarations.get(variable);
      if (on && !ON_COLORS.test(on)) {
        found.push(`${selector}: letra sobre o recheo «${on}» (só branca/negra derivada: --f-on-*)`);
      }
    }
    if (background === 'var(--f-tag-fill)' && color && color !== 'var(--f-tag-on)') {
      found.push(`${selector}: recheo do ton con letra «${color}»`);
    }
  }
  return found;
}

function cssFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory()
      ? cssFiles(join(directory, entry.name))
      : entry.name.endsWith('.css')
        ? [join(directory, entry.name)]
        : [],
  );
}

describe('chips de cor', () => {
  it('ningunha regra de chip mestura fondo tinguido e letra no ton', () => {
    const found = cssFiles(COMPONENTS).flatMap((file) =>
      chipViolations(readFileSync(file, 'utf8')).map(
        (violation) => `${file.slice(COMPONENTS.length + 1)}: ${violation}`,
      ),
    );
    expect(found).toEqual([]);
  });

  it('o detector colle o tinte e deixa pasar os dous aspectos válidos', () => {
    expect(
      chipViolations('.f-tag { background: color-mix(in srgb, var(--f-danger) 12%, var(--f-surface)); }'),
    ).toHaveLength(1);
    expect(chipViolations('.f-tag { background: var(--f-tint); color: var(--f-danger); }')).toHaveLength(1);
    expect(chipViolations('.f-tag { background: var(--f-tag-fill); color: var(--f-danger); }')).toHaveLength(1);
    expect(chipViolations('.f-tag { background: var(--f-tag-tone); color: var(--f-tag-on); }')).toHaveLength(1);
    expect(chipViolations('.f-tag { --f-tag-on: var(--f-danger); }')).toHaveLength(1);
    expect(chipViolations('.f-tag { background: var(--f-tag-fill); color: var(--f-tag-on); }')).toEqual([]);
    expect(chipViolations('.f-tag { background: var(--f-surface); color: var(--f-tag-tone); }')).toEqual([]);
    expect(chipViolations('.f-tag { background: var(--f-tint); color: var(--f-ink); }')).toEqual([]);
    expect(chipViolations('.f-tag { background: var(--f-tint); }')).toHaveLength(1);
  });

  it('o fondo pálido falla nunha etiqueta e pasa só no chip de filtro', () => {
    const pale = `background: ${PALE}; color: var(--f-chip-tone);`;
    const filled = `background: ${GRADIENT}; color: var(--f-chip-on);`;
    for (const selector of ['.f-tag[data-tone]', '.f-chip', '.f-badge', '.f-tag-swatch']) {
      expect(chipViolations(`${selector} { ${pale} }`), selector).toHaveLength(1);
      expect(chipViolations(`${selector} { ${filled} }`), selector).toHaveLength(1);
    }
    expect(chipViolations(`.f-filter-chip[data-tone] { ${pale} }`)).toEqual([]);
    expect(chipViolations(`.f-filter-chip[data-tone][aria-pressed='true'] { ${filled} }`)).toEqual([]);
    expect(chipViolations(`.f-filter-chip[data-tone] { background: ${PALE}; color: var(--f-ink); }`)).toHaveLength(1);
    expect(
      chipViolations(`.f-filter-chip[data-tone] { background: ${GRADIENT}; color: var(--f-chip-tone); }`),
    ).toHaveLength(1);
    expect(chipViolations('.f-filter-chip { --f-chip-on: var(--f-success); }')).toHaveLength(1);
    expect(chipViolations('.f-filter-chip { background: var(--f-tint); color: var(--f-success); }')).toHaveLength(1);
  });

  it('filter-chip.css usa exactamente o fondo pálido e o degradado que se comproban', () => {
    const css = readFileSync(join(COMPONENTS, 'filter-chip.css'), 'utf8');
    expect(css).toContain(`background: ${PALE};`);
    expect(css).toContain(`background: ${GRADIENT};`);
  });

  /** Tema por defecto, o máis xusto que admite a API e un escuro (os do catálogo). */
  const THEMES: [string, ThemeColors][] = [
    ['por defecto', DEFAULT_THEME],
    ['límite', { primary: '#949494', onPrimary: '#000000', accent: '#767676', background: '#ffffff', text: '#767676' }],
    ['escuro', { primary: '#4493f8', onPrimary: '#0a0a0a', accent: '#f0b46a', background: '#0d1117', text: '#f0f6fc' }],
  ];

  it.each(THEMES)('chip de filtro, tema %s: 4,5:1 sen marcar e marcado en todos os tons', (_name, theme) => {
    const t = deriveTokens(theme);
    const failed: string[] = [];
    const check = (name: string, ink: string, background: string) => {
      const ratio = contrastRatio(ink, background);
      if (ratio < 4.5) failed.push(`${name}: ${ink} / ${background} = ${ratio.toFixed(2)}`);
    };
    check('neutro', t.ink, t.surface);
    check('neutro marcado', t.primaryText, t.surface);
    const tones = [
      ['success', t.success, t.successFill, t.onSuccess],
      ['warn', t.warn, t.warnFill, t.onWarn],
      ['danger', t.danger, t.dangerFill, t.onDanger],
      ['info', t.info, t.infoFill, t.onInfo],
      ['muted', t.muted, t.mutedFill, t.onMuted],
    ] as const;
    for (const [name, tone, fill, on] of tones) {
      check(`${name} sen marcar`, tone, mix(tone, t.surface, Number.parseFloat(t.chipMix) / 100));
      check(`${name} marcado (arriba)`, on, fill);
      check(`${name} marcado (abaixo)`, on, mix(fill, '#000000', 0.85));
    }
    expect(failed).toEqual([]);
  });
});
