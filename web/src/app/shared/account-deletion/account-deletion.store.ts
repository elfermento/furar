// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import type { AccountDeletionBlockerResponse } from '../../api/models/account-deletion-blocker-response';
import type { AccountDeletionResponse } from '../../api/models/account-deletion-response';

export type { AccountDeletionBlockerResponse, AccountDeletionResponse };

/**
 * Se se pode eliminar a conta da sesión (`GET /api/v1/me/account-deletion`, ADR-0043). Pregúntase
 * cada vez, xusto antes de ir a Keycloak: sen caché. A función da API cárgase á parte, ao usala
 * (orzamento, D27).
 */
@Injectable({ providedIn: 'root' })
export class AccountDeletionStore {
  private readonly api = inject(Api);

  async load(): Promise<AccountDeletionResponse> {
    const { getMyAccountDeletion } = await import('../../api/fn/people/get-my-account-deletion');
    return this.api.invoke(getMyAccountDeletion);
  }
}
