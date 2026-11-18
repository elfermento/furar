// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { Injectable, effect, inject, untracked } from '@angular/core';
import { SessionService } from '../../../core/auth/session.service';
import { DependentsStore } from '../../../shared/dependents/dependents.store';
import type { BookedParticipant } from '../../../shared/dependents/participants';
import { ParticipantsSelection } from '../../../shared/dependents/participants';

/** «Para quen» tal como se manda ao reservar ou cambiar. */
export interface WhoBody {
  readonly self: boolean;
  readonly dependentIds: string[];
}

/**
 * «Para quen» da páxina de reservar (ADR-0031): as persoas a cargo da conta (pídense a
 * primeira vez que se escolle un uso por prazas, con sesión; as que se eliminan deixan de contar),
 * as da reserva que se cambia e o que se manda. Só na páxina da persoa: no mostrador as persoas
 * son as da conta do correo (`BookForPeople`).
 */
@Injectable()
export class ReserveParticipants {
  private readonly who = inject(ParticipantsSelection);
  private readonly dependents = inject(DependentsStore);
  private readonly session = inject(SessionService);
  /** «Para quen» da reserva que se cambia, para mandalo só se cambia. */
  private original: string | null = null;

  /** Segue o uso escollido: cando é por prazas, fan falta as persoas a cargo. */
  watch(bySeats: Signal<boolean>): void {
    effect(() => {
      if (bySeats() && this.session.authenticated()) untracked(() => void this.dependents.load());
    });
    effect(() => {
      const items = this.dependents.items();
      // As da reserva que se cambia quedan aínda que xa non estean na conta (a API mantenas).
      if (this.dependents.state() === 'ready' && this.original === null) {
        untracked(() => this.who.keep(items.map((item) => item.id)));
      }
    });
  }

  /** Modo cambio: as persoas da reserva, marcadas. */
  startChange(participants: readonly BookedParticipant[]): void {
    const dependentIds = participants.flatMap((p) => (p.dependentId ? [p.dependentId] : []));
    const self = participants.some((p) => p.kind === 'SELF');
    this.who.set(self, dependentIds);
    this.original = key({ self, dependentIds });
  }

  /**
   * O que se manda. Ao cambiar, só se a persoa o tocou: sen el, a API deixa as mesmas persoas
   * (tamén as que xa non están na conta e os acompañantes que escribiu o persoal).
   */
  body(changing: boolean): { participants?: WhoBody } {
    const participants = { self: this.who.self(), dependentIds: [...this.who.dependentIds()] };
    return changing && key(participants) === this.original ? {} : { participants };
  }
}

function key(who: WhoBody): string {
  return `${who.self}|${[...who.dependentIds].sort().join(',')}`;
}
