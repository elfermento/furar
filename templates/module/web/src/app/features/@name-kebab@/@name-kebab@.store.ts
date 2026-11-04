// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { listCustomFields } from '../../api/fn/custom-fields/list-custom-fields'; // @cf
import { create@Name@ } from '../../api/fn/@names@/create-@name-kebab@';
import { get@Name@ } from '../../api/fn/@names@/get-@name-kebab@';
import { list@Names@ } from '../../api/fn/@names@/list-@names@';
import { update@Name@ } from '../../api/fn/@names@/update-@name-kebab@';
import type { Create@Name@Request } from '../../api/models/create-@name-kebab@-request';
import type { @Name@Response } from '../../api/models/@name-kebab@-response';
import type { Update@Name@Request } from '../../api/models/update-@name-kebab@-request';
import type { CustomFieldDefinition } from '../../shared/custom-fields/custom-field.types'; // @cf
import { forTarget } from '../../shared/custom-fields/custom-field.types'; // @cf

export type { @Name@Response };
export type { CustomFieldDefinition }; // @cf

/** Única porta da feature ao cliente xerado (furar/zones). */
@Injectable({ providedIn: 'root' })
export class @Name@Store {
  private readonly api = inject(Api);

  async list(slug: string): Promise<@Name@Response[]> {
    const page = await this.api.invoke(list@Names@, { slug });
    return page.items;
  }

  get(slug: string, id: string): Promise<@Name@Response> {
    return this.api.invoke(get@Name@, { slug, id });
  }

  create(slug: string, body: Create@Name@Request): Promise<@Name@Response> {
    return this.api.invoke(create@Name@, { slug, body });
  }

  /** Bloqueo optimista: If-Match coa versión lida (o mesmo valor que o ETag, ADR-0009). */
  update(slug: string, current: @Name@Response, body: Update@Name@Request): Promise<@Name@Response> {
    return this.api.invoke(update@Name@, { slug, id: current.id, 'If-Match': `"${current.version}"`, body });
  }
  // @cf{

  /**
   * Campos personalizados de @TARGET@ que pode ver quen pregunta (ADR-0011), co obrigatorio e a
   * visibilidade do seu uso en @TARGET@.
   */
  async definitions(slug: string): Promise<CustomFieldDefinition[]> {
    const list = await this.api.invoke(listCustomFields, { slug, target: '@TARGET@' });
    return list.map((definition) => forTarget(definition, '@TARGET@'));
  }
  // @cf}
}
