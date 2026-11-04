// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ApiProblem } from '../core/errors/api-problem';
import { EditFieldComponent } from '../shared/ui/edit-section/edit-field.component';
import { EditSectionComponent } from '../shared/ui/edit-section/edit-section.component';
import { describedBy, fieldError } from '../shared/ui/field-errors';
import { FieldComponent } from '../shared/ui/field.component';
import { InputDirective } from '../shared/ui/input/input.directive';
import { SwitchComponent } from '../shared/ui/switch/switch.component';

/** Correos que o «servidor» do catálogo rexeita, para ver un erro da API no seu campo. */
const REJECTED = ['erro@exemplo.gal', 'error@ejemplo.es'];
const DELAY_MS = 600;

/**
 * Sección do catálogo para D1-05b: dúas `furar-edit-section` cun gardado simulado. «Contacto» ten
 * valores en repouso como botón (`furar-edit-field`) e un erro do servidor no campo; «Descrición»
 * é un formulario simple e, co interruptor, falla con 412 para ver o aviso da sección.
 */
@Component({
  selector: 'furar-catalog-edit',
  imports: [
    EditFieldComponent,
    EditSectionComponent,
    FieldComponent,
    InputDirective,
    ReactiveFormsModule,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-card gap-group grid" id="seccion-editable">
      <h2 class="f-card__title">{{ 'catalog.edit.title' | transloco }}</h2>
      <furar-edit-section
        [formGroup]="contact"
        [headingLevel]="3"
        [heading]="'catalog.edit.contact' | transloco"
        [save]="saveContact"
      >
        <furar-edit-field
          [label]="'catalog.edit.phone' | transloco"
          [value]="saved().phone"
          [control]="contact.controls.phone"
        >
          <furar-field fieldId="catalog-edit-phone" [label]="'catalog.edit.phone' | transloco">
            <input furarInput id="catalog-edit-phone" type="tel" autocomplete="off" formControlName="phone" />
          </furar-field>
        </furar-edit-field>
        <furar-edit-field
          [label]="'catalog.edit.email' | transloco"
          [value]="saved().email"
          [control]="contact.controls.email"
        >
          <furar-field
            fieldId="catalog-edit-email"
            [label]="'catalog.edit.email' | transloco"
            [help]="'catalog.edit.emailHelp' | transloco"
            [required]="true"
            [error]="emailError()"
          >
            <input
              furarInput
              id="catalog-edit-email"
              type="email"
              autocomplete="off"
              formControlName="email"
              [attr.aria-invalid]="emailError() ? 'true' : null"
              [attr.aria-describedby]="emailDescribedBy()"
            />
          </furar-field>
        </furar-edit-field>
      </furar-edit-section>

      <furar-edit-section
        [formGroup]="description"
        [headingLevel]="3"
        [heading]="'catalog.edit.description' | transloco"
        [save]="saveText"
      >
        <furar-field fieldId="catalog-edit-text" [label]="'catalog.edit.description' | transloco">
          <textarea furarInput id="catalog-edit-text" rows="3" formControlName="text"></textarea>
        </furar-field>
        <furar-switch formControlName="fail" [label]="'catalog.edit.fail' | transloco" />
      </furar-edit-section>
    </div>
  `,
})
export class CatalogEditComponent {
  protected readonly contact = new FormGroup({
    phone: new FormControl('981 000 000', { nonNullable: true }),
    email: new FormControl('instalacions@exemplo.gal', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });
  protected readonly description = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    fail: new FormControl(false, { nonNullable: true }),
  });
  /** O último gardado: é o que mostran os botóns en repouso. */
  protected readonly saved = signal(this.contact.getRawValue());

  private readonly contactEvents = toSignal(this.contact.events);
  protected readonly emailError = computed(() => {
    this.contactEvents();
    return fieldError(this.contact.controls.email, false);
  });
  protected readonly emailDescribedBy = computed(() => describedBy('catalog-edit-email', true, this.emailError()));

  protected readonly saveContact = async (): Promise<void> => {
    await wait();
    const value = this.contact.getRawValue();
    if (REJECTED.includes(value.email.trim().toLowerCase())) {
      throw new ApiProblem(422, 'validation.failed', {}, [field('email', 'customfield.invalid-format')], undefined, '');
    }
    this.saved.set(value);
  };

  protected readonly saveText = async (): Promise<void> => {
    await wait();
    if (this.description.controls.fail.value) {
      throw new ApiProblem(412, 'common.stale-version', {}, [], 'catalogo-0001', '');
    }
  };
}

function wait(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, DELAY_MS));
}

function field(name: string, code: 'customfield.invalid-format') {
  return { field: name, code, params: {} };
}
