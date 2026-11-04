// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { listCustomFields } from '../../api/fn/custom-fields/list-custom-fields';
import { createMunicipality } from '../../api/fn/municipalities/create-municipality';
import { getLegalTexts } from '../../api/fn/municipalities/get-legal-texts';
import { getPaymentNotes } from '../../api/fn/municipalities/get-payment-notes';
import { getSportsContact } from '../../api/fn/municipalities/get-sports-contact';
import { updateLegalTexts } from '../../api/fn/municipalities/update-legal-texts';
import { updatePaymentNotes } from '../../api/fn/municipalities/update-payment-notes';
import { updateSportsContact } from '../../api/fn/municipalities/update-sports-contact';
import { updateMunicipality } from '../../api/fn/municipalities/update-municipality';
import { getPrivacyNotice } from '../../api/fn/privacy/get-privacy-notice';
import type { CreateMunicipalityRequest } from '../../api/models/create-municipality-request';
import type { LegalDetailsRequest } from '../../api/models/legal-details-request';
import type { LegalDetailsResponse } from '../../api/models/legal-details-response';
import type { LegalTextsRequest } from '../../api/models/legal-texts-request';
import type { LegalTextsResponse } from '../../api/models/legal-texts-response';
import type { MunicipalityResponse } from '../../api/models/municipality-response';
import type { MunicipalitySummaryResponse } from '../../api/models/municipality-summary-response';
import type { PaymentNotesRequest } from '../../api/models/payment-notes-request';
import type { PaymentNotesResponse } from '../../api/models/payment-notes-response';
import type { PrivacyNoticeResponse } from '../../api/models/privacy-notice-response';
import type { SportsContactRequest } from '../../api/models/sports-contact-request';
import type { SportsContactResponse } from '../../api/models/sports-contact-response';
import type { ThemeResponse } from '../../api/models/theme-response';
import type { UpdateMunicipalityRequest } from '../../api/models/update-municipality-request';
import { MunicipalityDirectory } from '../../core/municipality/municipality-directory';
import type { CustomFieldDefinition } from '../../shared/custom-fields/custom-field.types';
import { forTarget } from '../../shared/custom-fields/custom-field.types';

export type {
  CustomFieldDefinition,
  LegalDetailsRequest,
  LegalDetailsResponse,
  LegalTextsRequest,
  LegalTextsResponse,
  MunicipalityResponse,
  MunicipalitySummaryResponse,
  PaymentNotesRequest,
  PaymentNotesResponse,
  PrivacyNoticeResponse,
  SportsContactRequest,
  SportsContactResponse,
  ThemeResponse,
};

/** Resposta dun `PUT` que comparte a versión do concello: o corpo e a versión nova (`ETag`). */
export interface Versioned<T> {
  readonly body: T;
  readonly version: number;
}

/** `"5"` ou `W/"5"` → 5. */
function versionOf(etag: string | null, fallback: number): number {
  const match = /"(\d+)"/.exec(etag ?? '');
  return match ? Number(match[1]) : fallback;
}

/** Acceso á API do módulo municipality (único sitio da feature que usa o cliente xerado). */
@Injectable({ providedIn: 'root' })
export class MunicipalityStore {
  private readonly api = inject(Api);
  private readonly directory = inject(MunicipalityDirectory);

  /** Todos os concellos, sempre da API (a raíz). */
  list(): Promise<MunicipalitySummaryResponse[]> {
    return this.directory.fetch();
  }

  /**
   * Campos personalizados do propio concello (a API só devolve os que o visitante pode ver), co
   * obrigatorio e a visibilidade do seu uso no concello.
   */
  async definitions(slug: string): Promise<CustomFieldDefinition[]> {
    const list = await this.api.invoke(listCustomFields, { slug, target: 'MUNICIPALITY' });
    return list.map((definition) => forTarget(definition, 'MUNICIPALITY'));
  }

  /** Bloqueo optimista: If-Match coa versión lida (ADR-0009). */
  update(municipality: MunicipalityResponse, body: UpdateMunicipalityRequest): Promise<MunicipalityResponse> {
    return this.api.invoke(updateMunicipality, {
      slug: municipality.slug,
      'If-Match': `"${municipality.version}"`,
      body,
    });
  }

  async create(body: CreateMunicipalityRequest): Promise<MunicipalityResponse> {
    const created = await this.api.invoke(createMunicipality, { body });
    // «Concellos ›» no menú da plataforma e a súa entrada len a lista nova.
    this.directory.invalidate();
    return created;
  }

  /** Contacto do servizo de deportes (lectura pública). */
  sportsContact(slug: string): Promise<SportsContactResponse> {
    return this.api.invoke(getSportsContact, { slug });
  }

  /** Textos de pago e devolución: os do concello ou os de Furar (`…Default`). */
  paymentNotes(slug: string): Promise<PaymentNotesResponse> {
    return this.api.invoke(getPaymentNotes, { slug });
  }

  /** Mesma versión ca o concello: devolve a nova para encadear o seguinte `PUT`. */
  async updateSportsContact(
    slug: string,
    version: number,
    body: SportsContactRequest,
  ): Promise<Versioned<SportsContactResponse>> {
    const response = await this.api.invoke$Response(updateSportsContact, { slug, 'If-Match': `"${version}"`, body });
    return { body: response.body, version: versionOf(response.headers.get('ETag'), version + 1) };
  }

  async updatePaymentNotes(
    slug: string,
    version: number,
    body: PaymentNotesRequest,
  ): Promise<Versioned<PaymentNotesResponse>> {
    const response = await this.api.invoke$Response(updatePaymentNotes, { slug, 'If-Match': `"${version}"`, body });
    return { body: response.body, version: versionOf(response.headers.get('ETag'), version + 1) };
  }

  /** Textos propios das páxinas legais (xa saneados no servidor). */
  legalTexts(slug: string): Promise<LegalTextsResponse> {
    return this.api.invoke(getLegalTexts, { slug });
  }

  async updateLegalTexts(
    slug: string,
    version: number,
    body: LegalTextsRequest,
  ): Promise<Versioned<LegalTextsResponse>> {
    const response = await this.api.invoke$Response(updateLegalTexts, { slug, 'If-Match': `"${version}"`, body });
    return { body: response.body, version: versionOf(response.headers.get('ETag'), version + 1) };
  }

  /** Os prazos do rexistro de actividade do despregue, para a «Privacidade» de Furar. */
  privacyNotice(): Promise<PrivacyNoticeResponse> {
    return this.api.invoke(getPrivacyNotice);
  }
}
