// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ThemeColors } from './contrast';
import { lighten, mix, onFill, rgb } from './color';
import { NON_TEXT_CONTRAST, TEXT_CONTRAST, contrastRatio, isHexColor, luminance } from './contrast';
import { STATE_TONES, chipMix, fills } from './state-tones';

export { STATE_TONES } from './state-tones';

export { mix, onFill } from './color';

/**
 * Cores do sistema de deseño derivadas do tema do concello (docs/deseno.md §4, ADR-0021).
 * CSS non sabe se o fondo é claro ou escuro nin cando un tinte rompe o contraste; isto si:
 * os tintes baixan desde o nominal e os textos e bordos derivados achéganse á tinta do concello
 * ata cumprir 4,5:1 (texto) e 3:1 (bordos e foco) para calquera tema que admita a API.
 */
export interface DerivedTokens {
  readonly page: string;
  readonly surface: string;
  readonly tint: string;
  readonly ink: string;
  readonly inkSoft: string;
  readonly divider: string;
  readonly line: string;
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
  readonly onDanger: string;
  readonly onSuccess: string;
  readonly onWarn: string;
  readonly onInfo: string;
  readonly onMuted: string;
  readonly softMix: string;
  readonly chipMix: string;
  readonly glass: string;
  readonly colorScheme: 'light' | 'dark';
}

/** Variable CSS de cada token (en tokens.css van os valores do tema por defecto). */
export const TOKEN_VARIABLES: Readonly<Record<keyof DerivedTokens, string>> = {
  page: '--f-page',
  surface: '--f-surface',
  tint: '--f-tint',
  ink: '--f-ink',
  inkSoft: '--f-ink-soft',
  divider: '--f-divider',
  line: '--f-line',
  primary: '--f-primary',
  primaryHover: '--f-primary-hover',
  onPrimary: '--f-on-primary',
  primaryText: '--f-primary-text',
  accent: '--f-accent',
  focus: '--f-focus',
  danger: '--f-danger',
  success: '--f-success',
  warn: '--f-warn',
  info: '--f-info',
  muted: '--f-muted',
  dangerFill: '--f-danger-fill',
  successFill: '--f-success-fill',
  warnFill: '--f-warn-fill',
  infoFill: '--f-info-fill',
  mutedFill: '--f-muted-fill',
  onDanger: '--f-on-danger',
  onSuccess: '--f-on-success',
  onWarn: '--f-on-warn',
  onInfo: '--f-on-info',
  onMuted: '--f-on-muted',
  softMix: '--f-soft-mix',
  chipMix: '--f-chip-mix',
  glass: '--f-glass',
  colorScheme: 'color-scheme',
};

/** Tema por defecto (o mesmo que :root en styles.css). */
export const DEFAULT_THEME: ThemeColors = {
  primary: '#0b5c8e',
  onPrimary: '#ffffff',
  accent: '#8a4b00',
  background: '#ffffff',
  text: '#1a1a1a',
};

const NOMINAL = {
  page: 4,
  tint: 10,
  soft: 8,
  glass: 0.9,
  inkSoft: 0.72,
  line: 0.55,
  divider: 0.16,
  hover: 0.15,
};

/**
 * Fondo escuro (§4.5): as cards soben pouco sobre a páxina (non hai sombra que se vexa en negro, pero
 * a superficie segue case negra; o resto sepárano divisorias e liñas) e as cores con ton sóbense de
 * luz sen perder o matiz, cun contraste máis folgado que o mínimo: un azul escuro sobre negro cumpre
 * 4,5:1 e aínda así cústalle ler. O primary do botón destaca a 4,5:1. O texto suave baixa máis ca no
 * claro para que se distinga da tinta case branca.
 */
const DARK = { lift: 4, tint: 6, inkSoft: 0.6, text: 6, primary: 4.5 };

const meets = (color: string, backgrounds: readonly string[], minimum: number) =>
  backgrounds.every((background) => contrastRatio(color, background) >= minimum);

/** Tinte do primary sobre o fondo: baixa punto a punto ata que todo o que vai enriba cumpra. */
function tint(theme: ThemeColors, nominal: number): string {
  for (let percent = nominal; percent > 0; percent--) {
    const candidate = mix(theme.primary, theme.background, percent / 100);
    if (
      meets(theme.text, [candidate], TEXT_CONTRAST) &&
      meets(theme.accent, [candidate], TEXT_CONTRAST) &&
      meets(theme.primary, [candidate], NON_TEXT_CONTRAST)
    ) {
      return candidate;
    }
  }
  return theme.background;
}

/** Parte de `nominal` e achégase á tinta do concello ata cumprir `minimum` sobre os fondos. */
function towardInk(ink: string, nominal: string, backgrounds: readonly string[], minimum: number): string {
  for (let step = 0; step <= 50; step++) {
    const candidate = mix(ink, nominal, step / 50);
    if (meets(candidate, backgrounds, minimum)) {
      return candidate;
    }
  }
  return ink;
}

/** Cor de hover do primary: afástase da súa tinta, así o contraste con on-primary só sobe. */
function primaryHover(primary: string, onPrimary: string): string {
  const extreme = luminance(onPrimary) > 0.18 ? '#000000' : '#ffffff';
  return mix(extreme, primary, NOMINAL.hover);
}

/**
 * Superficie translúcida (§6): só se o texto aguanta co peor contido debaixo. Debaixo dunha barra
 * fixa pode pasar texto na tinta, un botón primary (ou a cabeceira) e un texto de erro.
 */
