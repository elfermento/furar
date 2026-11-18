// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../../api/api';
import { countPendingEventRequests } from '../../api/fn/events/count-pending-event-requests';
import { StaffAccessService } from '../auth/staff-access.service';
import type { ManageCounter } from '../layout/manage-links';
import { CurrentMunicipality } from '../municipality/current-municipality';

/**
 * As solicitudes pendentes dos eventos «Con aprobación» do concello actual (ADR-0038): o número da
 * pestana «Eventos», sumado no apartado «Instalacións». Só para quen ve as persoas apuntadas
 * (Xestión e Recepción); pídese ao entrar no concello, ao navegar pola xestión e cando a xestión
 * acepta ou rexeita (`refresh`).
 */
@Injectable({ providedIn: 'root' })
export class PendingEventRequests implements ManageCounter {
  private readonly api = inject(Api);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly value = signal<number | null>(null);
  private request = 0;

  readonly count = this.value.asReadonly();

  constructor() {
    effect(() => {
      const slug = this.municipality.slug();
      const role = this.staff.role();
      untracked(() => void this.load(slug, role !== null));
    });
    const subscription = inject(Router)
      .events.pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => {
        if (event.urlAfterRedirects.includes('/xestion/')) this.refresh();
      });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }

  /** Volve contar (despois de aceptar ou rexeitar). */
  refresh(): void {
    void this.load(this.municipality.slug(), this.staff.role() !== null);
  }

  private async load(slug: string | null, staff: boolean): Promise<void> {
    const request = ++this.request;
    if (slug === null || !staff) {
      this.value.set(null);
      return;
    }
    try {
      const { count } = await this.api.invoke(countPendingEventRequests, { slug });
      if (request === this.request) this.value.set(count);
    } catch {
      if (request === this.request) this.value.set(null);
    }
  }
}
