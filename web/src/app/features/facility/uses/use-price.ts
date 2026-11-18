// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatCents } from '../../../shared/ui/money/money';
import type { Use } from './uses.store';

export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** Prezo dunha fila: «Gratis», «Sen prezo» ou «45,00 €». */
export function priceText(use: Pick<Use, 'price'>, t: Translate): string {
  const { kind, amountCents } = use.price;
  if (kind === 'PAID' && typeof amountCents === 'number') return formatCents(amountCents);
  return t(`facility.uses.price.${kind === 'PAID' ? 'NONE' : kind}`);
}

/** A que se refire un prezo de pago: «por persoa» ou «por reserva de 60 min»; se non, nada. */
export function pricePer(use: Pick<Use, 'price' | 'rentalMode' | 'slotMinutes'>, t: Translate): string | null {
  if (use.price.kind !== 'PAID' || typeof use.price.amountCents !== 'number') return null;
  return use.rentalMode === 'SEATS'
    ? t('facility.uses.price.perPerson')
    : `${t('facility.uses.price.perBooking')} ${t('facility.uses.slot.option', { minutes: use.slotMinutes })}`;
}

/** Sen prezo: vai en ton suave. */
export function priceMuted(use: Pick<Use, 'price'>): boolean {
  return use.price.kind === 'NONE' || (use.price.kind === 'PAID' && typeof use.price.amountCents !== 'number');
}
