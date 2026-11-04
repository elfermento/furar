// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { formatDateLong, galicianFallback, isIsoDate } from '../ui/calendar/local-date';
import { isEmpty } from './custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValues } from './custom-field.types';
import { localized } from './localized';

interface ShownValue {
  readonly code: string;
  readonly label: string;
  /** Texto xa formatado, ou clave de tradución para sí/non. */
  readonly text: string;
  readonly translate: boolean;
}

/** Valores de campos personalizados en lectura (lista de definicións <dl>), tamén dos desactivados. */
@Component({
  selector: 'furar-custom-fields-view',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shown().length) {
      <dl class="gap-x-card gap-y-unit grid sm:grid-cols-[minmax(10rem,auto)_1fr]">
        @for (item of shown(); track item.code) {
          <dt class="font-bold">{{ item.label }}</dt>
          <dd class="mb-unit sm:mb-0">{{ item.translate ? (item.text | transloco) : item.text }}</dd>
        }
      </dl>
    }
  `,
})
export class CustomFieldsViewComponent {
  readonly definitions = input.required<readonly CustomFieldDefinition[]>();
  readonly values = input.required<CustomFieldValues>();
  readonly lang = input.required<string>();

  protected readonly shown = computed<ShownValue[]>(() => {
    const lang = this.lang();
    const values = this.values();
    // Tamén os desactivados: os seus valores gardados séguense vendo (ADR-0011, 2026-11-04).
    return this.definitions()
      .filter((definition) => !isEmpty(values[definition.code]))
      .map((definition) => format(definition, values[definition.code], lang));
  });
}

function optionLabel(definition: CustomFieldDefinition, code: unknown, lang: string): string {
  const option = definition.options.find((candidate) => candidate.code === code);
  return option ? localized(option.label, lang) : String(code);
}

export function format(definition: CustomFieldDefinition, value: unknown, lang: string): ShownValue {
  const base = { code: definition.code, label: localized(definition.label, lang), translate: false };
  // Sen datos `gl` en Intl (Chrome), as cifras irían co formato inglés («1,234.5»): van co de `es`.
  const locale = lang === 'es' || galicianFallback(lang) ? 'es-ES' : 'gl-ES';
  switch (definition.type) {
    case 'BOOLEAN':
      return { ...base, text: value === true ? 'common.yes' : 'common.no', translate: true };
    case 'SELECT':
      return { ...base, text: optionLabel(definition, value, lang) };
    case 'MULTI_SELECT':
      return {
        ...base,
        text: (Array.isArray(value) ? value : []).map((code) => optionLabel(definition, code, lang)).join(', '),
      };
    case 'NUMBER':
      return { ...base, text: typeof value === 'number' ? value.toLocaleString(locale) : String(value) };
    case 'DATE':
      return {
        ...base,
        text: typeof value === 'string' && isIsoDate(value) ? formatDateLong(value, lang) : String(value),
      };
    default:
      return { ...base, text: String(value) };
  }
}
