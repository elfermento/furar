// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive, input } from '@angular/core';
import type { ColumnWidths } from './column-widths';

/**
 * Táboa con columnas que se poden ensanchar (`table[furarColumnResize]="widths"`): deseño fixo,
 * os anchos van nun `<colgroup>` (`<col [style.width.px]="widths.width(key)">`) e cada cabeceira
 * leva o seu tirador `furar-column-resize`. A táboa mide polo menos a suma das columnas
 * (`--f-columns-min`); se non cabe, o seu contedor despraza en horizontal.
 */
@Directive({
  selector: 'table[furarColumnResize]',
  host: {
    class: 'f-column-resize-table',
    '[style.--f-columns-min.px]': 'widths().total()',
  },
})
export class ColumnResizeDirective {
  readonly widths = input.required<ColumnWidths>({ alias: 'furarColumnResize' });
}
