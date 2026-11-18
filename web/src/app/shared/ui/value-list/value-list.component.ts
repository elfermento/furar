// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
} from '@angular/core';

/** Unha fila: rótulo e valor xa traducidos («Día» · «martes, 27 de outubro»). */
export interface ValueListItem {
  /** Clave estable da fila (vai en `data-value`, para as probas e os estilos). */
  readonly id: string;
  readonly label: string;
  readonly value: string;
}

/**
 * Lista de rótulos e valores (`dl`): o rótulo en gris á esquerda e o valor en negra á dereita,
 * unha fila por dato. Dúas variantes:
 *
 * - `lined` (por defecto): con liñas enriba e embaixo («Reserva confirmada», detalle dunha reserva).
 * - `plain`: sen liñas nin recheo, dentro doutra peza (os campos propios da ficha
 *   pública). Se algunha fila non cabe nunha liña, todas pasan a rótulo enriba e valor debaixo, aliñado á
 *   esquerda (`data-stacked`); nunca mesturadas. Decídeo o ancho real (ResizeObserver).
 *
 * Estilos en `styles/components/value-list.css` (folla `reservas.css`: a ruta leva
 * `bookingStylesResolver`).
 */
@Component({
  selector: 'furar-value-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-value-list', '[attr.data-variant]': 'variant()' },
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
  readonly variant = input<'lined' | 'plain'>('lined');

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    const view = inject(DOCUMENT).defaultView;
    let observer: ResizeObserver | null = null;
    afterRenderEffect(() => {
      this.items();
      if (this.variant() !== 'plain') {
        this.host.removeAttribute('data-stacked');
        return;
      }
      this.measure();
      if (!observer && view && 'ResizeObserver' in view) {
        observer = new view.ResizeObserver(() => this.measure());
        observer.observe(this.host);
      }
    });
    inject(DestroyRef).onDestroy(() => observer?.disconnect());
  }

  /**
   * Proba primeiro todas nunha liña: se algún valor baixa (ou o rótulo parte en dúas liñas), todas
   * van apiladas.
   */
  private measure(): void {
    this.host.removeAttribute('data-stacked');
    const rows = [...this.host.querySelectorAll<HTMLElement>('.f-value-list__row')];
    const wraps = rows.some((row) => {
      const dt = row.querySelector('dt');
      const dd = row.querySelector('dd');
      if (!dt || !dd) return false;
      const a = dt.getBoundingClientRect();
      const b = dd.getBoundingClientRect();
      return b.top >= a.bottom - 1 || stacked(dt) || stacked(dd);
    });
    if (wraps) this.host.setAttribute('data-stacked', '');
  }
}

/** Se o texto ocupa máis dunha liña. */
function stacked(el: HTMLElement): boolean {
  const line = parseFloat(getComputedStyle(el).lineHeight);
  return Number.isFinite(line) && line > 0 && el.getBoundingClientRect().height > line * 1.5;
}
