// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../ui/button/button.directive';
import { CalendarComponent } from '../ui/calendar/calendar.component';
import type { IsoDate } from '../ui/calendar/local-date';
import { formatDayMonth } from '../ui/calendar/local-date';
import { madridToday } from '../ui/calendar/madrid-time';
import { pickDay } from '../ui/date-filter/date-filter';
import { FieldGroupComponent } from '../ui/field-group/field-group.component';
import { FieldPopoverComponent } from '../ui/field-popover/field-popover.component';
import { formatMinutes } from '../ui/time-picker/time-format';
import { TimePickerComponent } from '../ui/time-picker/time-picker.component';
import { WeekdayPickerComponent } from '../ui/weekday-picker/weekday-picker.component';
import { capitalized, maskToWeekdays, minutesText, weekdaysText, yearAfter } from './schedule';

/** Os días, as horas e as datas dun horario. */
export interface ScheduleValue {
  /** Máscara do selector de días (luns = 1). */
  readonly weekdays: number;
  readonly startMinute: number | null;
  readonly endMinute: number | null;
  readonly firstDate: IsoDate | null;
  readonly lastDate: IsoDate | null;
}

/** Os erros que se ven de cada campo. */
export interface ScheduleErrors {
  readonly weekdays?: ControlError;
  readonly hours?: ControlError;
  readonly dates?: ControlError;
}

let nextId = 0;

/**
 * Días · Horas · Datas dun horario que se repite (tempadas e actividades con horario), nunha
 * cápsula: Días (o panel cos días da semana), Horas (Desde e Ata, de 15 en 15) e Datas (o
 * calendario de rango con «Aplicar», como moito un ano). O que se proxecta vai enriba, na mesma
 * cápsula (a tempada pon alí a entidade e a instalación). O valor entra por `value` e cada cambio
 * sae por `changed`; os erros pásaos quen o usa, que decide cando se ven.
 */
