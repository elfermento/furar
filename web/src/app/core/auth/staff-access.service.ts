// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Api } from '../../api/api';
import { getMyStaffAccess } from '../../api/fn/staff/get-my-staff-access';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { Role, StaffRole } from './roles';
import { canManage, canViewBookings, mainRole } from './roles';
import { PlatformAccessService } from './platform-access.service';
import { SessionService } from './session.service';

/** O acceso da persoa da sesión á xestión dun concello (`GET …/me/staff`). */
export interface StaffAccess {
  /** Rol de persoal no concello; `null` se non é persoal. */
  readonly role: StaffRole | null;
  /** Acceso anterior ao desta sesión (ENS op.acc.6 R5), en ISO 8601; `null` se é o primeiro. */
  readonly previousSignInAt: string | null;
  /**
   * Conta de traballo: persoal activo deste ou doutro concello. Non reserva como veciña (ADR-0027,
   * revisión do 2026-10-30); a reserva avísao antes.
   */
  readonly workAccount: boolean;
}

interface Loaded {
  readonly key: string;
  readonly access: StaffAccess | null;
}

/**
 * O rol de persoal sae da API (ADR-0027), non do claim do token: así unha baixa ou un cambio de
 * rol vale sen esperar a que caduque a sesión. Garda só o acceso dun concello e dunha persoa: ao
 * cambiar de concello ou de sesión, volve preguntar. Só para a interface (a API decide sempre).
 */
@Injectable({ providedIn: 'root' })
export class StaffAccessService {
  private readonly api = inject(Api);
  private readonly session = inject(SessionService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly platform = inject(PlatformAccessService);
  private readonly loaded = signal<Loaded | null>(null);
  private pending: { readonly key: string; readonly promise: Promise<StaffAccess | null> } | null = null;

  /** O acceso no concello actual; `null` sen sesión, sen concello, mentres carga ou se falla. */
  readonly current = computed<StaffAccess | null>(() => {
    const key = this.key(this.municipality.slug());
    const loaded = this.loaded();
    return key !== null && loaded?.key === key ? loaded.access : null;
  });
  readonly role = computed(() => this.current()?.role ?? null);
  readonly previousSignInAt = computed(() => this.current()?.previousSignInAt ?? null);
  /** Xestión do concello actual (só MANAGER; a plataforma non). */
  readonly canManage = computed(() => canManage(this.role()));
  /** Reservas do concello actual: xestión e persoal do mostrador. */
  readonly canViewBookings = computed(() => canViewBookings(this.role()));
  /** Conta de traballo nalgún concello: non reserva como veciña. */
  readonly workAccount = computed(() => this.current()?.workAccount ?? false);
  /** O rol que se mostra na barra: a plataforma por riba do de persoal. */
  readonly mainRole = computed<Role | null>(() => mainRole(this.platform.platformAdmin(), this.role()));

  constructor() {
    // A barra precisa o rol en calquera páxina do concello, non só nas da xestión.
    effect(() => {
      const slug = this.municipality.slug();
      if (slug !== null && this.key(slug) !== null) {
        untracked(() => void this.load(slug));
      }
    });
  }

  /** O acceso nese concello (as gardas chámano antes de que o resolver cambie o concello actual). */
  load(slug: string): Promise<StaffAccess | null> {
    const key = this.key(slug);
    if (key === null) {
      return Promise.resolve(null);
    }
    const loaded = this.loaded();
    if (loaded?.key === key) {
      return Promise.resolve(loaded.access);
    }
    if (this.pending?.key === key) {
      return this.pending.promise;
    }
    const promise = this.fetch(slug).then((access) => {
      if (this.pending?.key === key) {
        this.pending = null;
        this.loaded.set({ key, access });
      }
      return access;
    });
    this.pending = { key, promise };
    return promise;
  }

  /**
   * Volve preguntar á API e substitúe o gardado (despois de aceptar unha invitación: o acceso
   * gardado era o de antes, sen rol).
   */
  reload(slug: string): Promise<StaffAccess | null> {
    this.pending = null;
    this.loaded.set(null);
    return this.load(slug);
  }

  private async fetch(slug: string): Promise<StaffAccess | null> {
    try {
      const response = await this.api.invoke(getMyStaffAccess, { slug });
      return {
        role: response.role ?? null,
        previousSignInAt: response.previousSignInAt ?? null,
        workAccount: response.workAccount === true,
      };
    } catch {
      // Sen correo verificado, sen rede ou co concello desaparecido: sen xestión (a API decide igual).
      return null;
    }
  }

  /** Concello e persoa: outra sesión ou outro concello invalidan o que había. */
  private key(slug: string | null): string | null {
    const subject = this.session.subject();
    return slug !== null && this.session.authenticated() && subject ? `${slug}\n${subject}` : null;
  }
}
