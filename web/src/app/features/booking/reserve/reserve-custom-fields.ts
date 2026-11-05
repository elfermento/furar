// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import type { ApiProblem } from '../../../core/errors/api-problem';
import type { CustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import {
  activeDefinitions,
  applyServerErrors,
  buildCustomFieldsForm,
  customFieldValuesOf,
} from '../../../shared/custom-fields/custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValues } from '../../../shared/custom-fields/custom-field.types';
import { BookingFieldsStore } from '../booking-fields.store';

/**
 * Os campos propios da reserva na páxina de reservar (ADR-0011; maqueta 57): os activos énchense no
 * mesmo paso, antes de confirmar, e van no `POST`; a API valídaos e os seus erros volven ao seu
 * campo. Sen sesión non hai ningún que encher: cárganse ao volver de Keycloak. Un por páxina.
 */
@Injectable()
export class ReserveCustomFields {
  private readonly store = inject(BookingFieldsStore);

  /** Os que ve quen reserva (tamén os desactivados, para ler a reserva confirmada). */
  readonly definitions = signal<CustomFieldDefinition[]>([]);
  /** Os que se enchen agora: os activos. */
  readonly active = computed(() => activeDefinitions(this.definitions()));
  readonly form = signal<CustomFieldsForm>(buildCustomFieldsForm([]));
  readonly showErrors = signal(false);

  async load(slug: string): Promise<void> {
    const list = await this.store.load(slug);
    this.definitions.set(list);
    this.form.set(buildCustomFieldsForm(list));
  }

  /** Enche o formulario cos valores gardados (cambiar unha reserva desde a xestión). */
  fill(values: CustomFieldValues | undefined): void {
    this.form.set(buildCustomFieldsForm(this.definitions(), values ?? {}));
    this.showErrors.set(false);
  }

  /** Se se pode enviar; se non, amosa os erros. */
  check(): boolean {
    if (this.form().valid) return true;
    this.form().markAllAsTouched();
    this.showErrors.set(true);
    return false;
  }

  /** Os valores para o `POST` (nada se non hai ningún). */
  body(): { customFields?: Record<string, unknown> } {
    const customFields = customFieldValuesOf(this.active(), this.form());
    return Object.keys(customFields).length ? { customFields } : {};
  }

  /** Os erros da API (`customFields.<código>`) no seu campo; `true` se os colocou todos. */
  apply(problem: ApiProblem): boolean {
    if (!problem.fieldErrors.length || applyServerErrors(this.form(), problem.fieldErrors).length) return false;
    this.showErrors.set(true);
    return true;
  }
}
