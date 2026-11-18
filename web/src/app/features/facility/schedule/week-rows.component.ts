// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { DayHours } from './schedule.store';
import { DAYS, HOLIDAYS_ROW } from './schedule.store';
import { intervalsText, weekdayName } from './week-format';

/**
 * A semana dunha tempada (especificación, bloque 2, «Semana»): unha fila por día (luns a
 * domingo e festivos). Fóra da edición, en texto resolto («Luns 9:00 – 14:00 · 16:00 – 23:00»,
 * «Pechado»). En edición, a semana vai na tarxeta da tempada (`furar-facility-season-list`).
 */
@Component({
  selector: 'furar-facility-week-rows',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @let t = 'facility.schedule.';
    <ul
      class="divide-y divide-divider overflow-hidden rounded-card border border-divider bg-surface shadow-raised"
      [attr.aria-label]="t + 'week.label' | transloco: { season: season() }"
    >
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
    </ul>
  `,
})
export class WeekRowsComponent {
  /** Nome da tempada (para o nome da lista). */
  readonly season = input.required<string>();
  /** A semana gardada. */
  readonly hours = input<readonly DayHours[]>([]);

  private readonly language = inject(LanguageService);

  protected readonly holidaysRow = HOLIDAYS_ROW;
  protected readonly names = computed(() => {
    const lang = this.language.lang();
    return DAYS.map((_, i) => (i === HOLIDAYS_ROW ? '' : weekdayName(i, lang)));
  });

  protected text(row: DayHours): string {
    return intervalsText(row.intervals, this.language.lang());
  }
}
