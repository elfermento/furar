// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import type { CalendarColumn } from './bookings-calendar';
import { GUTTER_REM, HEAD_REM, HOUR_REM, columnBox, gridTemplate } from './bookings-calendar';
import type { GridBlock, GridCount, PeekRequest } from './bookings-calendar-view';

/**
 * Grella do calendario (maqueta 27): unha columna por instalación (día) ou por día (semana) e
 * unha fila por hora. As reservas van en bloques (a persoa e o uso; se se pisan, lado a lado) e,
 * na semana de todas as instalacións, cada hora con reservas é un botón coa conta que abre a vista
 * rápida. Os bloques van na orde de lectura (hora, columna), que é a do teclado, e cada un ten de
 * nome a reserva enteira («Ana Rey, Partido, Pista de pádel 1, 19:00–20:00, Pendente»); o fondo (horas e
 * celas, raiadas se a instalación está pechada esa hora) é só visual. Desprázase de lado se non cabe.
 */
@Component({
  selector: 'furar-calendar-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-card f-bcal__grid' },
  template: `
    <div
      class="f-bcal__table"
      [style.grid-template-columns]="template()"
      [style.min-width.rem]="minWidth()"
      [style.height.rem]="height()"
    >
      <div class="f-bcal__head" aria-hidden="true"></div>
      @for (column of columns(); track column.key) {
        <div class="f-bcal__head" [class.f-bcal__head--today]="column.today" [attr.data-column]="column.key">
          {{ column.title }}
          @if (column.subtitle) {
            <small>{{ column.subtitle }}</small>
          }
        </div>
      }
      @for (hour of hours(); track hour) {
        <div class="f-bcal__hour" aria-hidden="true">{{ hour }}:00</div>
        @for (column of columns(); track column.key) {
          <div
            class="f-bcal__cell"
            aria-hidden="true"
            [class.f-bcal__cell--closed]="closed().has(column.key + '|' + hour)"
          ></div>
        }
      }
      @for (block of blocks(); track block.booking.id) {
        @let box = boxOf(block);
        <button
          type="button"
          class="f-bcal__block"
          data-cal-block
          [attr.data-booking]="block.booking.id"
          [attr.aria-label]="block.name"
          [attr.aria-pressed]="block.booking.id === selectedId()"
          [style.top.rem]="block.top"
          [style.height.rem]="block.height"
          [style.left]="box.left"
          [style.width]="box.width"
          (click)="picked.emit(block.booking)"
        >
          <b>{{ block.person }}</b
          >{{ ' ' }}
          <span>{{ block.booking.useName }}</span>
        </button>
      }
      @for (count of counts(); track count.date + count.hour) {
        @let box = countBox(count);
        <button
          type="button"
          class="f-bcal__count"
          data-peek-trigger
          aria-haspopup="dialog"
          [attr.data-count]="count.date + 'T' + count.hour"
          [attr.aria-label]="count.name"
          [style.top.rem]="countTop(count.hour)"
          [style.left]="box.left"
          [style.width]="box.width"
          (click)="peek(count, $event)"
        >
          {{ count.text }}
        </button>
      }
    </div>
  `,
})
export class CalendarGridComponent {
  readonly columns = input.required<readonly CalendarColumn[]>();
  readonly hours = input.required<readonly number[]>();
  /** `columna|hora` das celas pechadas, que se raian. */
  readonly closed = input<ReadonlySet<string>>(new Set());
  readonly blocks = input<readonly GridBlock[]>([]);
  readonly counts = input<readonly GridCount[]>([]);
  readonly selectedId = input<string | null>(null);
  readonly picked = output<ManagedBookingResponse>();
  readonly peeked = output<PeekRequest>();

  protected readonly template = computed(() => gridTemplate(this.columns().length));
  protected readonly minWidth = computed(() => Math.max(40, GUTTER_REM + this.columns().length * 8));
  protected readonly height = computed(() => HEAD_REM + this.hours().length * HOUR_REM);

  protected boxOf(block: GridBlock) {
    return columnBox(block.column, this.columns().length, block.lane, block.lanes);
  }

  protected countBox(count: GridCount) {
    return columnBox(count.column, this.columns().length, 0, 1, 6);
  }

  protected peek(count: GridCount, event: Event): void {
    this.peeked.emit({ date: count.date, hour: count.hour, trigger: event.currentTarget as HTMLElement });
  }

  protected countTop(hour: number): number {
    return HEAD_REM + (hour - this.hours()[0]) * HOUR_REM + 0.375;
  }
}
