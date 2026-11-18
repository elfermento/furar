// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { submitMunicipalityJoinRequest } from '../../../api/fn/deployment/submit-municipality-join-request';
import type { MunicipalityJoinRequest } from '../../../api/models/municipality-join-request';

/** O corpo da solicitude, tal como o pide a API. */
export type JoinRequestBody = MunicipalityJoinRequest;

/** «¿Es un concello?» (ADR-0042): a solicitude vai por correo a quen opera o servizo. */
@Injectable({ providedIn: 'root' })
export class JoinRequestStore {
  private readonly api = inject(Api);

  async submit(body: JoinRequestBody): Promise<void> {
    await this.api.invoke(submitMunicipalityJoinRequest, { body });
  }
}
