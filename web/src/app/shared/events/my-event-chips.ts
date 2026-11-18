// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TagTone } from '../ui/tag/tag.component';
import { ordinal } from './event-format';
import type { MyEventRegistrationResponse } from './public-events.store';

type Status = NonNullable<MyEventRegistrationResponse['status']>;
type Payment = NonNullable<MyEventRegistrationResponse['payment']>;

/**
 * As etiquetas dunha inscrición: a da inscrición (`status` vai en `data-chip`; o texto, na clave
 * `key` cos seus `params`) e, se é de pago, a do pago (`event.payment.*`).
 */
export interface RegistrationChips {
  readonly status: {
    readonly status: Status;
    readonly key: string;
    readonly params: Readonly<Record<string, unknown>>;
    readonly tone: TagTone;
  };
  readonly payment: { readonly key: Payment; readonly tone: TagTone } | null;
}

const STATUS_TONE: Record<Status, TagTone> = {
  CONFIRMED: 'success',
  PENDING: 'warn',
  REJECTED: 'danger',
  WAITING: 'muted',
  NOTIFIED: 'warn',
};
const PAYMENT_TONE: Record<Payment, TagTone> = { PENDING: 'warn', PAID: 'success', WAIVED: 'info' };

/**
 * Apuntada en verde, Pendente en ámbar e Rexeitada en vermello (a paleta de estados); na lista de
 * espera, en gris co posto («Na lista · 3.ª»), e avisada, en ámbar («Praza libre para ti»). O pago
 * só nun evento de pago e coa inscrición aceptada: Pendente de pago, Pagada ou Gratis (exento). Sen
 * estado, Apuntada.
 */
export function registrationChips(
  item: Pick<MyEventRegistrationResponse, 'status' | 'payment' | 'event'> &
    Partial<Pick<MyEventRegistrationResponse, 'position'>>,
): RegistrationChips {
  const status = item.status ?? 'CONFIRMED';
  const waitingAt = status === 'WAITING' && item.position ? item.position : null;
  const payment =
    status === 'CONFIRMED' && item.event.priceCents && item.payment
      ? { key: item.payment, tone: PAYMENT_TONE[item.payment] }
      : null;
  return {
    status: {
      status,
      key: waitingAt ? 'event.mine.waitingAt' : 'event.mine.' + status,
      params: waitingAt ? { position: ordinal(waitingAt) } : {},
      tone: STATUS_TONE[status],
    },
    payment,
  };
}
