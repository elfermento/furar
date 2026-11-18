// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EnvironmentInjector,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  model,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { IsoDate } from './local-date';
import { addDays, addMonths, clamp, formatMonth } from './local-date';

/** O que se ve: os días dun mes, os anos ou os meses dun ano. */
export type CalendarView = 'days' | 'years' | 'months';

/** Anos e meses van en tres columnas: as frechas arriba e abaixo saltan unha fila. */
const COLUMNS = 3;

/** Paso de cada tecla nas listas de anos e meses; `null` = non é da lista. */
function stepFor(key: string): number | null {
  switch (key) {
    case 'ArrowLeft':
      return -1;
    case 'ArrowRight':
      return 1;
    case 'ArrowUp':
      return -COLUMNS;
    case 'ArrowDown':
      return COLUMNS;
    case 'PageUp':
      return -COLUMNS * 4;
    case 'PageDown':
      return COLUMNS * 4;
    default:
      return null;
  }
}

/**
 * A escolla do ano e do mes de `furar-calendar` con `yearSelect`: a lista de anos (de `min` a `max`;
 * sen eles, 120 anos atrás e 10 adiante) e, ao escoller un, a dos meses dese ano; ao escoller o mes,
 * volve aos días (`view`). As dúas son `listbox` de tres columnas cunha parada de tabulador:
 * frechas, Re Páx/Av Páx (catro filas), Inicio e Fin, Intro ou Espazo escollen e, nos anos, teclear
 * as cifras vai a ese ano. Move o día enfocado do calendario (`focused`) ao mesmo día do ano e mes.
 */
