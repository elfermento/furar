// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ControlError } from '../../shared/custom-fields/custom-field-controls';
import { FieldGroupComponent } from '../../shared/ui/field-group/field-group.component';
import { FieldLineComponent } from '../../shared/ui/field-line/field-line.component';
import { FieldComponent } from '../../shared/ui/field.component';
import { InputDirective } from '../../shared/ui/input/input.directive';
import { SegmentedOptionDirective, SegmentedRadioDirective } from '../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { EventForm, PlaceKind } from './event-form';
import { PLACE_MAX } from './event-form';

/** Os erros que se ven do lugar. */
export interface PlaceErrors {
  readonly facilityId: ControlError | null;
  readonly useId: ControlError | null;
  readonly partId: ControlError | null;
  readonly place: ControlError | null;
}

/**
 * O «Lugar» do evento (sistema de campos): fila solta coa escolla «Instalación» ·
 * «Outro sitio» (segmentado) e, debaixo, o campo: a instalación do concello ou o texto libre. Nunha
 * actividade con horario (`withUse`), a instalación vai nunha fila co uso e, se o uso vai por
 * partes, a parte: as sesións ocupan iso coma unha tempada.
 */
@Component({
  selector: 'furar-event-place-field',
  imports: [
    FieldComponent,
    FieldGroupComponent,
    FieldLineComponent,
    InputDirective,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SelectComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid' },
  template: `
    @let t = 'event.form.';
    @let e = errors();
    <div class="grid gap-form" [formGroup]="form()">
      <furar-field-line #line required [label]="t + 'place' | transloco" [labelId]="fieldId() + '-label'">
        <div furarSegmented mode="radio" formControlName="placeKind" [attr.aria-labelledby]="line.labelId()">
          <button type="button" furarSegmentedOption="FACILITY" data-place-kind="facility">
            {{ t + 'placeFacility' | transloco }}
          </button>
          <button type="button" furarSegmentedOption="OTHER" data-place-kind="other">
            {{ t + 'placeOther' | transloco }}
          </button>
        </div>
      </furar-field-line>
      @if (placeKind() === 'FACILITY') {
        <furar-field-group cells [style.--f-cell]="'10rem'">
          <furar-field [fieldId]="fieldId()" [label]="t + 'placeFacility' | transloco" [error]="e.facilityId">
            <furar-select
              formControlName="facilityId"
              data-event-facility
              [selectId]="fieldId()"
              [label]="t + 'placeFacility' | transloco"
              [options]="facilities()"
              [placeholder]="t + 'facilityPlaceholder' | transloco"
              [invalid]="!!e.facilityId"
              [describedBy]="e.facilityId ? fieldId() + '-error' : null"
            />
          </furar-field>
          @if (withUse()) {
            <furar-field [fieldId]="fieldId() + '-use'" [label]="t + 'use' | transloco" [error]="e.useId">
              <furar-select
                formControlName="useId"
                data-event-use
                [selectId]="fieldId() + '-use'"
                [label]="t + 'use' | transloco"
                [options]="uses()"
                [placeholder]="t + 'choose' | transloco"
                [invalid]="!!e.useId"
                [describedBy]="e.useId ? fieldId() + '-use-error' : null"
              />
            </furar-field>
            @if (parts().length) {
              <furar-field [fieldId]="fieldId() + '-part'" [label]="t + 'part' | transloco" [error]="e.partId">
                <furar-select
                  formControlName="partId"
                  data-event-part
                  [selectId]="fieldId() + '-part'"
                  [label]="t + 'part' | transloco"
                  [options]="parts()"
                  [placeholder]="t + 'choose' | transloco"
                  [invalid]="!!e.partId"
                  [describedBy]="e.partId ? fieldId() + '-part-error' : null"
                />
              </furar-field>
            }
          }
        </furar-field-group>
      } @else {
        <furar-field [fieldId]="fieldId()" [label]="t + 'placeOther' | transloco" [error]="e.place">
          <input
            furarInput
            formControlName="place"
            autocomplete="off"
            data-event-place
            [id]="fieldId()"
            [attr.maxlength]="placeMax"
            [placeholder]="t + 'placeOtherPlaceholder' | transloco"
            [attr.aria-invalid]="e.place ? 'true' : null"
            [attr.aria-describedby]="e.place ? fieldId() + '-error' : null"
          />
        </furar-field>
      }
    </div>
  `,
})
export class EventPlaceFieldComponent {
  readonly form = input.required<EventForm>();
  readonly fieldId = input.required<string>();
  readonly facilities = input.required<readonly SelectOption<string>[]>();
  readonly placeKind = input.required<PlaceKind>();
  /** Con uso (e parte): unha actividade con horario nunha instalación. */
  readonly withUse = input(false, { transform: booleanAttribute });
  readonly uses = input<readonly SelectOption<string>[]>([]);
  readonly parts = input<readonly SelectOption<string>[]>([]);
  readonly errors = input<PlaceErrors>({ facilityId: null, useId: null, partId: null, place: null });

  protected readonly placeMax = PLACE_MAX;
}
