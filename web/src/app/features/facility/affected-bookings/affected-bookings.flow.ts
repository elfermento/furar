// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import type { AffectedBookingsDialogData, AffectedBookingsDialogResult } from './affected-bookings-dialog.component';
import { AffectedBookingsDialogComponent } from './affected-bookings-dialog.component';
import type { BookingDecision } from './affected-bookings.store';
import { affectedFrom } from './affected-bookings.store';

/** Como acabou: gardado (co que devolveu a API) ou o xestor volveu atrás sen gardar. */
export type AffectedBookingsOutcome<T> = { readonly done: true; readonly value: T } | { readonly done: false };

/**
 * Fluxo en dous pasos sobre o mesmo endpoint (plan do dominio 1, §1.2.6): `send()` sen decisións;
 * se a API responde 409 `facility.affected-bookings`, ábrese o modal coas `affected[]` e
 * «Confirmar» reenvía a mesma petición con `decisions[]`. Se as afectadas cambiaron entre os dous
 * pasos, o modal amosa a lista nova sen pechar. Calquera outro erro chega a quen chama (412, 422…).
 *
 * `send` é do store da sección (bloqueos, estado, usos), que segue sendo quen fai o HTTP.
 */
@Injectable({ providedIn: 'root' })
export class AffectedBookingsFlow {
  private readonly dialogs = inject(DialogService);

  async run<T>(
    send: (decisions?: BookingDecision[]) => Promise<T>,
    options: { readonly summary?: string } = {},
  ): Promise<AffectedBookingsOutcome<T>> {
    let affected;
    try {
      return { done: true, value: await send() };
    } catch (error) {
      affected = affectedFrom(error);
      if (!affected) throw error;
    }
    let saved: { value: T } | null = null;
    const data: AffectedBookingsDialogData = {
      affected,
      summary: options.summary,
      submit: async (decisions) => {
        try {
          saved = { value: await send(decisions) };
          return null;
        } catch (error) {
          const next = affectedFrom(error);
          if (next) return next;
          throw error;
        }
      },
    };
    const ref = this.dialogs.open<AffectedBookingsDialogResult, AffectedBookingsDialogData>(
      AffectedBookingsDialogComponent,
      { data },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (typeof result === 'object') throw result.failed;
    const done = saved as { value: T } | null;
    return result === 'done' && done ? { done: true, value: done.value } : { done: false };
  }
}
