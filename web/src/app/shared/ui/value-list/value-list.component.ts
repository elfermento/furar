// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Unha fila: rótulo e valor xa traducidos («Día» · «martes, 27 de outubro»). */
export interface ValueListItem {
  /** Clave estable da fila (vai en `data-value`, para as probas e os estilos). */
  readonly id: string;
  readonly label: string;
  readonly value: string;
}

/**
 * Lista de rótulos e valores (`dl`): o rótulo en gris á esquerda e o valor en negra á dereita,
 * unha fila por dato, con liñas enriba e embaixo. «Reserva confirmada» e o detalle dunha reserva
 * na xestión. Estilos en `styles/reservas.css`: a ruta leva `bookingStylesResolver`.
 */
@Component({
  selector: 'furar-value-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-value-list' },
  template: `
    <dl class="f-value-list__list">
      @for (item of items(); track item.id) {
        <div class="f-value-list__row" [attr.data-value]="item.id">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      }
    </dl>
  `,
})
export class ValueListComponent {
  readonly items = input.required<readonly ValueListItem[]>();
}
