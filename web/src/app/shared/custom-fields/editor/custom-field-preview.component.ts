// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CardComponent } from '../../ui/card/card.component';
import { CustomFieldsFormComponent } from '../custom-fields-form.component';
import { buildCustomFieldsForm } from '../custom-field-controls';
import type { CustomFieldDefinition } from '../custom-field.types';

/** Vista previa en vivo en galego e castelán co mesmo compoñente que verá o veciño. */
@Component({
  selector: 'furar-custom-field-preview',
  imports: [CustomFieldsFormComponent, TranslocoPipe, CardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card tone="tint" [heading]="'customFields.editor.preview' | transloco">
      <p class="f-help mb-form">{{ 'customFields.editor.previewHelp' | transloco }}</p>
      <div class="gap-group grid sm:grid-cols-2 lg:grid-cols-1">
        @for (lang of languages; track lang) {
          <div>
            <!-- O título vai no idioma da interface; só o formulario leva o lang da vista previa (3.1.2). -->
            <h3 class="text-ink-soft mb-unit text-body">{{ 'lang.' + lang | transloco }}</h3>
            <furar-custom-fields-form
              [attr.lang]="lang"
              [definitions]="definitions()"
              [form]="forms()[lang]"
              [lang]="lang"
              [idPrefix]="'preview-' + lang"
            />
          </div>
        }
      </div>
    </furar-card>
  `,
})
export class CustomFieldPreviewComponent {
  readonly definition = input.required<CustomFieldDefinition>();
  protected readonly languages = ['gl', 'es'] as const;
  protected readonly definitions = computed(() => [this.definition()]);
  /** Un formulario por idioma; rexérase ao cambiar a definición. */
  protected readonly forms = computed(() => ({
    gl: buildCustomFieldsForm(this.definitions()),
    es: buildCustomFieldsForm(this.definitions()),
  }));
}