@Component({
  selector: 'furar-schedule-fields',
  imports: [
    ButtonDirective,
    CalendarComponent,
    FieldGroupComponent,
    FieldPopoverComponent,
    ReactiveFormsModule,
    TimePickerComponent,
    TranslocoPipe,
    WeekdayPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid' },
  template: `
    @let t = 'booking.seasons.form.';
    @let e = errors();
    <furar-field-group>
      <ng-content />
      <furar-field-popover
        required
        data-season-days
        [label]="t + 'days' | transloco"
        [value]="daysText()"
        [placeholder]="t + 'choose' | transloco"
        [invalid]="!!e.weekdays"
        [describedBy]="e.weekdays ? id + '-days-error' : null"
      >
        <div class="f-season-form__panel">
          <furar-weekday-picker [formControl]="days" [label]="t + 'days' | transloco" />
        </div>
        @if (e.weekdays; as error) {
          <span fieldError class="f-field-error" [id]="id + '-days-error'">{{
            error.key | transloco: error.params
          }}</span>
        }
      </furar-field-popover>
      <furar-field-popover
        required
        data-season-hours
        [label]="t + 'hours' | transloco"
        [value]="hoursText()"
        [placeholder]="t + 'choose' | transloco"
        [invalid]="!!e.hours"
        [describedBy]="e.hours ? id + '-hours-error' : null"
      >
        <span class="inline-flex min-h-11 items-center gap-1">
          <furar-time-picker
            data-season-start
            required
            [label]="t + 'from' | transloco"
            [formControl]="start"
            [invalid]="!!e.hours"
          />
          <span aria-hidden="true">–</span>
          <furar-time-picker
            data-season-end
            required
            allowEndOfDay
            [label]="t + 'to' | transloco"
            [formControl]="end"
            [invalid]="!!e.hours"
          />
        </span>
        @if (e.hours; as error) {
          <span fieldError class="f-field-error" [id]="id + '-hours-error'">{{
            error.key | transloco: error.params
          }}</span>
        }
      </furar-field-popover>
      <furar-field-popover
        #datesField
        required
        data-season-dates
        [label]="t + 'dates' | transloco"
        [value]="datesText()"
        [placeholder]="t + 'choose' | transloco"
        [invalid]="!!e.dates"
        [describedBy]="e.dates ? id + '-dates-error' : null"
        (opened)="openDates()"
      >
        <div class="f-season-form__panel">
          <furar-calendar
            range
            [value]="from()"
            [rangeEnd]="to()"
            [min]="today"
            [max]="maxDate()"
            [today]="today"
            (picked)="pickDate($event)"
          />
          @if (pickFirst()) {
            <p class="f-field-error" role="alert" data-season-pick-first>{{ t + 'pickFirst' | transloco }}</p>
          }
          <div class="f-season-form__apply">
            <!-- Sempre activo: sen ningún día, o aviso sae enriba e o foco vai ao calendario. -->
            <button
              type="button"
              furarButton="primary"
              size="sm"
              data-season-apply
              (click)="applyDates() && datesField.close()"
            >
              {{ t + 'apply' | transloco }}
            </button>
          </div>
        </div>
        @if (e.dates; as error) {
          <span fieldError class="f-field-error" [id]="id + '-dates-error'">{{
            error.key | transloco: error.params
          }}</span>
        }
      </furar-field-popover>
    </furar-field-group>
  `,
})
export class ScheduleFieldsComponent {
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  /** O horario que vai escollido. */
  readonly value = input.required<ScheduleValue>();
  /** Os erros que se ven. */
  readonly errors = input<ScheduleErrors>({});
  /** O que cambiou. */
  readonly changed = output<Partial<ScheduleValue>>();

  protected readonly id = `furar-schedule-${nextId++}`;
  protected readonly today = madridToday();
  protected readonly days = new FormControl(0, { nonNullable: true });
  protected readonly start = new FormControl<number | null>(null);
  protected readonly end = new FormControl<number | null>(null);
  /** O rango que se está a escoller no calendario (aplícase con «Aplicar»). */
  protected readonly from = signal<IsoDate | null>(null);
  protected readonly to = signal<IsoDate | null>(null);
  /** Premeuse «Aplicar» sen ningún día: o aviso enriba do botón. */
  protected readonly pickFirst = signal(false);
  private readonly calendar = viewChild(CalendarComponent);

  protected readonly daysText = computed(() => {
    this.langChanges();
    const lang = this.language.lang();
    const text = weekdaysText(maskToWeekdays(this.value().weekdays), lang, this.transloco.translate('booking.who.and'));
    return text ? capitalized(text, lang) : null;
  });
  protected readonly datesText = computed(() => {
    this.langChanges();
    const { firstDate, lastDate } = this.value();
    if (!firstDate || !lastDate) return null;
    const lang = this.language.lang();
    return this.transloco.translate('booking.seasons.rangeField', {
      from: formatDayMonth(firstDate, lang),
      to: formatDayMonth(lastDate, lang),
    });
  });
  /** «19:00–20:30»; con só unha das dúas, a que hai. */
  protected readonly hoursText = computed(() => {
    const { startMinute, endMinute } = this.value();
    if (startMinute === null && endMinute === null) return null;
    const lang = this.language.lang();
    if (startMinute !== null && endMinute !== null) return minutesText(startMinute, endMinute, lang);
    const part = (minute: number | null) => (minute === null ? '' : formatMinutes(minute, lang));
    return `${part(startMinute)}–${part(endMinute)}`;
  });
  /** Como moito un ano desde o primeiro día escollido (ou desde hoxe). */
  protected readonly maxDate = computed(() => yearAfter(this.from() ?? this.today));

  constructor() {
    // O que vén de fóra pasa aos controis sen volver avisar.
    effect(() => {
      const { weekdays, startMinute, endMinute } = this.value();
      untracked(() => {
        if (this.days.value !== weekdays) this.days.setValue(weekdays, { emitEvent: false });
        if (this.start.value !== startMinute) this.start.setValue(startMinute, { emitEvent: false });
        if (this.end.value !== endMinute) this.end.setValue(endMinute, { emitEvent: false });
      });
    });
    this.days.valueChanges.subscribe((weekdays) => this.changed.emit({ weekdays }));
    this.start.valueChanges.subscribe((startMinute) => this.changed.emit({ startMinute }));
    this.end.valueChanges.subscribe((endMinute) => this.changed.emit({ endMinute }));
  }

  /** Ao abrir as datas, o calendario parte das que xa hai. */
  protected openDates(): void {
    this.pickFirst.set(false);
    this.from.set(this.value().firstDate);
    this.to.set(this.value().lastDate);
  }

  protected pickDate(day: IsoDate): void {
    this.pickFirst.set(false);
    const range = pickDay({ from: this.from(), to: this.to() }, day);
    this.from.set(range.from);
    this.to.set(range.to);
  }

  /**
   * «Aplicar»: un só día escollido é un horario dun día. Sen ningún, o aviso e o foco no calendario;
   * devolve se aplicou (e o panel pecha).
   */
  protected applyDates(): boolean {
    const from = this.from();
    if (!from) {
      this.pickFirst.set(true);
      this.calendar()?.focusActive();
      return false;
    }
    this.changed.emit({ firstDate: from, lastDate: this.to() ?? from });
    return true;
  }
}
