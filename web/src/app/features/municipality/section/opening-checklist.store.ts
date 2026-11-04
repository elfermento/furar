// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { OpeningStatus } from '../../../core/municipality/opening';

/**
 * Datos de «Para abrir as reservas» que non trae o concello: se hai polo menos unha instalación
 * activa que se vexa no público. A mesma lectura ca a entrada da xestión despois de entrar
 * (`OpeningStatus`); á parte do `MunicipalityStore` para ir co `@defer` da card.
 */
@Injectable({ providedIn: 'root' })
export class OpeningChecklistStore {
  private readonly opening = inject(OpeningStatus);

  hasPublishedFacility(slug: string): Promise<boolean> {
    return this.opening.hasPublishedFacility(slug);
  }
}
