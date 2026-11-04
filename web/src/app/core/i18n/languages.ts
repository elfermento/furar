// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/** Idiomas da aplicación (ADR-0003): galego por defecto. */
export const LANGUAGES = ['gl', 'es'] as const;
export type Language = (typeof LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Language = 'gl';

export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value);
}

/** Primeiro idioma soportado dunha lista tipo navigator.languages ("es-ES", "gl"...). */
export function pickLanguage(candidates: readonly string[]): Language | undefined {
  for (const candidate of candidates) {
    const base = candidate.toLowerCase().split('-')[0];
    if (isLanguage(base)) {
      return base;
    }
  }
  return undefined;
}
