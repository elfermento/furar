// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { FieldsetDirective } from '../../ui/card/fieldset.directive';
import { IconComponent } from '../../ui/icon/icon.component';
import { SegmentedDirective } from '../../ui/segmented/segmented.directive';
import { SegmentedOptionDirective, SegmentedRadioDirective } from '../../ui/segmented/segmented-radio.directive';
import { OptionsPipe } from '../../ui/select/options.pipe';
import { SelectComponent } from '../../ui/select/select.component';
import type { CustomFieldVisibility } from '../custom-field.types';
import { CUSTOM_FIELD_TARGETS, visibilitiesFor } from '../custom-field.types';
import type { DefinitionForm } from './editor-form';

/**
 * «Onde se usa» (ADR-0011, revisión do 2026-11-04; maqueta 57): un campo, un sitio. Arriba, o
 * selector «Concello | Instalación | Reserva»; debaixo, só o obrigatorio e quen o ve nese sitio. Cos
 * valores xa gardados o sitio non cambia, e unha liña di por que. Na reserva non hai «Público».
 */
@Component({
  selector: 'furar-place-editor',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    FieldsetDirective,
    IconComponent,
    OptionsPipe,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset furarFieldset class="mb-group" [formGroup]="form()">
      <legend id="def-place">{{ 'customFields.editor.usages' | transloco }}</legend>
      <div
        furarSegmented
        mode="radio"
        formControlName="target"
        aria-labelledby="def-place"
        [attr.aria-describedby]="locked() ? 'def-place-lock' : null"
      >
        @for (target of targets(); track target) {
          <button type="button" [furarSegmentedOption]="target">
            @if (known(target)) {
              {{ 'customFields.target.' + target | transloco }}
            } @else {
              {{ target }}
            }
          </button>
        }
      </div>
      @if (locked()) {
        <p class="f-help gap-unit mt-unit flex items-center" id="def-place-lock">
          <furar-icon name="lock" />
          {{ 'customFields.editor.placeLocked' | transloco }}
        </p>
      }
      <div class="f-field-row mt-form grid-cols-1 sm:grid-cols-2">
        <!-- Sen rótulo enriba: a casiña vai na fila dos controis, á altura do despregable. -->
        <div class="f-field">
          <label class="inline-flex min-h-11 items-center gap-unit">
            <input type="checkbox" class="size-5" formControlName="required" id="def-required" />
            {{ 'customFields.editor.usageRequired' | transloco }}
          </label>
        </div>
        <div class="f-field">
          <label class="label" for="def-visibility">{{ 'customFields.editor.usageVisibility' | transloco }}</label>
          <furar-select
            formControlName="visibility"
            selectId="def-visibility"
            [label]="'customFields.editor.usageVisibility' | transloco"
            [options]="visibilities() | furarOptions: visibilityPrefix() : lang() : notPublic()"
            [invalid]="invalid()"
            [describedBy]="describedBy()"
          />
          @if (errorKey(); as key) {
            <span class="field-error" id="def-visibility-error">{{ key | transloco }}</span>
          }
        </div>
      </div>
      @if (personalData()) {
        <p class="f-help mt-unit" id="def-visibility-note">
          {{ 'customFields.editor.personalDataNotPublic' | transloco }}
        </p>
      }
    </fieldset>
  `,
})
export class PlaceEditorComponent {
  readonly form = input.required<DefinitionForm>();
  /** Sitio escollido agora (o formulario non é un signal). */
  readonly target = input.required<string>();
  readonly personalData = input(false);
  /** O campo xa ten valores: o sitio non cambia. */
  readonly locked = input(false);
  /** Un dato persoal público (validación propia). */
  readonly personalPublic = input(false);
  /** Código do erro da API en «Quen o ve» (dato persoal público, público na reserva). */
  readonly serverError = input<string | null>(null);

  protected readonly lang = inject(LanguageService).lang;
  /** Os sitios coñecidos e, se o gardado é outro, tamén ese. */
  protected readonly targets = computed(() =>
    CUSTOM_FIELD_TARGETS.includes(this.target()) ? CUSTOM_FIELD_TARGETS : [...CUSTOM_FIELD_TARGETS, this.target()],
  );
  protected readonly visibilities = computed(() => visibilitiesFor(this.target()));
  /** Na reserva, «A persoa que reserva e o persoal» e «Só o persoal». */
  protected readonly visibilityPrefix = computed(() =>
    this.target() === 'BOOKING' ? 'customFields.visibilityBooking.' : 'customFields.visibility.',
  );
  /** Un dato persoal nunca é público: a opción vese pero non se pode escoller. */
  protected readonly notPublic = computed<readonly CustomFieldVisibility[]>(() =>
    this.personalData() ? ['PUBLIC'] : [],
  );
  /** O erro de «Quen o ve»: o da API ou o propio. */
  protected readonly errorKey = computed(() => {
    const server = this.serverError();
    if (server) {
      return `errors.${server}`;
    }
    return this.personalPublic() ? 'errors.customfield.personal-data-public' : null;
  });
  protected readonly invalid = computed(() => this.errorKey() !== null);
  protected readonly describedBy = computed(() => {
    const ids = [this.personalData() ? 'def-visibility-note' : '', this.invalid() ? 'def-visibility-error' : ''];
    return ids.filter(Boolean).join(' ') || null;
  });

  protected known(target: string): boolean {
    return CUSTOM_FIELD_TARGETS.includes(target);
  }
}
