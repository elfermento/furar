// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../../api/api';
import { countPendingSeriesRequests } from '../../api/fn/bookings/count-pending-series-requests';
import { StaffAccessService } from '../auth/staff-access.service';
import type { ManageCounter } from '../layout/manage-links';
import { CurrentMunicipality } from '../municipality/current-municipality';

/**
 * As solicitudes de tempada pendentes do concello actual (ADR-0039): o número da pestana
 * «Entidades», sumado no apartado «Reservas». Só para a xestión (permiso das entidades);
 * pídese ao entrar no concello, ao navegar pola xestión e ao aprobar ou rexeitar (`refresh`).
 */
@Injectable({ providedIn: 'root' })
export class PendingSeriesRequests implements ManageCounter {
  private readonly api = inject(Api);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly value = signal<number | null>(null);
  private request = 0;

  readonly count = this.value.asReadonly();

  constructor() {
    effect(() => {
      const slug = this.municipality.slug();
      const manager = this.staff.canManage();
      untracked(() => void this.load(slug, manager));
    });
    const subscription = inject(Router)
      .events.pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => {
        if (event.urlAfterRedirects.includes('/xestion/')) this.refresh();
      });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }

  /** Volve contar (despois de aprobar, rexeitar ou eliminar unha entidade). */
  refresh(): void {
    void this.load(this.municipality.slug(), this.staff.canManage());
  }

  private async load(slug: string | null, manager: boolean): Promise<void> {
    const request = ++this.request;
    if (slug === null || !manager) {
      this.value.set(null);
      return;
    }
    try {
      const { count } = await this.api.invoke(countPendingSeriesRequests, { slug });
      if (request === this.request) this.value.set(count);
    } catch {
      if (request === this.request) this.value.set(null);
    }
  }
}
