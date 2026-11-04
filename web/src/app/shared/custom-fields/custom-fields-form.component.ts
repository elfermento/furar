// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
import type { FormControl } from '@angular/forms';
import { CustomFieldComponent } from './custom-field.component';
import type { CustomFieldsForm } from './custom-field-controls';
import { activeDefinitions } from './custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValue } from './custom-field.types';

/**
 * Renderizador reutilizable de campos personalizados (ADR-0011). Vale para calquera
 * formulario: o pai crea o FormRecord con buildCustomFieldsForm e envía os valores con
 * customFieldValuesOf. Uso: <furar-custom-fields-form [definitions]="defs" [form]="cf" [lang]="lang" />
 */
@Component({
  selector: 'furar-custom-fields-form',
  imports: [CustomFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (field of fields(); track field.code) {
      <div class="mb-form">
        <furar-custom-field
          [definition]="field"
          [control]="controlFor(field.code)"
          [lang]="lang()"
          [idPrefix]="idPrefix()"
          [showErrors]="showErrors()"
        />
      </div>
    }
  `,
})
export class CustomFieldsFormComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly definitions = input.required<readonly CustomFieldDefinition[]>();
  readonly form = input.required<CustomFieldsForm>();
  readonly lang = input.required<string>();
  /** Prefixo dos id (único na páxina se hai varios formularios, p. ex. vista previa gl/es). */
  readonly idPrefix = input('cf');
  readonly showErrors = input(false);

  /** Sen grupos nin orde manual (especificación 1b): os activos, na orde da API. */
  protected readonly fields = computed<CustomFieldDefinition[]>(() =>
    activeDefinitions(this.definitions()).filter((field) => this.form().controls[field.code] !== undefined),
  );

  protected controlFor(code: string): FormControl<CustomFieldValue> {
    const control = this.form().controls[code];
    if (!control) {
      throw new Error(`Sen control para o campo ${code}`);
    }
    return control;
  }

  /** Leva o foco ao primeiro campo con erro (WCAG 3.3.1). Devolve se o atopou. */
  focusFirstInvalid(): boolean {
    const invalid = this.host.nativeElement.querySelector<HTMLElement>(
      '[aria-invalid="true"] input, input[aria-invalid="true"], textarea[aria-invalid="true"], select[aria-invalid="true"], button[aria-invalid="true"]',
    );
    invalid?.focus();
    return invalid !== null;
  }
}
