// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive } from '@angular/core';

/** <fieldset> con aspecto de card: a <legend> é o título da card (docs/deseno.md P1-4). */
@Directive({
  selector: '[furarFieldset]',
  host: { class: 'f-fieldset' },
})
export class FieldsetDirective {}
