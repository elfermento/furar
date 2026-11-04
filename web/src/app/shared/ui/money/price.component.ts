// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * Prezo á dereita dunha fila ou tarxeta (maqueta 54): o importe en negriña («45,00 €», «Gratis»)
 * e, debaixo, a que se refire («por reserva de 60 min»). `muted`: un valor sen prezo («Sen
 * prezo»), en ton suave e sen negriña. O texto chega xa formatado (`formatCents` e i18n).
 */
@Component({
  selector: 'furar-price',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-price', '[attr.data-muted]': 'muted() ? "" : null' },
  template: `
    {{ amount() }}
    @if (per(); as per) {
      <small>{{ per }}</small>
    }
  `,
})
export class PriceComponent {
  /** O importe ou o tipo de prezo, xa formatado. */
  readonly amount = input.required<string>();
  /** A que se refire o importe; sen el, só o importe. */
  readonly per = input<string | null>(null);
  readonly muted = input(false, { transform: booleanAttribute });
}
