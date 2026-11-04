// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CustomFieldDefinition, CustomFieldValues } from '../../shared/custom-fields/custom-field.types';
import { isEmpty } from '../../shared/custom-fields/custom-field-controls';
import { format } from '../../shared/custom-fields/custom-fields-view.component';
import type { ValueListItem } from '../../shared/ui/value-list/value-list.component';

/** Destino dos campos propios da reserva (ADR-0011, revisión do 2026-11-04). */
export const BOOKING_TARGET = 'BOOKING';

/** Filas dos campos propios con valor para `furar-value-list`, na orde da API. */
export function bookingFieldRows(
  definitions: readonly CustomFieldDefinition[],
  values: CustomFieldValues | undefined,
  lang: string,
  translate: (key: string) => string,
): ValueListItem[] {
  const stored = values ?? {};
  return definitions
    .filter((definition) => !isEmpty(stored[definition.code]))
    .map((definition) => {
      const shown = format(definition, stored[definition.code], lang);
      return {
        id: `cf-${shown.code}`,
        label: shown.label,
        value: shown.translate ? translate(shown.text) : shown.text,
      };
    });
}
