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
  linkedSignal,
  model,
  output,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../icon/icon.component';
import type { IsoDate } from './local-date';
import {
  addDays,
  addMonths,
  clamp,
  formatLong,
  formatMonth,
  localToday,
  monthGrid,
  startOfMonth,
  startOfWeek,
  weekdayNames,
} from './local-date';

let nextId = 0;

/** Desprazamento de cada tecla da grella (APG Date Picker Dialog); `null` = non é da grella. */
function moveFor(event: KeyboardEvent, from: IsoDate): IsoDate | null {
  switch (event.key) {
    case 'ArrowLeft':
      return addDays(from, -1);
    case 'ArrowRight':
      return addDays(from, 1);
    case 'ArrowUp':
      return addDays(from, -7);
    case 'ArrowDown':
      return addDays(from, 7);
    case 'Home':
      return startOfWeek(from);
    case 'End':
      return addDays(startOfWeek(from), 6);
    case 'PageUp':
      return addMonths(from, event.shiftKey ? -12 : -1);
    case 'PageDown':
      return addMonths(from, event.shiftKey ? 12 : 1);
    default:
      return null;
  }
}

/**
 * Calendario dun mes (plan do dominio 1, D1-04; docs/deseno.md §8): grella APG con luns primeiro e
 * sempre seis filas; hoxe con aro (`aria-current="date"`), o escollido con recheo `primary`
 * (`aria-selected`) e os días fóra de `min`/`max` ou de `dateDisabled` apagados (`aria-disabled`:
 * enfócanse pero non se escollen). Data simple; un rango nun formulario son dous campos (P14). Para
 * un filtro de días, `range` e `rangeEnd` marcan tamén o último día e os do medio (`aria-selected`
 * e `data-in-range`; a grella, `aria-multiselectable`, e o nome do día di «inicio» ou «fin»): quen
 * o usa decide que fai cada clic.
 *
 * Teclado (unha parada de tabulador, a do día enfocado): frechas día e semana, Inicio/Fin luns e
 * domingo, Re Páx/Av Páx mes, Maiús + Re Páx/Av Páx ano, Intro ou Espazo escollen. O título do
 * mes é unha rexión viva, así que cambiar de mes anúnciase; cada día ten de nome a data enteira.
 * Nomes de días e meses por `Intl` co idioma de LanguageService (cambian en quente).
 */
