// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { activateCustomField } from '../../api/fn/custom-fields/activate-custom-field';
import { createCustomField } from '../../api/fn/custom-fields/create-custom-field';
import { deactivateCustomField } from '../../api/fn/custom-fields/deactivate-custom-field';
import { deleteCustomField } from '../../api/fn/custom-fields/delete-custom-field';
import { getCustomField } from '../../api/fn/custom-fields/get-custom-field';
import { listCustomFields } from '../../api/fn/custom-fields/list-custom-fields';
import { updateCustomField } from '../../api/fn/custom-fields/update-custom-field';
import type { CustomFieldDefinitionRequest } from '../../api/models/custom-field-definition-request';
import type { CustomFieldDefinitionResponse } from '../../api/models/custom-field-definition-response';
import type { UpdateCustomFieldDefinitionRequest } from '../../api/models/update-custom-field-definition-request';

/** A definición co seu uso (un campo, un sitio) e se xa ten valores (`hasValues`). */
export type EditableDefinition = CustomFieldDefinitionResponse;

/** Máximo de campos activos usados nun mesmo sitio (concello, destino) (ADR-0011); compróbao a API. */
export const ACTIVE_LIMIT = 50;

const ifMatch = (definition: CustomFieldDefinitionResponse) => `"${definition.version}"`;

/** API de campos personalizados para o editor do xestor. */
@Injectable({ providedIn: 'root' })
export class CustomFieldsStore {
  private readonly api = inject(Api);

  /** Todos os campos do concello, de calquera sitio, activos e desactivados (só xestor), en orde alfabética da API. */
  list(slug: string): Promise<EditableDefinition[]> {
    return this.api.invoke(listCustomFields, { slug, includeInactive: true });
  }

  get(slug: string, id: string): Promise<EditableDefinition> {
    return this.api.invoke(getCustomField, { slug, id });
  }

  create(slug: string, body: CustomFieldDefinitionRequest): Promise<EditableDefinition> {
    return this.api.invoke(createCustomField, { slug, body });
  }

  update(
    slug: string,
    definition: CustomFieldDefinitionResponse,
    body: UpdateCustomFieldDefinitionRequest,
  ): Promise<EditableDefinition> {
    return this.api.invoke(updateCustomField, { slug, id: definition.id, 'If-Match': ifMatch(definition), body });
  }

  /** Desactivar (deixa de pedirse; os valores quedan) ou activar. */
  setActive(slug: string, definition: CustomFieldDefinitionResponse, active: boolean): Promise<EditableDefinition> {
    const params = { slug, id: definition.id, 'If-Match': ifMatch(definition) };
    return active ? this.api.invoke(activateCustomField, params) : this.api.invoke(deactivateCustomField, params);
  }

  /** Eliminar: só sen valores (409 `customfield.has-values` se os ten). */
  async delete(slug: string, definition: CustomFieldDefinitionResponse): Promise<void> {
    await this.api.invoke(deleteCustomField, { slug, id: definition.id, 'If-Match': ifMatch(definition) });
  }
}
