// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { CustomFieldsFormComponent } from '../../../../shared/custom-fields/custom-fields-form.component';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { RequiredNoteComponent } from '../../../../shared/ui/required/required.component';
import { AMENITY_GROUPS, amenityIcon } from '../../amenities/amenities';
import { EMAIL_MAX, RIDEGA_MAX } from '../../data-card/data-form';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

type TextField = 'phone' | 'email' | 'ridegaCode';

/**
 * Paso 3, «Contacto e servizos» (opcional): o que ten a ficha e non cabe noutro paso. Teléfono,
 * correo, servizos e accesibilidade, código RIDEGA e os campos propios do concello (`Datos
 * propios`, con `furar-custom-fields-form`). Se o concello marcou algún campo propio como
 * obrigatorio, o paso deixa de ser opcional.
 */
@Component({
  selector: 'furar-wizard-contact-step',
  imports: [
    CustomFieldsFormComponent,
    FieldComponent,
    IconComponent,
    InputDirective,
    ReactiveFormsModule,
    RequiredNoteComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.contact.';
    @let d = 'facility.data.';
    @let a = 'facility.amenities.';
    @if (store.contactRequired()) {
      <furar-required-note />
    } @else {
      <p class="text-ink-soft" data-optional>{{ t + 'optional' | transloco }}</p>
    }
    <div class="gap-form grid" [formGroup]="store.data">
      <div class="f-field-row sm:grid-cols-2">
        @for (text of contact; track text.name) {
          <furar-field
            [fieldId]="'wizard-' + text.name"
            [label]="d + text.name + '.label' | transloco"
            [help]="d + text.name + '.help' | transloco: { max: text.max }"
            [error]="errors()[text.name]"
          >
            <input
              furarInput
              [id]="'wizard-' + text.name"
              [type]="text.type"
              [attr.autocomplete]="text.autocomplete"
              [attr.inputmode]="text.inputmode"
              [formControl]="store.data.controls[text.name]"
              [attr.aria-invalid]="errors()[text.name] ? 'true' : null"
              [attr.aria-describedby]="describedBy('wizard-' + text.name, true, errors()[text.name])"
            />
          </furar-field>
        }
      </div>

      <fieldset class="gap-item grid" [formGroup]="store.amenities">
        <legend class="mb-unit font-bold">{{ a + 'title' | transloco }}</legend>
        @for (group of groups; track group.key) {
          <div class="gap-unit grid" [attr.data-group]="group.key">
            <p class="text-ink-soft font-bold" [id]="'wizard-amenities-' + group.key">
              {{ a + 'group.' + group.key | transloco }}
            </p>
            <ul class="f-wizard-checks" [attr.aria-labelledby]="'wizard-amenities-' + group.key">
              @for (item of group.items; track item) {
                <li>
                  <label class="f-wizard-check">
                    <input type="checkbox" [formControlName]="item" />
                    <furar-icon [name]="iconOf(item)" [size]="20" />
                    <span>{{ a + 'item.' + item | transloco }}</span>
                  </label>
                </li>
              }
            </ul>
          </div>
        }
      </fieldset>

      <furar-field
        fieldId="wizard-ridegaCode"
        [label]="d + 'ridegaCode.label' | transloco"
        [help]="d + 'ridegaCode.help' | transloco: { max: ridegaMax }"
        [error]="errors().ridegaCode"
      >
        <input
          furarInput
          id="wizard-ridegaCode"
          autocomplete="off"
          [formControl]="store.data.controls.ridegaCode"
          [attr.aria-invalid]="errors().ridegaCode ? 'true' : null"
          [attr.aria-describedby]="describedBy('wizard-ridegaCode', true, errors().ridegaCode)"
        />
      </furar-field>

      @if (store.definitions().length) {
        <section class="gap-item grid" aria-labelledby="wizard-own">
          <div>
            <h3 id="wizard-own" class="font-bold">{{ t + 'own' | transloco }}</h3>
            <p class="f-help">{{ t + 'ownHelp' | transloco }}</p>
          </div>
          <furar-custom-fields-form
            idPrefix="wizard-cf"
            [definitions]="store.definitions()"
            [form]="store.data.controls.customFields"
            [lang]="lang()"
            [showErrors]="tried()"
          />
        </section>
      }
    </div>
  `,
})
export class ContactStepComponent {
  protected readonly store = inject(WizardStore);
  protected readonly lang = inject(LanguageService).lang;
  protected readonly groups = AMENITY_GROUPS;
  protected readonly iconOf = amenityIcon;
  protected readonly describedBy = describedBy;
  protected readonly contact = [
    { name: 'phone', type: 'tel', autocomplete: 'tel', inputmode: 'tel', max: 0 },
    { name: 'email', type: 'email', autocomplete: 'email', inputmode: 'email', max: EMAIL_MAX },
  ] as const;
  protected readonly ridegaMax = RIDEGA_MAX;
  protected readonly tried = computed(() => this.store.tried().has(STEP.contact));
  protected readonly errors = computed(() => {
    this.store.ok();
    const c = this.store.data.controls;
    const tried = this.tried();
    return Object.fromEntries(
      (['phone', 'email', 'ridegaCode'] as const).map((name) => [name, fieldError(c[name], tried)]),
    ) as Record<TextField, ReturnType<typeof fieldError>>;
  });
}
