// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Api } from '../../api/api';
import { getMyPlatformAccess } from '../../api/fn/staff/get-my-platform-access';
import { SessionService } from './session.service';

interface Loaded {
  readonly subject: string;
  readonly platformAdmin: boolean;
}

/**
 * Se a persoa da sesión é administración da plataforma (`GET /api/v1/me/platform-access`,
 * ADR-0028). O rol vive na base de Furar, non no token: a primeira petición da conta de
 * `FURAR_ADMIN_EMAIL` é a que llo dá. Garda o dunha persoa (non un fallo); outra sesión volve
 * preguntar. Só para a interface (a API decide sempre).
 */
@Injectable({ providedIn: 'root' })
export class PlatformAccessService {
  private readonly api = inject(Api);
  private readonly session = inject(SessionService);
  private readonly loaded = signal<Loaded | null>(null);
  private pending: { readonly subject: string; readonly promise: Promise<boolean> } | null = null;

  /** Falso sen sesión, mentres carga ou se falla. */
  readonly platformAdmin = computed<boolean>(() => {
    const subject = this.subject();
    const loaded = this.loaded();
    return subject !== null && loaded?.subject === subject && loaded.platformAdmin;
  });

  constructor() {
    // A barra e o menú precisan sabelo en calquera páxina con sesión.
    effect(() => {
      if (this.subject() !== null) untracked(() => void this.load());
    });
  }

  load(): Promise<boolean> {
    const subject = this.subject();
    if (subject === null) return Promise.resolve(false);
    const loaded = this.loaded();
    if (loaded?.subject === subject) return Promise.resolve(loaded.platformAdmin);
    if (this.pending?.subject === subject) return this.pending.promise;
    const promise = this.fetch().then((platformAdmin) => {
      if (this.pending?.subject === subject) {
        this.pending = null;
        // Un fallo non se garda: a seguinte garda ou a entrada volven preguntar.
        if (platformAdmin !== null) this.loaded.set({ subject, platformAdmin });
      }
      return platformAdmin ?? false;
    });
    this.pending = { subject, promise };
    return promise;
  }

  /** `null` se falla (sen correo verificado ou sen rede): mentres, sen administración. */
  private async fetch(): Promise<boolean | null> {
    try {
      const response = await this.api.invoke(getMyPlatformAccess, {});
      return response.platformAdmin === true;
    } catch {
      return null;
    }
  }

  private subject(): string | null {
    const subject = this.session.subject();
    return this.session.authenticated() && subject ? subject : null;
  }
}