@Component({
  selector: 'furar-calendar-year-month',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (view() === 'years') {
      <div
        class="f-calendar__choices f-calendar__choices--years"
        role="listbox"
        data-calendar-years
        [attr.aria-label]="'ui.calendar.chooseYear' | transloco"
      >
        @for (year of years(); track year) {
          <div
            class="f-calendar__choice"
            role="option"
            [attr.data-year]="year"
            [attr.tabindex]="year === focusedYear() ? 0 : -1"
            [attr.aria-selected]="year === selectedYear()"
            (click)="chooseYear(year)"
            (keydown)="onYearKeydown($event, year)"
          >
            {{ year }}
          </div>
        }
      </div>
    } @else {
      <div
        class="f-calendar__choices"
        role="listbox"
        data-calendar-months
        [attr.aria-label]="'ui.calendar.chooseMonth' | transloco: { year: focusedYear() }"
      >
        @for (month of months(); track month.index) {
          <div
            class="f-calendar__choice"
            role="option"
            [attr.data-month]="month.index"
            [attr.tabindex]="month.index === focusedMonth() ? 0 : -1"
            [attr.aria-selected]="month.selected"
            [attr.aria-disabled]="month.disabled || null"
            (click)="chooseMonth(month.index)"
            (keydown)="onMonthKeydown($event, month.index)"
          >
            {{ month.name }}
          </div>
        }
      </div>
    }
  `,
})
export class CalendarYearMonthComponent {
  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly environment = inject(EnvironmentInjector);

  readonly view = model.required<CalendarView>();
  /** O día enfocado do calendario. */
  readonly focused = model.required<IsoDate>();
  readonly value = input<IsoDate | null>(null);
  readonly min = input<IsoDate | null>(null);
  readonly max = input<IsoDate | null>(null);
  readonly today = input.required<IsoDate>();

  protected readonly focusedYear = computed(() => Number(this.focused().slice(0, 4)));
  protected readonly focusedMonth = computed(() => Number(this.focused().slice(5, 7)));
  protected readonly selectedYear = computed(() => {
    const value = this.value();
    return value ? Number(value.slice(0, 4)) : null;
  });
  /** Os anos que se poden escoller, do máis antigo ao máis novo. */
  protected readonly years = computed(() => {
    const today = Number(this.today().slice(0, 4));
    const min = this.min();
    const max = this.max();
    const from = min ? Number(min.slice(0, 4)) : today - 120;
    const to = max ? Number(max.slice(0, 4)) : today + 10;
    return Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i);
  });
  /** Os doce meses do ano enfocado, co nome no idioma e se caen fóra de `min`/`max`. */
  protected readonly months = computed(() => {
    const lang = this.language.lang();
    const year = this.focusedYear();
    const value = this.value();
    const min = this.min();
    const max = this.max();
    return Array.from({ length: 12 }, (_, i) => {
      const first = `${year}-${String(i + 1).padStart(2, '0')}-01`;
      const last = addDays(addMonths(first, 1), -1);
      return {
        index: i + 1,
        name: formatMonth(first, lang).replace(/\s.*$/, ''),
        selected: !!value && value.slice(0, 7) === first.slice(0, 7),
        disabled: (!!min && last < min) || (!!max && first > max),
      };
    });
  });
  private typed = '';
  private typedAt = 0;

  /** Leva o foco á opción enfocada (o panel chámao tras pintar). */
  focusActive(): void {
    const selector =
      this.view() === 'years' ? `[data-year="${this.focusedYear()}"]` : `[data-month="${this.focusedMonth()}"]`;
    const target = this.host.nativeElement.querySelector<HTMLElement>(selector);
    target?.focus();
    if (this.view() === 'years') target?.scrollIntoView?.({ block: 'center' });
  }

  protected chooseYear(year: number): void {
    this.setYearMonth(year, this.focusedMonth());
    this.view.set('months');
    this.refocus();
  }

  protected chooseMonth(month: number): void {
    if (this.months()[month - 1]?.disabled) return;
    this.setYearMonth(this.focusedYear(), month);
    // Este compoñente desaparece cos días: o foco vai ao día enfocado do calendario que o contén.
    const calendar = this.host.nativeElement.closest('.f-calendar');
    this.view.set('days');
    afterNextRender(() => calendar?.querySelector<HTMLElement>('[data-date][tabindex="0"]')?.focus(), {
      injector: this.environment,
    });
  }

  protected onYearKeydown(event: KeyboardEvent, year: number): void {
    if (/^\d$/.test(event.key)) {
      const now = Date.now();
      this.typed = (now - this.typedAt < 1500 ? this.typed : '') + event.key;
      this.typedAt = now;
      const found = this.years().find((y) => String(y).startsWith(this.typed));
      if (found !== undefined) this.moveYearFocus(found);
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.chooseYear(year);
      return;
    }
    const years = this.years();
    const index = years.indexOf(year);
    const target = this.listTarget(event, index, years.length);
    if (target === null) return;
    event.preventDefault();
    this.moveYearFocus(years[target]);
  }

  protected onMonthKeydown(event: KeyboardEvent, month: number): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.chooseMonth(month);
      return;
    }
    const target = this.listTarget(event, month - 1, 12);
    if (target === null) return;
    event.preventDefault();
    this.setYearMonth(this.focusedYear(), target + 1);
    this.refocus();
  }

  /** O índice ao que vai o foco nunha lista de tres columnas (sen dar a volta). */
  private listTarget(event: KeyboardEvent, index: number, length: number): number | null {
    if (event.key === 'Home') return 0;
    if (event.key === 'End') return length - 1;
    const step = stepFor(event.key);
    if (step === null) return null;
    return Math.min(length - 1, Math.max(0, index + step));
  }

  private moveYearFocus(year: number): void {
    this.setYearMonth(year, this.focusedMonth());
    this.refocus();
  }

  /** O día enfocado pasa a ese ano e mes (o mesmo día, ou o último se non existe), dentro dos límites. */
  private setYearMonth(year: number, month: number): void {
    const current = this.focused();
    const first = `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-01`;
    const end = addDays(addMonths(first, 1), -1);
    const wanted = addDays(first, Number(current.slice(8)) - 1);
    this.focused.set(clamp(wanted > end ? end : wanted, this.min(), this.max()));
  }

  private refocus(): void {
    afterNextRender(() => this.focusActive(), { injector: this.injector });
  }
}
