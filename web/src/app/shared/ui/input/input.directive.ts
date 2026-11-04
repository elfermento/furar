// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive } from '@angular/core';

/**
 * Aspecto de campo sobre <input> e <textarea> nativos (docs/deseno.md P1-3): 48 px, bordo de
 * 2 px con 3:1, aria-invalid="true" pinta o bordo de erro. O marcado (id, aria-*) é de quen chama.
 * Para escoller dunha lista, furar-select (non o <select> nativo).
 */
@Directive({
  selector: '[furarInput]',
  host: { class: 'f-input' },
})
export class InputDirective {}