@Component({
  selector: 'furar-calendar',
  imports: [IconComponent, TranslocoPipe],
  host: { class: 'f-calendar' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-calendar__header">
      <button
        type="button"
        class="f-calendar__nav"
        [attr.aria-label]="'ui.calendar.previousMonth' | transloco"
        [attr.aria-disabled]="!canGoBack() || null"
        (click)="moveMonth(-1)"
      >
        <furar-icon name="chevron-left" />
      </button>
      <span class="f-calendar__title" aria-live="polite" [id]="titleId">{{ title() }}</span>
      <button
        type="button"
        class="f-calendar__nav"
        [attr.aria-label]="'ui.calendar.nextMonth' | transloco"
        [attr.aria-disabled]="!canGoForward() || null"
        (click)="moveMonth(1)"
      >
        <furar-icon name="chevron-right" />
      </button>
    </div>
    <table
      role="grid"
      class="f-calendar__grid"
      [attr.aria-labelledby]="titleId"
      [attr.aria-multiselectable]="range() || null"
    >
      <thead>
        <tr>
          @for (day of weekdays(); track $index) {
            <th scope="col" class="f-calendar__weekday">
              <span aria-hidden="true">{{ day.short }}</span>
              <span class="sr-only">{{ day.long }}</span>
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @for (week of weeks(); track $index) {
          <tr>
            @for (day of week; track $index) {
              @if (day) {
                <td
                  class="f-calendar__day"
                  [attr.data-date]="day"
                  [attr.tabindex]="day === focused() ? 0 : -1"
                  [attr.aria-label]="dayName(day)"
                  [attr.aria-selected]="isSelected(day)"
                  [attr.data-in-range]="inRange(day) || null"
                  [attr.aria-current]="day === todayDate() ? 'date' : null"
                  [attr.aria-disabled]="isDisabled(day) || null"
                  (click)="onDayClick(day)"
                  (keydown)="onKeydown($event, day)"
                >
                  {{ dayNumber(day) }}
                </td>
              } @else {
                <td class="f-calendar__day" data-outside></td>
              }
            }
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class CalendarComponent {
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Data escollida (ISO «2026-12-24») ou `null`. */
  readonly value = model<IsoDate | null>(null);
  /** Primeira data que se pode escoller (incluída). */
  readonly min = input<IsoDate | null>(null);
  /** Última data que se pode escoller (incluída). */
  readonly max = input<IsoDate | null>(null);
  /** Outros días que non se poden escoller (festivos, pechados…): decídeo quen o usa. */
  readonly dateDisabled = input<(date: IsoDate) => boolean>(() => false);
  /**
   * Modo rango (filtros): quen o usa decide `value` (o inicio) e `rangeEnd` (o fin) con cada
   * `picked`; o calendario só marca os dous extremos e os días do medio.
   */
  readonly range = input(false, { transform: booleanAttribute });
  /** Último día do rango que empeza en `value`. */
  readonly rangeEnd = input<IsoDate | null>(null);
  /** Un día do mes que se ve mentres non hai `value` (sen el, o de hoxe). */
  readonly month = input<IsoDate | null>(null);
  /** «Hoxe» que manda a API (`today`, Europe/Madrid); se falta, o do navegador. */
  readonly today = input<IsoDate | null>(null);
  /** Escolleuse un día (clic, Intro ou Espazo), aínda que xa fose o escollido. */
  readonly picked = output<IsoDate>();

  protected readonly titleId = `furar-calendar-${nextId++}-title`;
  protected readonly todayDate = computed(() => this.today() ?? localToday());
  /** Día co foco itinerante; volve ao escollido (ou a hoxe) se cambian o valor ou os límites. */
  readonly focused = linkedSignal(() =>
    clamp(this.value() ?? this.month() ?? this.todayDate(), this.min(), this.max()),
  );

  protected readonly title = computed(() => formatMonth(this.focused(), this.language.lang()));
  protected readonly weekdays = computed(() => weekdayNames(this.language.lang()));
  protected readonly weeks = computed(() => monthGrid(this.focused()));
  protected readonly canGoBack = computed(() => {
    const min = this.min();
    return !min || addDays(startOfMonth(this.focused()), -1) >= min;
  });
  protected readonly canGoForward = computed(() => {
    const max = this.max();
    return !max || addMonths(startOfMonth(this.focused()), 1) <= max;
  });

  protected isSelected(day: IsoDate): boolean {
    return day === this.value() || day === this.rangeEnd() || this.inRange(day);
  }

  protected inRange(day: IsoDate): boolean {
    const from = this.value();
    const to = this.rangeEnd();
    return from !== null && to !== null && day > from && day < to;
  }

  /** A data enteira e, nun rango, se é o inicio ou o fin («luns, 5 de outubro de 2026, inicio»). */
  protected dayName(day: IsoDate): string {
    const name = formatLong(day, this.language.lang());
    if (!this.range()) return name;
    const ends = [
      day === this.value() ? this.transloco.translate('ui.calendar.rangeStart') : '',
      day === this.rangeEnd() ? this.transloco.translate('ui.calendar.rangeEnd') : '',
    ].filter(Boolean);
    return [name, ...ends].join(', ');
  }

  protected dayNumber(day: IsoDate): number {
    return Number(day.slice(8));
  }

  isDisabled(day: IsoDate): boolean {
    const min = this.min();
    const max = this.max();
    return (!!min && day < min) || (!!max && day > max) || this.dateDisabled()(day);
  }

  /** Leva o foco ao día itinerante xa pintado (quen abre un panel chámao tras o render). */
  focusActive(): void {
    this.host.nativeElement.querySelector<HTMLElement>(`[data-date="${this.focused()}"]`)?.focus();
  }

  /** Frechas do mes: o foco queda no botón (APG) e o día itinerante móvese un mes. */
  protected moveMonth(delta: number): void {
    if (delta < 0 ? this.canGoBack() : this.canGoForward()) {
      this.focused.set(clamp(addMonths(this.focused(), delta), this.min(), this.max()));
    }
  }

  protected onDayClick(day: IsoDate): void {
    this.focused.set(day);
    this.choose(day);
  }

  protected onKeydown(event: KeyboardEvent, day: IsoDate): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.choose(day);
      return;
    }
    const next = moveFor(event, day);
    if (next === null) {
      return;
    }
    event.preventDefault();
    this.focused.set(clamp(next, this.min(), this.max()));
    afterNextRender(() => this.focusActive(), { injector: this.injector });
  }

  private choose(day: IsoDate): void {
    if (this.isDisabled(day)) {
      return;
    }
    if (!this.range()) this.value.set(day);
    this.picked.emit(day);
  }
}
