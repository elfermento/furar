// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { formatShort, weekdayIndex } from '../../../../shared/ui/calendar/local-date';
import { TagComponent } from '../../../../shared/ui/tag/tag.component';
import { intervalsText, weekdayName } from '../week-format';
import type { ScheduleException } from './exceptions.store';

/** «Xoves 24/12/2026»: día da semana por `Intl` e data co formato do idioma. */
export function exceptionDay(date: string, lang: string): string {
  return `${weekdayName(weekdayIndex(date), lang)} ${formatShort(date)}`;
}

/**
 * O que di unha excepción (D1-23b; especificación, bloque 2, «Excepcións»): o tramo ou «Pechado»
 * nunha etiqueta e o motivo. En `<span>`, para ir na fila ou dentro da tarxeta do modo edición.
 */
@Component({
  selector: 'furar-facility-exception-summary',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'gap-unit inline-flex flex-wrap items-center' },
  template: `
    <furar-tag [tone]="exception().open ? 'primary' : 'neutral'">
      {{ hours() || ('facility.schedule.exceptions.closed' | transloco) }}
    </furar-tag>
    @if (exception().reason) {
      <span class="text-ink-soft">{{ exception().reason }}</span>
    }
  `,
})
export class ExceptionSummaryComponent {
  readonly exception = input.required<ScheduleException>();

  private readonly language = inject(LanguageService);
  protected readonly hours = computed(() => {
    const { open, startMinute, endMinute } = this.exception();
    return open && startMinute !== null && endMinute !== null
      ? intervalsText([{ startMinute, endMinute }], this.language.lang())
      : '';
  });
}
