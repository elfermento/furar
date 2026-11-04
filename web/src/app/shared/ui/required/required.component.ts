// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Marca de obrigatorio dentro dun rótulo (docs/deseno.md P1-3): un «*» á vista, oculto ao lector,
 * e «(obrigatorio)» só para o lector, así o nome accesible do campo segue dicíndoo. O control leva
 * ademais `aria-required="true"`.
 */
@Component({
  selector: 'furar-required-mark',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="f-required" aria-hidden="true">*</span
    ><span class="sr-only"> ({{ 'ui.form.required' | transloco }})</span>`,
})
export class RequiredMarkComponent {}

/** «* Campo obrigatorio», unha vez, enriba de cada formulario con campos obrigatorios. */
@Component({
  selector: 'furar-required-note',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-required-note' },
  template: `<span class="f-required" aria-hidden="true">*</span> {{ 'ui.form.requiredNote' | transloco }}`,
})
export class RequiredNoteComponent {}