function glass(surface: string, ink: string, primary: string, danger: string): string {
  const under = [ink, primary, danger];
  const composited = under.map((color) => mix(surface, color, NOMINAL.glass));
  if (!meets(ink, composited, TEXT_CONTRAST)) {
    return surface;
  }
  const [r, g, b] = rgb(surface);
  return `rgb(${r} ${g} ${b} / ${NOMINAL.glass})`;
}

export function deriveTokens(theme: ThemeColors): DerivedTokens {
  return luminance(theme.background) < 0.18 ? deriveDark(theme) : deriveLight(theme);
}

function deriveLight(theme: ThemeColors): DerivedTokens {
  const { text: ink, background: surface } = theme;
  const page = tint(theme, NOMINAL.page);
  const tinted = tint(theme, NOMINAL.tint);
  const all = [page, surface, tinted];
  const textOn = (nominal: string) => towardInk(ink, nominal, all, TEXT_CONTRAST);
  const danger = textOn(STATE_TONES.danger);
  const states = {
    danger,
    success: textOn(STATE_TONES.success),
    warn: textOn(STATE_TONES.warn),
    info: textOn(STATE_TONES.info),
  };
  const muted = textOn(STATE_TONES.muted);
  return {
    page,
    surface,
    tint: tinted,
    ink,
    inkSoft: textOn(mix(ink, surface, NOMINAL.inkSoft)),
    divider: mix(ink, surface, NOMINAL.divider),
    line: towardInk(ink, mix(ink, surface, NOMINAL.line), all, NON_TEXT_CONTRAST),
    primary: theme.primary,
    primaryHover: primaryHover(theme.primary, theme.onPrimary),
    onPrimary: theme.onPrimary,
    primaryText: textOn(theme.primary),
    accent: towardInk(ink, theme.accent, [page, surface], TEXT_CONTRAST),
    focus: towardInk(ink, theme.primary, all, NON_TEXT_CONTRAST),
    ...states,
    muted,
    ...fills(),
    ...mixes(states, surface, ink),
    chipMix: chipMix({ ...states, muted }, surface),
    glass: glass(surface, ink, theme.primary, danger),
    colorScheme: 'light',
  };
}

function deriveDark(theme: ThemeColors): DerivedTokens {
  const { text: ink, background: page } = theme;
  const surface = raised(page, ink, DARK.lift);
  const tinted = raised(surface, ink, DARK.tint, theme.primary);
  const all = [page, surface, tinted];
  const textOn = (nominal: string) => lighten(nominal, all, DARK.text);
  const primary = lighten(theme.primary, all, DARK.primary);
  const onPrimary =
    primary === theme.primary && contrastRatio(theme.onPrimary, primary) >= TEXT_CONTRAST
      ? theme.onPrimary
      : onFill(primary);
  const danger = textOn(STATE_TONES.danger);
  const states = {
    danger,
    success: textOn(STATE_TONES.success),
    warn: textOn(STATE_TONES.warn),
    info: textOn(STATE_TONES.info),
  };
  const muted = textOn(STATE_TONES.muted);
  return {
    page,
    surface,
    tint: tinted,
    ink,
    inkSoft: towardInk(ink, mix(ink, surface, DARK.inkSoft), all, TEXT_CONTRAST),
    divider: mix(ink, surface, NOMINAL.divider),
    line: towardInk(ink, mix(ink, surface, NOMINAL.line), all, NON_TEXT_CONTRAST),
    primary,
    primaryHover: primaryHover(primary, onPrimary),
    onPrimary,
    primaryText: textOn(primary),
    accent: lighten(theme.accent, [page, surface], DARK.text),
    focus: lighten(primary, all, NON_TEXT_CONTRAST),
    ...states,
    muted,
    ...fills(),
    ...mixes(states, surface, ink),
    chipMix: chipMix({ ...states, muted }, surface),
    glass: glass(surface, ink, primary, danger),
    colorScheme: 'dark',
  };
}

/** Superficie que sobe sobre `base` (branco, ou o primary no tinte) mentres a tinta aguante 4,5:1. */
function raised(base: string, ink: string, nominal: number, toward = '#ffffff'): string {
  for (let percent = nominal; percent > 0; percent--) {
    const candidate = mix(toward, base, percent / 100);
    if (meets(ink, [candidate], TEXT_CONTRAST)) return candidate;
  }
  return base;
}

interface States {
  readonly danger: string;
  readonly success: string;
  readonly warn: string;
  readonly info: string;
}

/**
 * Canto ton se mestura no fondo suave dos avisos (callout.css):
 * baixa desde o nominal ata que a tinta siga a 4,5:1 e a icona do ton a 3:1 sobre a mestura.
 */
function mixes(states: States, surface: string, ink: string) {
  const fits = (percent: number) =>
    Object.values(states).every((tone) => {
      const fill = mix(tone, surface, percent / 100);
      return contrastRatio(tone, fill) >= NON_TEXT_CONTRAST && contrastRatio(ink, fill) >= TEXT_CONTRAST;
    });
  let percent = NOMINAL.soft;
  while (percent > 0 && !fits(percent)) percent--;
  return { softMix: `${percent}%` };
}

/** Tema completo e con cores válidas: só entón se deriva (senón, quedan os valores por defecto). */
export function isCompleteTheme(theme: ThemeColors): boolean {
  return Object.values(theme).every((value) => typeof value === 'string' && isHexColor(value));
}
