// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import type { FormArray } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { DayRowClose } from './day-row.component';
import { DayRowComponent } from './day-row.component';
import type { DayHours } from './schedule.store';
import { DAYS, HOLIDAYS_ROW } from './schedule.store';
import type { DayForm } from './week-form';
import { intervalsText, weekdayName } from './week-format';

let nextId = 0;

/**
 * A semana dunha tempada (D1-23a; especificación, bloque 2, «Semana»): unha fila por día (luns a
 * domingo e festivos). Fóra da edición, en texto resolto («Luns 9:00 – 14:00 · 16:00 – 23:00»,
 * «Pechado»). No modo edición da ficha, cada fila ábrese ao pulsala (`day-row`: interruptor,
 * tramos, «+ Tramo»); poden estar abertas varias e unha fila con erros non se pecha.
 */
@Component({
  selector: 'furar-facility-week-rows',
  imports: [DayRowComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @let t = 'facility.schedule.';
    <ul
      class="divide-y divide-divider overflow-hidden rounded-card border border-divider bg-surface shadow-raised"
      [attr.aria-label]="t + 'week.label' | transloco: { season: season() }"
    >
      @if (editable() && week(); as week) {
        @for (day of week.controls; track $index; let i = $index) {
          <li>
            <furar-facility-day-row
              [day]="day"
              [index]="i"
              [name]="i === holidaysRow ? (t + 'day.holidays' | transloco) : names()[i]"
              [editing]="openRows().has(i)"
              [idPrefix]="id"
              (opened)="open(i)"
              (closeRequested)="close(i, $event)"
            />
          </li>
        }
      } @else {
        @for (row of hours(); track row.day; let i = $index) {
          <li
            class="gap-x-item gap-y-unit flex min-h-15 flex-wrap items-center px-item py-unit"
            [attr.data-day]="row.day"
          >
            <span class="w-36" [class.font-bold]="row.intervals.length" [class.text-ink-soft]="!row.intervals.length">
              {{ i === holidaysRow ? (t + 'day.holidays' | transloco) : names()[i] }}
            </span>
            <span class="tabular-nums" [class.text-ink-soft]="!row.intervals.length">
              {{ text(row) || (t + 'day.closed' | transloco) }}
            </span>
          </li>
        }
      }
    </ul>
  `,
})
export class WeekRowsComponent {
  /** Nome da tempada (para o nome da lista). */
  readonly season = input.required<string>();
  /** A semana gardada (fóra da edición). */
  readonly hours = input<readonly DayHours[]>([]);
  /** O formulario da semana (en edición). */
  readonly week = input<FormArray<DayForm> | null>(null);
  readonly editable = input(false, { transform: booleanAttribute });

  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly id = `furar-week-${nextId++}`;
  protected readonly holidaysRow = HOLIDAYS_ROW;
  protected readonly openRows = signal<ReadonlySet<number>>(new Set());
  protected readonly names = computed(() => {
    const lang = this.language.lang();
    return DAYS.map((_, i) => (i === HOLIDAYS_ROW ? '' : weekdayName(i, lang)));
  });

  protected text(row: DayHours): string {
    return intervalsText(row.intervals, this.language.lang());
  }

  /** Abre as filas con erro (tras un erro da API) e leva o foco ao primeiro. */
  openDays(days: readonly number[]): void {
    this.openRows.update((rows) => new Set([...rows, ...days]));
  }

  protected open(index: number): void {
    this.openRows.update((rows) => new Set(rows).add(index));
    this.focusIn(`[data-day-row="${index}"] button`);
  }

  /** Volve a repouso se o día non ten erros; se os ten, queda aberto co erro á vista (P20). */
  protected close(index: number, reason: DayRowClose): void {
    const day = this.week()?.at(index);
    if (!day) return;
    day.markAllAsTouched();
    if (day.invalid) {
      if (reason === 'escape') this.focusIn(`[data-day-row="${index}"] [aria-invalid="true"]`);
      return;
    }
    this.openRows.update((rows) => new Set([...rows].filter((row) => row !== index)));
    if (reason === 'escape') this.focusIn(`[data-day-edit="${index}"]`);
  }

  private focusIn(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
