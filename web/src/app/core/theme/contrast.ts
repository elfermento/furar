// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/** Contraste WCAG 2.1 (1.4.3 e 1.4.11), o mesmo cálculo que fai a API (ADR-0014). */
export const TEXT_CONTRAST = 4.5;
export const NON_TEXT_CONTRAST = 3;

const HEX = /^#([0-9a-f]{6})$/i;

export function isHexColor(value: string): boolean {
  return HEX.test(value);
}

function channel(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function luminance(hex: string): number {
  const match = HEX.exec(hex);
  if (!match) {
    throw new Error(`Cor non válida: ${hex}`);
  }
  const n = Number.parseInt(match[1] ?? '000000', 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}

export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (light + 0.05) / (dark + 0.05);
}

export interface ThemeColors {
  readonly primary: string;
  readonly onPrimary: string;
  readonly accent: string;
  readonly background: string;
  readonly text: string;
}

export interface ContrastCheck {
  readonly pair: 'textOnBackground' | 'onPrimaryOnPrimary' | 'accentOnBackground' | 'primaryOnBackground';
  readonly ratio: number;
  readonly minimum: number;
  readonly ok: boolean;
}

/** Os pares que rexeita a API con municipality.theme-low-contrast. */
export function checkTheme(theme: ThemeColors): ContrastCheck[] {
  const pairs: [ContrastCheck['pair'], string, string, number][] = [
    ['textOnBackground', theme.text, theme.background, TEXT_CONTRAST],
    ['onPrimaryOnPrimary', theme.onPrimary, theme.primary, TEXT_CONTRAST],
    ['accentOnBackground', theme.accent, theme.background, TEXT_CONTRAST],
    ['primaryOnBackground', theme.primary, theme.background, NON_TEXT_CONTRAST],
  ];
  return pairs
    .filter(([, a, b]) => isHexColor(a) && isHexColor(b))
    .map(([pair, a, b, minimum]) => {
      const ratio = contrastRatio(a, b);
      return { pair, ratio, minimum, ok: ratio >= minimum };
    });
}

/** Que cor cambia «Axustar» en cada par (a que nomea a frase) e que cor queda fixa. */
const FIX: Readonly<Record<ContrastCheck['pair'], { fix: keyof ThemeColors; other: keyof ThemeColors }>> = {
  textOnBackground: { fix: 'text', other: 'background' },
  onPrimaryOnPrimary: { fix: 'primary', other: 'onPrimary' },
  accentOnBackground: { fix: 'accent', other: 'background' },
  primaryOnBackground: { fix: 'primary', other: 'background' },
};

/** Luminosidade da cor fixa por riba da que hai que escurecer (se non, aclarar). */
const LIGHT = 0.18;

/** A cor que cambia «Axustar» nese par. */
export function fixedColor(pair: ContrastCheck['pair']): keyof ThemeColors {
  return FIX[pair].fix;
}

/** Se para arranxar o par hai que escurecer (`darken`) ou aclarar (`lighten`) a súa cor. */
export function fixDirection(theme: ThemeColors, pair: ContrastCheck['pair']): 'darken' | 'lighten' {
  return luminance(theme[FIX[pair].other]) > LIGHT ? 'darken' : 'lighten';
}

function mix(hex: string, to: number, amount: number): string {
  const n = Number.parseInt(hex.slice(1), 16);
  const channels = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  return `#${channels
    .map((value) =>
      Math.round(value + (to - value) * amount)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;
}

/**
 * «Axustar» (maqueta 49): escurece ou aclara a cor do par só ata chegar ao mínimo, en pasos do
 * 1 %, para cambiala o menos posible. Repite unhas voltas por se arranxar un par estraga outro que
 * comparte cor (a principal). Devolve o tema novo; sen `pair`, arranxa todos os que fallan.
 */
export function fixContrast(theme: ThemeColors, pair?: ContrastCheck['pair']): ThemeColors {
  let next = { ...theme };
  for (let round = 0; round < 4; round++) {
    const failing = checkTheme(next).filter((check) => !check.ok && (!pair || check.pair === pair));
    if (!failing.length) break;
    for (const check of failing) {
      const { fix, other } = FIX[check.pair];
      const to = luminance(next[other]) > LIGHT ? 0 : 255;
      for (let step = 1; step <= 100; step++) {
        const candidate = mix(next[fix], to, step / 100);
        if (contrastRatio(candidate, next[other]) >= check.minimum) {
          next = { ...next, [fix]: candidate };
          break;
        }
      }
    }
  }
  return next;
}
