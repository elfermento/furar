// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LegalDetailsResponse } from '../../api/models/legal-details-response';

/** As tres páxinas legais do concello (ADR-0017 punto 8), na orde das pestanas e do pé. */
export const LEGAL_PAGES = ['privacy', 'accessibility', 'legalNotice'] as const;
export type LegalPageKey = (typeof LEGAL_PAGES)[number];

/**
 * Como se publica cada páxina: a de Furar («Páxina desta web»), un texto propio, a páxina do
 * concello ou ningunha («Sen páxina»: o pé non a liga; a obriga legal é do concello).
 */
export type LegalPageMode = LegalDetailsResponse['privacyMode'];
export const LEGAL_PAGE_MODES: readonly LegalPageMode[] = ['FURAR', 'TEXT', 'URL', 'NONE'];

/** Ruta de cada páxina dentro do concello (/:concello/privacidade…). */
export const LEGAL_PAGE_PATHS: Readonly<Record<LegalPageKey, string>> = {
  privacy: 'privacidade',
  accessibility: 'accesibilidade',
  legalNotice: 'aviso-legal',
};

/** Clave do modo e do enderezo de cada páxina nos datos legais. */
export const LEGAL_PAGE_FIELDS = {
  privacy: { mode: 'privacyMode', url: 'privacyUrl' },
  accessibility: { mode: 'accessibilityMode', url: 'accessibilityUrl' },
  legalNotice: { mode: 'legalNoticeMode', url: 'legalNoticeUrl' },
} as const satisfies Record<LegalPageKey, { mode: keyof LegalDetailsResponse; url: keyof LegalDetailsResponse }>;

export function legalPageMode(legal: LegalDetailsResponse | undefined, page: LegalPageKey): LegalPageMode {
  return legal?.[LEGAL_PAGE_FIELDS[page].mode] ?? 'FURAR';
}

/** Se a páxina se publica (aquí ou fóra): no modo «Sen páxina», non. */
export function legalPagePublished(legal: LegalDetailsResponse | undefined, page: LegalPageKey): boolean {
  return legalPageMode(legal, page) !== 'NONE';
}

/**
 * O enderezo ao que levan as ligazóns de Furar se o concello publica a páxina na súa web (modo
 * «Páxina propia»); `null` se a páxina é a de Furar ou un texto propio.
 */
export function externalLegalUrl(legal: LegalDetailsResponse | undefined, page: LegalPageKey): string | null {
  if (legalPageMode(legal, page) !== 'URL') return null;
  const url = legal?.[LEGAL_PAGE_FIELDS[page].url];
  return url && /^https:\/\//i.test(url) ? url : null;
}
