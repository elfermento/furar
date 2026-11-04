// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { listCustomFields } from '../../api/fn/custom-fields/list-custom-fields';
import type { CustomFieldDefinition } from '../../shared/custom-fields/custom-field.types';
import { forTarget } from '../../shared/custom-fields/custom-field.types';
import { BOOKING_TARGET } from './booking-fields';

/**
 * Os campos propios da reserva que ve quen pregunta (ADR-0011, revisión do 2026-11-04): quen vai
 * reservar ou reservou, os de «a persoa que reserva e o persoal»; o persoal, todos. Tamén os
 * desactivados, para ler os valores gardados; os formularios só pintan os activos.
 */
@Injectable({ providedIn: 'root' })
export class BookingFieldsStore {
  private readonly api = inject(Api);

  /** Se falla, ningún: a reserva segue sen eles. */
  async load(slug: string): Promise<CustomFieldDefinition[]> {
    try {
      const list = await this.api.invoke(listCustomFields, { slug, target: BOOKING_TARGET });
      return list.map((definition) => forTarget(definition, BOOKING_TARGET));
    } catch {
      return [];
    }
  }
}
