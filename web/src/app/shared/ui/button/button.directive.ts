// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive, booleanAttribute, input } from '@angular/core';
import { ActionDirective } from '../action/action.directive';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'quiet';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Aspecto de botón sobre <button> ou <a> nativos (docs/deseno.md P1-2). Unha acción `primary`
 * por pantalla. Uso: <button type="submit" furarButton="primary" [busy]="saving()">…</button>
 *
 * Leva `furarAction` (accións en curso, docs/deseno.md §8): `busy` fai deste botón a acción en
 * curso do seu contexto (a páxina, a capa ou `[furarActionScope]`) e, mentres dura, este e os
 * demais botóns do contexto quedan con `aria-disabled` e sen efecto ao premelos; o foco non se
 * move. O rótulo en xerundio pono quen chama. `unavailable`: bloqueado por outro motivo.
 *
 * `round`: só icona, redondo de 44 px; leva sempre `aria-label` (sen texto non ten nome). Para
 * borrar, `furar-delete-button` (papeleira en `danger`, co nome obrigatorio).
 */
@Directive({
  selector: '[furarButton]',
  hostDirectives: [{ directive: ActionDirective, inputs: ['busy', 'unavailable'] }],
  host: {
    class: 'f-button',
    '[attr.data-variant]': 'variant()',
    '[attr.data-size]': 'size()',
    '[attr.data-block]': 'block() ? "true" : null',
    '[attr.data-round]': 'round() ? "true" : null',
  },
})
export class ButtonDirective {
  readonly variant = input<ButtonVariant, ButtonVariant | ''>('secondary', {
    alias: 'furarButton',
    transform: (value: ButtonVariant | '') => value || 'secondary',
  });
  readonly size = input<ButtonSize>('md');
  /** Ocupa o ancho no móbil (< 640 px). */
  readonly block = input(false, { transform: booleanAttribute });
  /** Redondo e só coa icona (o nome vai en `aria-label`). */
  readonly round = input(false, { transform: booleanAttribute });
}
