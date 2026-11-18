// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { FieldsetDirective } from '../../ui/card/fieldset.directive';
import { IconComponent } from '../../ui/icon/icon.component';
import { FieldGroupComponent } from '../../ui/field-group/field-group.component';
import { FieldComponent } from '../../ui/field.component';
import { OptionsPipe } from '../../ui/select/options.pipe';
import type { SelectOption } from '../../ui/select/select.component';
import { SelectComponent } from '../../ui/select/select.component';
import type { CustomFieldVisibility } from '../custom-field.types';
import { CUSTOM_FIELD_TARGETS, visibilitiesFor } from '../custom-field.types';
import type { DefinitionForm } from './editor-form';

/**
 * «Onde se usa» (ADR-0011, revisión do 2026-11-04): un campo, un sitio. Nun grupo de
 * campos, o despregable «Concello · Instalación · Reserva» e, debaixo, quen o ve nese sitio (o obrigatorio vai co
 * «dato persoal», no editor). Cos valores xa gardados o sitio non cambia, e unha liña di por que.
 * Na reserva non hai «Todo o mundo»; no concello e nas instalacións, «titular» é o persoal e non se
 * ofrece (`visibilitiesFor`). Na instalación e para «Todo o mundo», unha liña avisa de que se ve na
 * web e nos datos abertos (ADR-0034).
 */
@Component({
  selector: 'furar-place-editor',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    FieldsetDirective,
    IconComponent,
    OptionsPipe,
    FieldComponent,
    FieldGroupComponent,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset furarFieldset class="mb-group" [formGroup]="form()">
      <legend id="def-place">{{ 'customFields.editor.usages' | transloco }}</legend>
      <furar-field-group>
        <furar-field fieldId="def-target" [label]="'customFields.editor.usages' | transloco">
          <furar-select
            formControlName="target"
            selectId="def-target"
            [label]="'customFields.editor.usages' | transloco"
            [options]="targetOptions()"
            [describedBy]="locked() ? 'def-place-lock' : null"
          />
        </furar-field>
        <furar-field fieldId="def-visibility" [label]="'customFields.editor.usageVisibility' | transloco">
          <furar-select
            formControlName="visibility"
            selectId="def-visibility"
            [label]="'customFields.editor.usageVisibility' | transloco"
            [options]="visibilities() | furarOptions: visibilityPrefix() : lang() : notPublic()"
            [invalid]="invalid()"
            [describedBy]="describedBy()"
          />
        </furar-field>
        @if (errorKey(); as key) {
          <span class="f-field-error" id="def-visibility-error">{{ key | transloco }}</span>
        }
      </furar-field-group>
      @if (locked()) {
        <p class="f-help gap-unit mt-unit flex items-center" id="def-place-lock">
          <furar-icon name="lock" />
          {{ 'customFields.editor.placeLocked' | transloco }}
        </p>
      }
      @if (personalData()) {
        <p class="f-help mt-unit" id="def-visibility-note">
          {{ 'customFields.editor.personalDataNotPublic' | transloco }}
        </p>
      } @else if (publicFacility()) {
        <p class="f-help mt-unit" id="def-visibility-note" data-def-public-tip>
          {{ 'customFields.editor.publicTip' | transloco }}
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
  /** Gardado como «titular» nun sitio onde iso é o persoal: a opción segue á vista («O persoal»). */
  readonly legacyOwner = input(false);
  /** O campo xa ten valores: o sitio non cambia. */
  readonly locked = input(false);
  /** Instalación e «Todo o mundo»: o aviso de que se ve na web e nos datos abertos. */
  readonly publicFacility = input(false);
  /** Un dato persoal público (validación propia). */
  readonly personalPublic = input(false);
  /** Código do erro da API en «Quen o ve» (dato persoal público, público na reserva). */
  readonly serverError = input<string | null>(null);

  protected readonly lang = inject(LanguageService).lang;
  /** Os sitios coñecidos e, se o gardado é outro, tamén ese. */
  protected readonly targets = computed(() =>
    CUSTOM_FIELD_TARGETS.includes(this.target()) ? CUSTOM_FIELD_TARGETS : [...CUSTOM_FIELD_TARGETS, this.target()],
  );
  protected readonly visibilities = computed(() => visibilitiesFor(this.target(), this.legacyOwner()));
  /** Na reserva, «A persoa que reserva e o persoal» e «Só o persoal»; nos demais, «Todo o mundo» e «Só o persoal». */
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
    const note = this.personalData() || this.publicFacility();
    const ids = [note ? 'def-visibility-note' : '', this.invalid() ? 'def-visibility-error' : ''];
    return ids.filter(Boolean).join(' ') || null;
  });

  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  /** «Onde se usa»: un despregable cos sitios coñecidos (e o gardado, se é outro, co seu código). */
  protected readonly targetOptions = computed<SelectOption<string>[]>(() => {
    this.langChanges();
    return this.targets().map((value) => ({
      value,
      label: CUSTOM_FIELD_TARGETS.includes(value) ? this.transloco.translate('customFields.target.' + value) : value,
    }));
  });
}
