// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { PipeTransform } from '@angular/core';
import { Pipe, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { placeOf } from './place-name';

/**
 * `{{ nome | placeOf }}` → «de Narón», «da Coruña» no idioma activo (`placeOf`). Impuro: segue o
 * cambio de idioma sen que cambie o nome; o cálculo é unha comparación de prefixos.
 */
@Pipe({ name: 'placeOf', pure: false })
export class PlaceOfPipe implements PipeTransform {
  private readonly transloco = inject(TranslocoService);

  transform(name: string | null | undefined): string {
    return placeOf(name ?? '', this.transloco.getActiveLang());
  }
}
