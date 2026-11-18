// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Pipe, inject } from '@angular/core';
import type { PipeTransform } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import type { SelectOption } from './select.component';

/**
 * Opcións dun furar-select desde unha lista de valores e o prefixo das súas claves:
 * `types | furarOptions: 'facility.type.' : lang()` → `{ value: 'POOL', label: 'Piscina' }`.
 * O idioma vai como argumento para que o pipe (puro) se recalcule ao cambialo. `disabled` son os
 * valores que se ven pero non se poden escoller (unha lista estable, p. ex. dun `computed`).
 */
@Pipe({ name: 'furarOptions' })
export class OptionsPipe implements PipeTransform {
  private readonly transloco = inject(TranslocoService);

  transform<T extends string>(
    values: readonly T[],
    prefix: string,
    lang?: string,
    disabled: readonly T[] = [],
  ): SelectOption<T>[] {
    return values.map((value) => ({
      value,
      label: this.transloco.translate(prefix + value, {}, lang),
      ...(disabled.includes(value) ? { disabled: true } : {}),
    }));
  }
}
