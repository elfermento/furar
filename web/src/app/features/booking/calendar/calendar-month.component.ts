// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { MonthDay, PeekRequest } from './bookings-calendar-view';

/**
 * Mes do calendario (maqueta 27): sete columnas de luns a domingo e cada día un botón coa conta
 * («3 reservas» ou «Libre») que abre a vista rápida dese día. Hoxe, con aro. No móbil só se ve o
 * número de reservas (o nome segue sendo enteiro).
 */
@Component({
  selector: 'furar-calendar-month',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-card f-bcal__grid' },
  template: `
    <div class="f-bcal__month">
      @for (name of weekdays(); track $index) {
        <div class="f-bcal__dh" aria-hidden="true">{{ name }}</div>
      }
      @for (week of weeks(); track $index) {
        @for (day of week; track $index) {
          @if (day) {
            <button
              type="button"
              class="f-bcal__mday"
              data-peek-trigger
              aria-haspopup="dialog"
              [class.f-bcal__mday--today]="day.today"
              [attr.aria-current]="day.today ? 'date' : null"
              [attr.data-day]="day.date"
              (click)="peek(day.date, $event)"
            >
              <span class="sr-only">{{ day.longDate }}</span
              >{{ ' ' }} <b aria-hidden="true">{{ day.day }}</b
              >{{ ' ' }}
              <span class="f-bcal__mcount" [class.f-bcal__mcount--free]="!day.count">
                @if (day.count) {
                  <span class="f-bcal__mnum" aria-hidden="true">{{ day.count }}</span>
                }
                <span class="f-bcal__mtext">{{ day.text }}</span>
              </span>
            </button>
          } @else {
            <div aria-hidden="true"></div>
          }
        }
      }
    </div>
  `,
})
export class CalendarMonthComponent {
  /** Nomes curtos dos días, de luns a domingo. */
  readonly weekdays = input.required<readonly string[]>();
  readonly weeks = input.required<readonly (MonthDay | null)[][]>();
  readonly peeked = output<PeekRequest>();

  protected peek(date: IsoDate, event: Event): void {
    this.peeked.emit({ date, hour: null, trigger: event.currentTarget as HTMLElement });
  }
}
