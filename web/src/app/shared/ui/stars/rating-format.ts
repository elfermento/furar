// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * A nota media cun decimal, no idioma de quen mira: «4,6». En galego, co formato de `es` (coma
 * decimal), como o resto dos números: hai navegadores sen `gl` en Intl.
 */
export function formatAverage(value: number, lang: string): string {
  return new Intl.NumberFormat(lang === 'gl' ? 'es' : lang, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value);
}
