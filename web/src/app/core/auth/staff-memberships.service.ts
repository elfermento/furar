// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Api } from '../../api/api';
import { listMyStaffMemberships } from '../../api/fn/staff/list-my-staff-memberships';
import type { StaffRole } from './roles';
import { SessionService } from './session.service';

/** Un concello onde a persoa da sesión é persoal activo (`GET /api/v1/me/staff-memberships`). */
export interface StaffMembership {
  readonly slug: string;
  readonly name: string;
  readonly role: StaffRole;
}

interface Loaded {
  readonly subject: string;
  readonly memberships: readonly StaffMembership[];
}

/**
 * Os concellos da persoa da sesión como persoal (ADR-0027, revisión do 2026-11-03): o cambio de
 * concello do seu menú. Garda os dunha persoa; outra sesión volve preguntar. Só para a interface.
 */
@Injectable({ providedIn: 'root' })
export class StaffMembershipsService {
  private readonly api = inject(Api);
  private readonly session = inject(SessionService);
  private readonly loaded = signal<Loaded | null>(null);
  private pending: { readonly subject: string; readonly promise: Promise<readonly StaffMembership[]> } | null = null;

  /** Os da persoa da sesión; baleiro sen sesión, mentres carga ou se falla. */
  readonly memberships = computed<readonly StaffMembership[]>(() => {
    const subject = this.subject();
    const loaded = this.loaded();
    return subject !== null && loaded?.subject === subject ? loaded.memberships : [];
  });

  constructor() {
    // O menú precisa a lista en calquera páxina con sesión.
    effect(() => {
      if (this.subject() !== null) untracked(() => void this.load());
    });
  }

  load(): Promise<readonly StaffMembership[]> {
    const subject = this.subject();
    if (subject === null) return Promise.resolve([]);
    const loaded = this.loaded();
    if (loaded?.subject === subject) return Promise.resolve(loaded.memberships);
    if (this.pending?.subject === subject) return this.pending.promise;
    const promise = this.fetch().then((memberships) => {
      if (this.pending?.subject === subject) {
        this.pending = null;
        this.loaded.set({ subject, memberships });
      }
      return memberships;
    });
    this.pending = { subject, promise };
    return promise;
  }

  private async fetch(): Promise<readonly StaffMembership[]> {
    try {
      const response = await this.api.invoke(listMyStaffMemberships, {});
      return response.map((m) => ({ slug: m.slug, name: m.name, role: m.role }));
    } catch {
      // Sen correo verificado ou sen rede: sen cambio de concello (a API decide igual).
      return [];
    }
  }

  private subject(): string | null {
    const subject = this.session.subject();
    return this.session.authenticated() && subject ? subject : null;
  }
}
