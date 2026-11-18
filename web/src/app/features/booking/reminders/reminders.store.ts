// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { getMyReminders } from '../../../api/fn/bookings/get-my-reminders';
import { optOutOfReminders } from '../../../api/fn/bookings/opt-out-of-reminders';
import { setMyReminders } from '../../../api/fn/bookings/set-my-reminders';

/**
 * Os recordatorios por correo dun concello (ADR-0035): o interruptor da persoa con sesión
 * (`/me/reminders`) e a baixa desde a ligazón do correo, sen sesión (`/public/reminder-opt-out`).
 */
@Injectable({ providedIn: 'root' })
export class RemindersStore {
  private readonly api = inject(Api);

  /** Se o correo da sesión recibe recordatorios neste concello. */
  async enabled(slug: string): Promise<boolean> {
    return (await this.api.invoke(getMyReminders, { slug })).enabled;
  }

  async setEnabled(slug: string, enabled: boolean): Promise<void> {
    await this.api.invoke(setMyReminders, { slug, body: { enabled } });
  }

  /** Coa proba asinada da ligazón; a API rexeita a manipulada ou caducada. */
  async optOut(slug: string, token: string): Promise<void> {
    await this.api.invoke(optOutOfReminders, { slug, body: { token } });
  }
}
