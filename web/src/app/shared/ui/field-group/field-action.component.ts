// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * A celda dun botón ao final dunha fila de campos (`.f-field-cells` ou `furar-field-group cells`):
 * p. ex. a papeleira dun tramo ou dunha parte, dentro do propio grupo, á dereita, co
 * alto da fila e sen liña divisoria. Só se pon cando hai botón: sen ela, os campos enchen a fila.
 *
 * ```html
 * <div class="f-field-cells">
 *   <furar-field …>…</furar-field>
 *   <furar-field-action><furar-delete-button … /></furar-field-action>
 * </div>
 * ```
 */
@Component({
  selector: 'furar-field-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-field-action' },
  template: '<ng-content />',
})
export class FieldActionComponent {}
