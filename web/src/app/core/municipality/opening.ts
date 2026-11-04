// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { listFacilities } from '../../api/fn/facilities/list-facilities';
import type { LegalDetailsResponse } from '../../api/models/legal-details-response';

/** O máximo que acepta a API por páxina. */
const PAGE_SIZE = 100;

/**
 * Os datos que usan os textos legais por defecto (maqueta 49): o primeiro paso de «Para abrir as
 * reservas» e o aviso da pestana «Legal».
 */
export const OPENING_LEGAL = [
  'legalEntityName',
  'nif',
  'address',
  'generalEmail',
  'dpoEmail',
] as const satisfies readonly (keyof LegalDetailsResponse)[];
export type OpeningLegalKey = (typeof OPENING_LEGAL)[number];

/** Os que faltan dos que usan os textos legais por defecto. */
export function missingOpeningLegal(
  legal: Partial<Record<OpeningLegalKey, string | null | undefined>> | undefined,
): OpeningLegalKey[] {
  return OPENING_LEGAL.filter((key) => !(legal?.[key] ?? '').trim());
}

/**
 * «Para abrir as reservas» (maqueta 48): os datos que usan os textos legais, gardados, e polo menos
 * unha instalación activa que se vexa no público (sen nada que lle falte para publicarse). A card de
 * «Xeral» e a entrada da xestión despois de entrar (maqueta 56) len o mesmo. Só para a interface.
 */
@Injectable({ providedIn: 'root' })
export class OpeningStatus {
  private readonly api = inject(Api);

  /** Co listado da xestión, páxina a páxina ata atopala. */
  async hasPublishedFacility(slug: string): Promise<boolean> {
    let seen = 0;
    for (let page = 0; ; page++) {
      const result = await this.api.invoke(listFacilities, { slug, page, size: PAGE_SIZE });
      if (result.items.some((f) => f.status === 'ACTIVE' && f.missing.length === 0)) return true;
      seen += result.items.length;
      if (result.items.length < PAGE_SIZE || seen >= result.totalItems) return false;
    }
  }

  /** Se lle falta algún paso; cos datos legais incompletos non pregunta polas instalacións. */
  async pending(municipality: {
    readonly slug: string;
    readonly legal?: Partial<Record<OpeningLegalKey, string | null | undefined>>;
  }): Promise<boolean> {
    if (missingOpeningLegal(municipality.legal).length > 0) return true;
    return !(await this.hasPublishedFacility(municipality.slug));
  }
}
