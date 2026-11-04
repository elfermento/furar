// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { fieldError } from '../../../../shared/ui/field-errors';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../../shared/ui/segmented/segmented.directive';
import { TimePickerComponent } from '../../../../shared/ui/time-picker/time-picker.component';
import { DayRowComponent } from '../../schedule/day-row.component';
import { DAYS, HOLIDAYS_ROW } from '../../schedule/schedule.store';
import { weekdayName } from '../../schedule/week-format';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 6, «Horario» (maqueta 53): unha tempada «Todo o ano» (do 1 de xaneiro ao 31 de decembro,
 * festivos pechados). Por defecto, o mesmo horario todos os días; «Por días» abre a semana coas
 * mesmas filas ca a ficha. Máis tempadas, na ficha.
 */
@Component({
  selector: 'furar-wizard-schedule-step',
  imports: [
    ButtonDirective,
    DayRowComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.schedule.';
    <p class="gap-x-item flex flex-wrap items-center">
      <span>
        {{ t + 'season' | transloco }} <b>{{ t + 'wholeYear' | transloco }}</b>
      </span>
      <button
        type="button"
        furarButton="quiet"
        size="sm"
        aria-controls="wizard-season-hint"
        [attr.aria-expanded]="hint()"
        (click)="hint.set(!hint())"
      >
        {{ t + 'addSeason' | transloco }}
      </button>
    </p>
    <p id="wizard-season-hint" class="f-help" [hidden]="!hint()">{{ t + 'seasonHint' | transloco }}</p>

    <div class="gap-unit grid">
      <span class="f-label" id="wizard-schedule-mode">{{ t + 'mode' | transloco }}</span>
      <div furarSegmented mode="radio" [formControl]="store.scheduleMode" aria-labelledby="wizard-schedule-mode">
        <button type="button" furarSegmentedOption="SAME">{{ t + 'same' | transloco }}</button>
        <button type="button" furarSegmentedOption="DAYS">{{ t + 'days' | transloco }}</button>
      </div>
    </div>

    @if (mode() === 'SAME') {
      <div class="gap-item flex flex-wrap items-end" [formGroup]="store.sameHours">
        <div class="gap-unit grid">
          <span class="f-label" aria-hidden="true">{{ t + 'from' | transloco }}</span>
          <furar-time-picker
            formControlName="startMinute"
            [max]="1425"
            [label]="t + 'openFrom' | transloco"
            [invalid]="!!sameError()"
            [describedBy]="sameError() ? 'wizard-same-error' : null"
          />
        </div>
        <div class="gap-unit grid">
          <span class="f-label" aria-hidden="true">{{ t + 'to' | transloco }}</span>
          <furar-time-picker
            formControlName="endMinute"
            allowEndOfDay
            [min]="15"
            [label]="t + 'openTo' | transloco"
            [invalid]="!!sameError()"
            [describedBy]="sameError() ? 'wizard-same-error' : null"
          />
        </div>
      </div>
      @if (sameError()) {
        <p class="f-field-error" id="wizard-same-error">
          {{ 'facility.schedule.errors.endBeforeStart' | transloco: { n: 1 } }}
        </p>
      }
    } @else {
      <ul class="f-wizard-week" [attr.aria-label]="t + 'week' | transloco">
        @for (day of week().controls.week.controls; track $index; let i = $index) {
          <li>
            <furar-facility-day-row editing idPrefix="wizard-week" [day]="day" [index]="i" [name]="names()[i]" />
          </li>
        }
      </ul>
      @if (noOpenDay()) {
        <p class="f-field-error">{{ 'errors.facility.no-open-day' | transloco }}</p>
      }
    }
  `,
})
export class ScheduleStepComponent {
  protected readonly store = inject(WizardStore);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  protected readonly hint = signal(false);
  protected readonly week = this.store.week;
  protected readonly tried = computed(() => this.store.tried().has(STEP.schedule));
  protected readonly mode = computed(() => {
    this.store.ok();
    return this.store.scheduleMode.value;
  });
  protected readonly sameError = computed(() => {
    this.store.ok();
    const group = this.store.sameHours;
    return (group.touched || this.tried()) && group.invalid ? fieldError(group, true) : null;
  });
  protected readonly noOpenDay = computed(() => this.tried() && this.store.noOpenDay());
  protected readonly names = computed(() => {
    const lang = this.language.lang();
    const holidays = 'facility.schedule.day.holidays';
    return DAYS.map((_, i) => (i === HOLIDAYS_ROW ? this.translateHolidays(holidays) : weekdayName(i, lang)));
  });

  private translateHolidays(key: string): string {
    return this.transloco.translate(key);
  }
}
