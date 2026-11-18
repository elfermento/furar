// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive } from '@angular/core';

/**
 * Lista de filas en card con divisores (docs/deseno.md P1-7) sobre <ul> nativo: cada <li> leva
 * un <furar-row>. O nome da lista (aria-label) pono quen chama.
 */
@Directive({
  selector: '[furarRowList]',
  host: { class: 'f-row-list' },
})
export class RowListDirective {}
