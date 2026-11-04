// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { MissingItem } from './facility-detail.store';

/**
 * `id` estables das seccións da ficha (D1-21a). O aviso de borrador liga a eles e leva o foco ao
 * seu `h2`; cada sección vive dentro do seu contedor, así que o `id` non depende do compoñente.
 */
export const SECTION_IDS = {
  photos: 'facility-photos',
  data: 'facility-data',
  uses: 'facility-uses',
  description: 'facility-description',
  houseRules: 'facility-house-rules-section',
  paymentNotes: 'facility-payment-notes-section',
  schedule: 'facility-schedule',
  blocks: 'facility-blocks',
  amenities: 'facility-amenities',
  location: 'facility-location',
} as const;

export type SectionKey = keyof typeof SECTION_IDS;

/** Onde se arranxa cada cousa que lle falta á instalación para saír de borrador. */
export const MISSING_SECTION: Record<MissingItem, SectionKey> = {
  SPORTS: 'data',
  USES: 'uses',
  SCHEDULE: 'schedule',
};

/**
 * Leva o foco ao título (`h2`) da sección: a ligazón do aviso non salta só a vista, tamén o lector
 * e o teclado seguen desde aí. Devolve se o atopou.
 */
export function focusSection(root: ParentNode, key: SectionKey): boolean {
  const heading = root.querySelector<HTMLElement>(`#${SECTION_IDS[key]} h2`);
  if (!heading) return false;
  if (!heading.hasAttribute('tabindex')) heading.tabIndex = -1;
  heading.scrollIntoView?.({ block: 'start' });
  heading.focus();
  return true;
}
