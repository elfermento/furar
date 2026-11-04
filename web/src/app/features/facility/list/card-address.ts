// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** O enderezo tal como o pinta unha tarxeta (sen número, sen vía: o que haxa). */
export interface CardAddressInput {
  readonly street?: string | null;
  readonly streetNumber?: string | null;
  readonly postalCode?: string | null;
  readonly locality?: string | null;
}

/** Enderezo da tarxeta: «Rúa do Alto, 12 · 15570 Narón» (baleiro nun borrador sen enderezo). */
export function cardAddress({ address }: { readonly address?: CardAddressInput | null }): string {
  if (!address) return '';
  const { street, streetNumber, postalCode, locality } = address;
  const line = [street, streetNumber].filter(Boolean).join(', ');
  const place = [postalCode, locality].filter(Boolean).join(' ');
  return [line, place].filter(Boolean).join(' · ');
}
