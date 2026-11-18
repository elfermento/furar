// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LocalizedText } from './custom-field.types';

/** Texto no idioma pedido; se falta, o outro (as etiquetas son obrigatorias nos dous). */
export function localized(text: LocalizedText | null | undefined, lang: string): string {
  if (!text) {
    return '';
  }
  const primary = lang === 'es' ? text.es : text.gl;
  return primary || text.gl || text.es;
}
