// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { MAX_SEATS, ParticipantsSelection } from '../../../shared/dependents/participants';
import { ReserveStore } from '../reserve/reserve.store';
import { companionValid } from './book-for-seats.component';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ConfirmedBookingStore } from '../reserve/confirmed-booking.store';
import { ReserveCustomFields } from '../reserve/reserve-custom-fields';
import { UseAvailabilityStore } from '../reserve/use-availability.store';
import type { HolderDependentResponse, StaffBookingParticipantsRequest } from './book-for.store';
import { BookForStore, EMAIL_PATTERN } from './book-for.store';
import type { ManagedBookingDetailResponse } from './manage-bookings';

/** O aviso de debaixo do correo. */
export type HolderHint = 'account' | 'email' | 'noEmail';

/**
 * «Prazas» da reserva que fai ou cambia o persoal (ADR-0031, punto 7): as persoas a
 * cargo da conta do correo (pídense ao saír do campo do correo, e só nos usos por prazas: cada
 * lectura queda no rexistro de actividade), o que se manda e o aviso de debaixo do correo. Un por
 * páxina, co `ParticipantsSelection` e o `ReserveStore` da páxina.
 */
@Injectable()
export class BookForPeople {
  private readonly store = inject(BookForStore);
  private readonly reserve = inject(ReserveStore);
  private readonly who = inject(ParticipantsSelection);

  /** O correo (normalizado) do que son as persoas a cargo que hai á vista. */
  private readonly lookedUp = signal<string | null>(null);
  /** «Prazas» da reserva que se cambia, para mandalas só se cambian. */
  private original: string | null = null;
  /** O último correo co que se saíu do campo (ou o que se corrixiu), por se o uso por prazas chega despois. */
  private readonly left = signal<{ slug: string; email: string } | null>(null);
  /** O erro da lectura que se fixo soa (ao escoller o uso por prazas despois do correo). */
  readonly problem = signal<ApiProblem | null>(null);

  constructor() {
    // O uso por prazas escóllese despois de escribir o correo: as fichas pídense entón, sen ter que
    // volver ao campo. A mesma lectura non se repite (`loadHolderDependents`).
    effect(() => {
      const left = this.left();
      if (this.reserve.bySeats() && left) {
        untracked(() => void this.lookup(left.slug, left.email).then((problem) => this.problem.set(problem)));
      }
    });
  }

  /** As persoas a cargo da conta dese correo; baleira mentres non se piden (ou se cambiou o correo). */
  people(email: string): readonly HolderDependentResponse[] {
    const asked = this.lookedUp();
    return asked !== null && (asked === '' || asked === key(email)) ? this.store.holderDependents() : [];
  }

  /**
   * «Ten conta: a reserva será súa» só cando a conta do correo ten persoas a cargo (a API non di
   * se un correo ten conta); con correo, a reserva queda ao seu nome; sen el, non hai confirmación.
   */
  hint(email: string): HolderHint {
    if (this.people(email).length) return 'account';
    return email.trim() ? 'email' : 'noEmail';
  }

  /**
   * Ao saír do correo, nun uso por prazas: as persoas a cargo da conta dese correo. Devolve o erro
   * se a lectura falla (por exemplo, demasiadas consultas seguidas).
   */
  async lookup(slug: string, email: string): Promise<ApiProblem | null> {
    const typed = key(email);
    const last = this.left();
    if (last?.slug !== slug || last.email !== typed) this.left.set({ slug, email: typed });
    if (!this.reserve.bySeats() || !EMAIL_PATTERN.test(typed)) {
      this.lookedUp.set(null);
      this.who.keep([]);
      return null;
    }
    try {
      await this.store.loadHolderDependents(slug, { email: typed });
    } catch (error) {
      return asProblem(error);
    }
    this.lookedUp.set(typed);
    this.who.keep(this.store.holderDependents().map((person) => person.id));
    return null;
  }

  /** «6,00 € · 2 prazas»: o total coas prazas cando son varias. */
  total(amount: string | null, translate: (key: string, params: Record<string, unknown>) => string): string | null {
    const seats = this.reserve.seats();
    return amount && seats > 1 ? translate('booking.price.seats', { amount, count: seats }) : amount;
  }

  /** Ao cambiar unha reserva por prazas: as súas persoas marcadas e as fichas da conta do seu correo. */
  async prefill(slug: string, booking: ManagedBookingDetailResponse): Promise<void> {
    if (booking.rentalMode !== 'SEATS') return;
    const people = booking.participants;
    this.who.set(
      people.some((p) => p.kind === 'SELF'),
      people.flatMap((p) => (p.dependentId ? [p.dependentId] : [])),
      people
        .filter((p) => p.kind === 'COMPANION')
        .map((p) => ({ name: p.name, year: p.birthYear ? String(p.birthYear) : '' })),
    );
    this.original = JSON.stringify(this.request());
    if (booking.holderEmail) {
      await this.store.loadHolderDependents(slug, { bookingId: booking.id });
      this.lookedUp.set(booking.holderAccount ? '' : key(booking.holderEmail));
    }
  }

  /** Se «Prazas» se pode gardar: alguén, non máis de once e os acompañantes ben escritos. */
  valid(): boolean {
    if (!this.reserve.bySeats()) return true;
    const seats = this.who.seats();
    return seats >= 1 && seats <= MAX_SEATS && this.who.companions().every(companionValid);
  }

  /** O que se manda; ao cambiar, só se «Prazas» cambiou (sen el, a API deixa as mesmas persoas). */
  body(changing: boolean): { participants?: StaffBookingParticipantsRequest } {
    if (!this.reserve.bySeats()) return {};
    const participants = this.request();
    return changing && JSON.stringify(participants) === this.original ? {} : { participants };
  }

  private request(): StaffBookingParticipantsRequest {
    return {
      self: this.who.self(),
      dependentIds: [...this.who.dependentIds()],
      companions: this.who.companions().map((c) => ({ name: c.name.trim(), birthYear: Number(c.year) })),
    };
  }
}

function key(email: string): string {
  return email.trim().toLowerCase();
}

/** O que provee a páxina de reservar no nome doutra persoa. */
export const BOOK_FOR_PROVIDERS = [
  BookForStore,
  BookForPeople,
  ReserveStore,
  ConfirmedBookingStore,
  UseAvailabilityStore,
  ReserveCustomFields,
  ParticipantsSelection,
];
