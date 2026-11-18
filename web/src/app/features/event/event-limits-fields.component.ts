// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import type { ControlError } from '../../shared/custom-fields/custom-field-controls';
import { describedBy } from '../../shared/ui/field-errors';
import { FieldGroupComponent } from '../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../shared/ui/field.component';
import { InputDirective } from '../../shared/ui/input/input.directive';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { EventForm } from './event-form';
import { PRICE_PERIODS } from './event-form';

/** Os erros que se ven destes campos. */
export interface LimitErrors {
  readonly capacity: ControlError | null;
  readonly price: ControlError | null;
  readonly birthYearFrom: ControlError | null;
  readonly birthYearTo: ControlError | null;
}

/**
 * Prazas · Prezo (· Cada, nunha actividade con prezo) e Nacidos desde · ata, cada fila na súa
 * cápsula de campos curtos. Os anos baleiros: calquera idade.
 */
@Component({
  selector: 'furar-event-limits-fields',
  imports: [FieldComponent, FieldGroupComponent, InputDirective, ReactiveFormsModule, SelectComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid gap-form' },
  template: `
    @let t = 'event.form.';
    @let e = errors();
    @let id = fieldId();
    <ng-container [formGroup]="form()">
      <furar-field-group cells [style.--f-cell]="'8rem'">
        <furar-field [fieldId]="id + '-capacity'" [label]="t + 'seats' | transloco" [error]="e.capacity">
          <input
            furarInput
            formControlName="capacity"
            inputmode="numeric"
            autocomplete="off"
            data-event-capacity
            [id]="id + '-capacity'"
            [placeholder]="t + 'seatsPlaceholder' | transloco"
            [attr.aria-invalid]="e.capacity ? 'true' : null"
            [attr.aria-describedby]="describedBy(id + '-capacity', false, e.capacity)"
          />
        </furar-field>
        <furar-field
          [fieldId]="id + '-price'"
          [label]="t + 'price' | transloco"
          [tip]="t + 'priceTip' | transloco"
          [error]="e.price"
        >
          <input
            furarInput
            formControlName="price"
            inputmode="decimal"
            autocomplete="off"
            data-event-price
            [id]="id + '-price'"
            [placeholder]="t + 'pricePlaceholder' | transloco"
            [attr.aria-invalid]="e.price ? 'true' : null"
            [attr.aria-describedby]="describedBy(id + '-price', false, e.price)"
          />
        </furar-field>
        @if (scheduled() && priced()) {
          <furar-field [fieldId]="id + '-period'" [label]="t + 'period.label' | transloco">
            <furar-select
              formControlName="pricePeriod"
              data-event-period
              [selectId]="id + '-period'"
              [label]="t + 'period.label' | transloco"
              [options]="periodOptions()"
            />
          </furar-field>
        }
      </furar-field-group>

      <furar-field-group cells [style.--f-cell]="'8rem'">
        <furar-field
          [fieldId]="id + '-born-from'"
          [label]="t + 'bornFrom' | transloco"
          [tip]="t + 'bornTip' | transloco"
          [error]="e.birthYearFrom"
        >
          <input
            furarInput
            formControlName="birthYearFrom"
            inputmode="numeric"
            autocomplete="off"
            maxlength="4"
            data-event-born-from
            [id]="id + '-born-from'"
            [placeholder]="t + 'bornAny' | transloco"
            [attr.aria-invalid]="e.birthYearFrom ? 'true' : null"
            [attr.aria-describedby]="describedBy(id + '-born-from', false, e.birthYearFrom)"
          />
        </furar-field>
        <furar-field [fieldId]="id + '-born-to'" [label]="t + 'bornTo' | transloco" [error]="e.birthYearTo">
          <input
            furarInput
            formControlName="birthYearTo"
            inputmode="numeric"
            autocomplete="off"
            maxlength="4"
            data-event-born-to
            [id]="id + '-born-to'"
            [placeholder]="t + 'bornAny' | transloco"
            [attr.aria-invalid]="e.birthYearTo ? 'true' : null"
            [attr.aria-describedby]="describedBy(id + '-born-to', false, e.birthYearTo)"
          />
        </furar-field>
      </furar-field-group>
    </ng-container>
  `,
})
export class EventLimitsFieldsComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly form = input.required<EventForm>();
  readonly fieldId = input.required<string>();
  /** Unha actividade con horario: o prezo leva «Cada». */
  readonly scheduled = input(false, { transform: booleanAttribute });
  readonly errors = input.required<LimitErrors>();

  protected readonly describedBy = describedBy;
  /** O prezo que vai escrito (o período só ten sentido se hai prezo). */
  private readonly price = signal('');
  protected readonly priced = computed(() => !!this.price().trim());
  protected readonly periodOptions = computed<SelectOption<string>[]>(() => {
    this.language.lang();
    return PRICE_PERIODS.map((value) => ({ value, label: this.transloco.translate('event.form.period.' + value) }));
  });

  constructor() {
    effect((onCleanup) => {
      const control = this.form().controls.price;
      untracked(() => this.price.set(control.value));
      const subscription = control.valueChanges.subscribe((value) => this.price.set(value));
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
