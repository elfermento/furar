// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  afterRenderEffect,
  booleanAttribute,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

/** Unha columna: a cabeceira, xa traducida; `numeric` aliña as cifras á dereita. */
export interface DataTableColumn {
  readonly label: string;
  readonly numeric?: boolean;
}

/** Unha fila: unha cela por columna, xa formatada; a primeira é a cabeceira da fila. */
export type DataTableRow = readonly string[];

let nextId = 0;

/**
 * Táboa de datos (D3-07; docs/deseno.md §8): a alternativa en texto dun gráfico. `caption`, `th
 * scope` (columnas e primeira cela de cada fila) e cifras á dereita con números tabulares. Se o
 * caption repite un título á vista, `captionHidden` déixao só para os lectores de pantalla.
 *
 * Se non cabe (o mapa de calor no móbil), a táboa móvese dentro da súa rexión (sen scroll de
 * páxina a 320 px, 1.4.10): só entón a rexión leva `role="region"`, o caption como nome e
 * `tabindex="0"` para movela co teclado (2.1.1).
 */
@Component({
  selector: 'furar-data-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-data-table' },
  template: `
    <div
      #scroller
      class="f-data-table__scroll"
      [attr.role]="overflows() ? 'region' : null"
      [attr.tabindex]="overflows() ? 0 : null"
      [attr.aria-labelledby]="overflows() ? captionId : null"
    >
      <table class="f-data-table__table">
        <caption class="f-data-table__caption" [class.sr-only]="captionHidden()" [id]="captionId">
          {{
            caption()
          }}
        </caption>
        <thead>
          <tr>
            @for (column of columns(); track $index) {
              <th scope="col" [class.f-data-table__num]="column.numeric">{{ column.label }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track $index) {
            <tr>
              @for (cell of row; track $index) {
                @if ($first) {
                  <th scope="row" [class.f-data-table__num]="columns()[0]?.numeric">{{ cell }}</th>
                } @else {
                  <td [class.f-data-table__num]="columns()[$index]?.numeric">{{ cell }}</td>
                }
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class DataTableComponent {
  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');

  readonly caption = input.required<string>();
  /**
   * O caption só para lectores de pantalla, cando repite o título que xa se ve enriba (o da
   * tarxeta ou o do modal); segue sendo o nome da táboa e da súa rexión con scroll.
   */
  readonly captionHidden = input(false, { transform: booleanAttribute });
  readonly columns = input.required<readonly DataTableColumn[]>();
  readonly rows = input.required<readonly DataTableRow[]>();

  protected readonly captionId = `furar-data-table-${nextId++}`;
  protected readonly overflows = signal(false);

  constructor() {
    const view = inject(DOCUMENT).defaultView;
    const destroyRef = inject(DestroyRef);
    // Mídese de novo ao cambiar as filas e co tamaño (xirar o móbil, abrir a táboa agochada).
    afterRenderEffect(() => {
      this.rows();
      this.columns();
      this.measure();
    });
    afterNextRender(() => {
      if (!view?.ResizeObserver) return;
      const observer = new view.ResizeObserver(() => this.measure());
      observer.observe(this.scroller().nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  private measure(): void {
    const element = this.scroller().nativeElement;
    this.overflows.set(element.scrollWidth > element.clientWidth + 1);
  }
}
