// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let nextId = 0;

/**
 * Fila de filtros (docs/deseno.md §8): o único patrón das pantallas con filtros enriba dunha lista,
 * un panel ou unha táboa. Un `role="group"` co seu nome e, dentro, campos `furar-filter-field`
 * (rótulo pequeno enriba e o control: `furar-select`, `furar-field-popover` con `look="filter"`
 * ou `[furarSegmented]`), chips `[furarFilterChip]` e botóns, todos de 44 px e en cápsula. Baixa de
 * liña se non cabe; por baixo de 640 px cada campo colle o ancho enteiro. `.f-filter-bar__end`
 * leva un control á dereita (p. ex. «Exportar»). Estilos en `styles/reservas.css`: a ruta leva
 * `bookingStylesResolver`.
 */
@Component({
  selector: 'furar-filter-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-filter-bar', role: 'group', '[attr.aria-label]': 'label()' },
  template: '<ng-content />',
})
export class FilterBarComponent {
  /** Nome do grupo («Filtros do rexistro»). */
  readonly label = input.required<string>();
}

/**
 * Un campo da fila de filtros: o rótulo visible enriba do control. Con `controlId` (o id do botón dun
 * `furar-select` ou dun `furar-field-popover`) é un `<label>`, e picar nel leva ao control; sen
 * `controlId`, un texto con `labelId` para o `aria-labelledby` dun grupo (`[furarSegmented]`). O nome
 * accesible do control empeza sempre polo texto do rótulo (2.5.3).
 */
@Component({
  selector: 'furar-filter-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-filter-field' },
  template: `
    @if (controlId(); as control) {
      <label class="f-filter-field__label" [id]="labelId" [for]="control">{{ label() }}</label>
    } @else {
      <span class="f-filter-field__label" [id]="labelId">{{ label() }}</span>
    }
    <ng-content />
  `,
})
export class FilterFieldComponent {
  readonly label = input.required<string>();
  /** Id do control que nomea o rótulo. */
  readonly controlId = input<string | null>(null);
  /** Id do rótulo, para `aria-labelledby`. */
  readonly labelId = `furar-filter-field-${nextId++}`;
}
