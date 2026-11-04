// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { FieldPopoverComponent } from '../../../shared/ui/field-popover/field-popover.component';
import { fieldError } from '../../../shared/ui/field-errors';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { WeekdayPickerComponent } from '../../../shared/ui/weekday-picker/weekday-picker.component';
import type { UseForm } from './use-form';
import { AVAILABILITIES } from './use-form';
import { UsePeriodsComponent } from './use-periods.component';
import { weekdaysText, whenDetail } from './use-terms';

/**
 * «Cando» do modal do uso (D2-11; maqueta 21): fila-resumo cos días en grande («Todos os días») e
 * as datas en gris («Sempre», «Do 20/12/2026 ao 06/01/2027 · bloquea os demais usos») que abre
 * un `furar-field-popover` con «Datas» (sempre ou só unhas datas, cos períodos), «Días»
 * (`furar-weekday-picker`) e, só con datas, «Bloquea os demais usos». Os erros destes campos
 * saen tamén debaixo do resumo, así vense co panel pechado.
 */
@Component({
  selector: 'furar-facility-use-when',
  imports: [
    ButtonDirective,
    FieldPopoverComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SwitchComponent,
    TranslocoPipe,
    UsePeriodsComponent,
    WeekdayPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.uses.';
    <furar-field-popover
      #popover
      look="summary"
      icon="calendar-month"
      [label]="t + 'availability.label' | transloco"
      [value]="summary().title"
      [detail]="summary().detail"
      [action]="t + 'when.change' | transloco"
      [describedBy]="error() ? idPrefix() + '-when-error' : null"
      data-use-when
    >
      <div class="f-use-when" [formGroup]="form()">
        <div class="f-use-when__section">
          <span class="f-label" [id]="idPrefix() + '-dates'">{{ t + 'when.dates' | transloco }}</span>
          <div
            furarSegmented
            mode="radio"
            formControlName="availability"
            [attr.aria-labelledby]="idPrefix() + '-dates'"
          >
            @for (availability of availabilities; track availability) {
              <button type="button" [furarSegmentedOption]="availability">
                {{ t + 'availability.' + availability | transloco }}
              </button>
            }
          </div>
          @if (value().availability === 'DATES') {
            <furar-facility-use-periods [periods]="form().controls.periods" [idPrefix]="idPrefix()" />
          }
        </div>
        <div class="f-use-when__section">
          <span class="f-label" aria-hidden="true">{{ t + 'when.days' | transloco }}</span>
          <furar-weekday-picker formControlName="weekdays" [label]="t + 'when.days' | transloco" />
          @if (weekdaysError(); as e) {
            <span class="f-field-error">{{ e.key | transloco: e.params }}</span>
          }
        </div>
        @if (value().availability === 'DATES') {
          <div class="f-use-when__section">
            <furar-switch
              formControlName="blocksOthers"
              [label]="t + 'blocksOthers.label' | transloco"
              [describedBy]="idPrefix() + '-blocks-help'"
            />
            <span class="f-help" [id]="idPrefix() + '-blocks-help'">{{ t + 'blocksOthers.help' | transloco }}</span>
          </div>
        }
        <div class="f-use-when__foot">
          <button type="button" furarButton="primary" size="sm" data-when-done (click)="popover.close()">
            {{ t + 'when.done' | transloco }}
          </button>
        </div>
      </div>
    </furar-field-popover>
    @if (error(); as e) {
      <span class="f-field-error" [id]="idPrefix() + '-when-error'">{{ e.key | transloco: e.params }}</span>
    }
  `,
  host: { class: 'f-use-when-field' },
})
export class UseWhenComponent {
  /** O formulario do uso (usa `availability`, `periods`, `weekdays` e `blocksOthers`). */
  readonly form = input.required<UseForm>();
  readonly idPrefix = input.required<string>();

  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly popover = viewChild.required<FieldPopoverComponent>('popover');
  private readonly events = toSignal(
    toObservable(this.form).pipe(switchMap((form) => form.events.pipe(startWith(null)))),
  );

  protected readonly availabilities = AVAILABILITIES;
  protected readonly value = computed(() => {
    this.events();
    return this.form().getRawValue();
  });

  protected readonly summary = computed(() => {
    this.langChanges();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const { weekdays, availability, periods, blocksOthers } = this.value();
    return {
      title: weekdaysText(weekdays, this.language.lang(), t),
      detail: whenDetail(availability, periods, availability === 'DATES' && blocksOthers, t),
    };
  });

  protected readonly weekdaysError = computed(() => {
    this.events();
    return fieldError(this.form().controls.weekdays, false);
  });

  /** O primeiro erro do «Cando» (días, períodos ou solape), para velo co panel pechado. */
  protected readonly error = computed(() => {
    this.events();
    const c = this.form().controls;
    const periods = c.periods.enabled && c.periods.touched && c.periods.invalid;
    const rows = c.periods.enabled && c.periods.controls.some((p) => p.touched && p.invalid);
    return (
      fieldError(c.weekdays, false) ??
      (periods || rows ? { key: 'errors.facility.use-invalid', params: {} } : null) ??
      null
    );
  });

  /** Ten erros: o modal abre o panel para amosalos (ao gardar ou se a API os devolveu). */
  invalid(): boolean {
    const c = this.form().controls;
    return c.weekdays.invalid || (c.periods.enabled && c.periods.invalid);
  }

  open(): void {
    this.popover().open();
  }
}
