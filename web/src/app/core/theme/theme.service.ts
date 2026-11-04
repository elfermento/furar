// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import type { ThemeColors } from './contrast';
import type * as ThemeTokens from './theme-tokens';
import type { DerivedTokens } from './theme-tokens';

type TokensModule = typeof ThemeTokens;
let tokensModule: Promise<TokensModule> | null = null;

/**
 * O cálculo das cores derivadas (contraste, mesturas, tons de estado) vai nun chunk á parte, fóra
 * da carga inicial (D27). `CurrentMunicipality` pídeo xa ao comezar a cargar o concello, en
 * paralelo coa API, e agarda polo tema antes de pintar: sen parpadeo (ADR-0014).
 */
export function loadThemeTokens(): Promise<TokensModule> {
  tokensModule ??= import('./theme-tokens');
  return tokensModule;
}

const VARIABLES: Record<keyof ThemeColors, string> = {
  primary: '--furar-primary',
  onPrimary: '--furar-on-primary',
  accent: '--furar-accent',
  background: '--furar-background',
  text: '--furar-text',
};

/**
 * Onde queda o esquema (claro ou escuro) do último tema aplicado, para Keycloak (`/auth`, a mesma
 * orixe): o seu tema de acceso pinta igual ca a web antes de mostrar nada (template.ftl). Non é un
 * dato persoal: só `light` ou `dark`.
 */
export const COLOR_SCHEME_KEY = 'furar.colorScheme';

/**
 * Aplica o tema do concello como variables CSS en <html>: os 5 campos (--furar-*) ao momento e as
 * cores derivadas con contraste comprobado (--f-*, deriveTokens) en canto está o seu chunk. Por
 * CSSOM: a CSP non cambia. O esquema claro ou escuro tamén queda en `localStorage`
 * ({@link COLOR_SCHEME_KEY}) para as páxinas de Keycloak.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  /** O chunk das cores derivadas, se xa chegou (sen el non hai nada derivado que quitar). */
  private tokens: TokensModule | null = null;
  /** Cada `apply` ou `reset` invalida un `apply` anterior que aínda agarde polo chunk. */
  private run = 0;

  /** Resólvese cando as cores derivadas xa están postas (ou non tocan: tema incompleto). */
  async apply(theme: ThemeColors): Promise<void> {
    const run = ++this.run;
    const style = this.document.documentElement.style;
    for (const [key, variable] of Object.entries(VARIABLES) as [keyof ThemeColors, string][]) {
      style.setProperty(variable, theme[key]);
    }
    this.document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme.primary);
    this.removeDerived();
    const tokens = await loadThemeTokens();
    this.tokens = tokens;
    if (run !== this.run || !tokens.isCompleteTheme(theme)) return;
    const derived = tokens.deriveTokens(theme);
    for (const [key, variable] of Object.entries(tokens.TOKEN_VARIABLES) as [keyof DerivedTokens, string][]) {
      style.setProperty(variable, derived[key]);
    }
    this.rememberScheme(derived.colorScheme);
  }

  /** Volve ao tema por defecto de styles.css e tokens.css (p. ex. na páxina de escoller concello). */
  reset(): void {
    this.run++;
    const style = this.document.documentElement.style;
    for (const variable of Object.values(VARIABLES)) {
      style.removeProperty(variable);
    }
    this.removeDerived();
    this.rememberScheme(null);
  }

  /** Garda (ou borra) o esquema para Keycloak; sen almacenamento, Keycloak vese claro. */
  private rememberScheme(scheme: string | null): void {
    try {
      const storage = this.document.defaultView?.localStorage;
      if (scheme) storage?.setItem(COLOR_SCHEME_KEY, scheme);
      else storage?.removeItem(COLOR_SCHEME_KEY);
    } catch {
      // Almacenamento bloqueado: nada que gardar.
    }
  }

  private removeDerived(): void {
    if (!this.tokens) return;
    const style = this.document.documentElement.style;
    for (const variable of Object.values(this.tokens.TOKEN_VARIABLES)) {
      style.removeProperty(variable);
    }
  }
}
