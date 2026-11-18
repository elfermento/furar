// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { contrastRatio } from './contrast';

/** Aritmética de cor para deriveTokens (theme-tokens.ts): mesturas sRGB, luz en HSL e letra sobre recheo. */
export function rgb(hex: string): [number, number, number] {
  const n = Number.parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function hex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map((c) => Math.round(c).toString(16).padStart(2, '0')).join('')}`;
}

/** Mestura en sRGB (como color-mix(in srgb, a w, b)): `weight` é a parte de `a`, de 0 a 1. */
export function mix(a: string, b: string, weight: number): string {
  const [ca, cb] = [rgb(a), rgb(b)];
  return hex([0, 1, 2].map((i) => (ca[i] ?? 0) * weight + (cb[i] ?? 0) * (1 - weight)) as [number, number, number]);
}

function hsl([r, g, b]: [number, number, number]): [number, number, number] {
  const [rn, gn, bn] = [r / 255, g / 255, b / 255];
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = max === rn ? (gn - bn) / d + (gn < bn ? 6 : 0) : max === gn ? (bn - rn) / d + 2 : (rn - gn) / d + 4;
  return [h / 6, s, l];
}

function fromHsl([h, s, l]: [number, number, number]): string {
  if (s === 0) return hex([l * 255, l * 255, l * 255]);
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  const hue = (t: number) => {
    const u = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
    if (u < 1 / 6) return p + (q - p) * 6 * u;
    if (u < 1 / 2) return q;
    if (u < 2 / 3) return p + (q - p) * (2 / 3 - u) * 6;
    return p;
  };
  return hex([hue(h + 1 / 3) * 255, hue(h) * 255, hue(h - 1 / 3) * 255]);
}

const meetsAll = (color: string, backgrounds: readonly string[], minimum: number) =>
  backgrounds.every((background) => contrastRatio(color, background) >= minimum);

/** Sobe a luz (HSL) de `nominal` mantendo o matiz ata cumprir `minimum` sobre os fondos escuros. */
export function lighten(nominal: string, backgrounds: readonly string[], minimum: number): string {
  const [h, s, l] = hsl(rgb(nominal));
  for (let step = 0; step <= 100; step++) {
    const candidate = fromHsl([h, s, Math.min(1, l + step / 100)]);
    if (meetsAll(candidate, backgrounds, minimum)) return candidate;
  }
  return '#ffffff';
}

/** Letra branca ou negra, a que máis contraste dea sobre un recheo de cor (chip «solid»). */
export function onFill(fill: string): string {
  return contrastRatio('#ffffff', fill) >= contrastRatio('#000000', fill) ? '#ffffff' : '#000000';
}
