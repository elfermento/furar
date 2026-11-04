// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { formatShort } from '../../../../shared/ui/calendar/local-date';
import { CalloutComponent } from '../../../../shared/ui/callout/callout.component';
import type { DateSpan } from './exceptions.store';
import { ExceptionsStore } from './exceptions.store';

/**
 * Aviso de días sen tempada (D1-23b; especificación, bloque 2, «Avísase se quedan días sen
 * tempada»): os rangos de `gaps[]` da API, de hoxe a un ano vista. Neses días a instalación está
 * pechada salvo que unha excepción a abra. Aviso tranquilo (`furar-callout` warn, sen
 * `role="alert"`): está aí desde que se carga, non é a resposta a algo que se acaba de facer.
 */
@Component({
  selector: 'furar-facility-schedule-gaps',
  imports: [CalloutComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.schedule.gaps.';
    @if (store.gaps().length) {
      <furar-callout tone="warn" data-schedule-gaps [heading]="t + 'title' | transloco">
        <p>{{ t + 'text' | transloco }}</p>
        <ul class="mt-unit list-disc ps-item">
          @for (gap of store.gaps(); track gap.from) {
            <li class="tabular-nums">
              @if (gap.from === gap.to) {
                {{ t + 'day' | transloco: { date: short(gap.from) } }}
              } @else {
                {{ t + 'range' | transloco: { from: short(gap.from), to: short(gap.to) } }}
              }
            </li>
          }
        </ul>
      </furar-callout>
    }
  `,
})
export class ScheduleGapsComponent {
  protected readonly store = inject(ExceptionsStore);

  protected short(date: DateSpan['from']): string {
    return formatShort(date);
  }
}
