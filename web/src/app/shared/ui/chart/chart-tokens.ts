// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NON_TEXT_CONTRAST, contrastRatio, isHexColor, luminance } from '../../../core/theme/contrast';
import { sequentialScale } from '../../../core/theme/sequential-scale';
import type { DerivedTokens } from '../../../core/theme/theme-tokens';
import { DEFAULT_THEME, TOKEN_VARIABLES, deriveTokens } from '../../../core/theme/theme-tokens';

/** Pasos da escala secuencial dos gráficos (mapa de calor; «paso 2» = `scale[1]`). */
export const SCALE_STEPS = 6;

const COLORS = [
  'surface',
  'ink',
  'inkSoft',
  'divider',
  'line',
  'primary',
  'success',
  'danger',
  'warn',
  'info',
  'muted',
] as const satisfies readonly (keyof DerivedTokens)[];

type ColorToken = (typeof COLORS)[number];

/**
 * Cores e letra cos que se debuxa un gráfico: os tokens `--f-*` do tema en uso (o canvas non
 * le CSS, así que se resolven antes de debuxar), a escala secuencial derivada do `primary` e a
 * tipografía da páxina co tamaño de `--text-support` en px (segue o zoom do texto).
 */
export type ChartTokens = Readonly<Record<ColorToken, string>> & {
  readonly scale: readonly string[];
  readonly fontFamily: string;
  readonly fontSize: number;
};

const FALLBACK = deriveTokens(DEFAULT_THEME);
const FALLBACK_FONT = 'system-ui, sans-serif';
const SUPPORT_REM = 0.875;

/** Tokens para un tema xa derivado (exemplos, specs e o tema por defecto). */
export function chartTokensFrom(tokens: DerivedTokens, fontFamily = FALLBACK_FONT, fontSize = 14): ChartTokens {
  const colors = Object.fromEntries(COLORS.map((key) => [key, tokens[key]])) as Record<ColorToken, string>;
  return { ...colors, scale: sequentialScale(tokens, SCALE_STEPS), fontFamily, fontSize };
}

/**
 * Le os tokens como os ve `element` (variables herdadas de `<html>`, onde as pon ThemeService).
 * Un valor que falte ou non sexa hex (sen folla de estilos, nos tests) colle o do tema por defecto.
 */
export function readChartTokens(element: Element): ChartTokens {
  const view = element.ownerDocument.defaultView;
  const style = view?.getComputedStyle(element);
  const colors = { ...FALLBACK };
  for (const key of COLORS) {
    const value = style?.getPropertyValue(TOKEN_VARIABLES[key]).trim() ?? '';
    if (isHexColor(value)) colors[key] = value;
  }
  const rootSize = Number.parseFloat(view?.getComputedStyle(element.ownerDocument.documentElement).fontSize ?? '');
  const fontSize = Math.round((Number.isFinite(rootSize) && rootSize > 0 ? rootSize : 16) * SUPPORT_REM);
  return chartTokensFrom(colors, style?.fontFamily || FALLBACK_FONT, fontSize);
}

/** Por debaixo desta luminancia, o fondo é escuro (o mesmo corte de deriveTokens). */
const DARK_SURFACE = 0.18;
const PAPER = '#ffffff';

/**
 * O tema claro co que se imprime un tema escuro (o papel é branco): o tema por defecto co `primary`
 * do concello se se ve sobre branco (3:1) ou o do tema por defecto se non; `null` se `surface` xa
 * é clara ou non se coñece (imprímese co tema que hai).
 */
export function printTheme(surface: string, primary: string): DerivedTokens | null {
  if (!isHexColor(surface) || luminance(surface) >= DARK_SURFACE) return null;
  const ok = isHexColor(primary) && contrastRatio(primary, PAPER) >= NON_TEXT_CONTRAST;
  return deriveTokens({ ...DEFAULT_THEME, primary: ok ? primary : DEFAULT_THEME.primary });
}

/** Os mesmos tokens en claro, para imprimir (`printTheme`); se xa son claros, iguais. A letra non cambia. */
export function lightChartTokens(tokens: ChartTokens): ChartTokens {
  const light = printTheme(tokens.surface, tokens.primary);
  return light ? chartTokensFrom(light, tokens.fontFamily, tokens.fontSize) : tokens;
}
