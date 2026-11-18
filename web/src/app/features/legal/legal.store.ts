// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { getLegalTexts } from '../../api/fn/municipalities/get-legal-texts';
import { getPrivacyNotice } from '../../api/fn/privacy/get-privacy-notice';
import type { LegalTextsResponse } from '../../api/models/legal-texts-response';
import type { PrivacyNoticeResponse } from '../../api/models/privacy-notice-response';

export type { LegalTextsResponse, PrivacyNoticeResponse };

/** Acceso á API das páxinas legais (único sitio da feature que usa o cliente xerado). */
@Injectable({ providedIn: 'root' })
export class LegalStore {
  private readonly api = inject(Api);

  /** Os prazos do rexistro de actividade do despregue, para «Privacidade» (ADR-0017, ADR-0020). */
  privacyNotice(): Promise<PrivacyNoticeResponse> {
    return this.api.invoke(getPrivacyNotice);
  }

  /** Textos propios das páxinas legais do concello, xa saneados no servidor. */
  legalTexts(slug: string): Promise<LegalTextsResponse> {
    return this.api.invoke(getLegalTexts, { slug });
  }
}
