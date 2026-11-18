// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { CustomFieldsFormComponent } from '../../../../shared/custom-fields/custom-fields-form.component';
import { describedBy } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { FieldGroupComponent } from '../../../../shared/ui/field-group/field-group.component';
import { SwitchComponent } from '../../../../shared/ui/switch/switch.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { AMENITY_GROUPS } from '../../amenities/amenities';
import type { ContactField } from '../../data-card/data-form';
import { contactError } from '../../data-card/data-form';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 3, «Contacto e servizos» (opcional): o que ten a ficha e non cabe noutro paso. Teléfono,
 * correo, servizos e accesibilidade (cada grupo co seu título), código RIDEGA e os campos propios
 * do concello (`Datos propios`, con `furar-custom-fields-form`). Se o concello marcou algún campo
 * propio como obrigatorio, o paso deixa de ser opcional. Sen liñas de axuda: o que se admite dío
 * o erro (`contactError`).
 */
@Component({
  selector: 'furar-wizard-contact-step',
  imports: [
    CustomFieldsFormComponent,
    FieldComponent,
    FieldGroupComponent,
    InputDirective,
    ReactiveFormsModule,
    SwitchComponent,
    TranslocoPipe,
  ],
  host: { class: 'f-wizard__step' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.contact.';
    @let d = 'facility.data.';
    @let a = 'facility.amenities.';
    @if (!store.contactRequired()) {
      <p class="text-ink-soft" data-optional>{{ t + 'optional' | transloco }}</p>
    }
    <div class="gap-form grid" [formGroup]="store.data">
      <furar-field-group cells [style.--f-cell]="'13rem'">
        @for (text of contact; track text.name) {
          <furar-field
            [fieldId]="'wizard-' + text.name"
            [label]="d + text.name + '.label' | transloco"
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
              [attr.aria-describedby]="describedBy('wizard-' + text.name, false, errors()[text.name])"
            />
          </furar-field>
        }
      </furar-field-group>

      <div class="gap-form grid" [formGroup]="store.amenities">
        @for (group of groups; track group.key) {
          <div class="gap-unit grid" [attr.data-group]="group.key">
            <h3 class="font-bold" [id]="'wizard-amenities-' + group.key">
              {{ a + 'group.' + group.key | transloco }}
            </h3>
            <div class="grid" role="group" [attr.aria-labelledby]="'wizard-amenities-' + group.key">
              @for (item of group.items; track item) {
                <furar-switch [formControlName]="item" [label]="a + 'item.' + item | transloco" />
              }
            </div>
          </div>
        }
      </div>

      <furar-field
        fieldId="wizard-ridegaCode"
        [label]="d + 'ridegaCode.label' | transloco"
        [error]="errors().ridegaCode"
      >
        <input
          furarInput
          id="wizard-ridegaCode"
          autocomplete="off"
          [formControl]="store.data.controls.ridegaCode"
          [attr.aria-invalid]="errors().ridegaCode ? 'true' : null"
          [attr.aria-describedby]="describedBy('wizard-ridegaCode', false, errors().ridegaCode)"
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
  protected readonly describedBy = describedBy;
  protected readonly contact = [
    { name: 'phone', type: 'tel', autocomplete: 'tel', inputmode: 'tel' },
    { name: 'email', type: 'email', autocomplete: 'email', inputmode: 'email' },
  ] as const;
  protected readonly tried = computed(() => this.store.tried().has(STEP.contact));
  protected readonly errors = computed(() => {
    this.store.ok();
    const c = this.store.data.controls;
    const tried = this.tried();
    return Object.fromEntries(
      (['phone', 'email', 'ridegaCode'] as const).map((name) => [name, contactError(name, c[name], tried)]),
    ) as Record<ContactField, ReturnType<typeof contactError>>;
  });
}
