// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { RequiredMarkComponent } from './required/required.component';

/**
 * Envoltorio accesible para un control proxectado: etiqueta visible ligada co `for`,
 * axuda e erro con id estables (o control pon aria-describedby con describedBy()).
 * Rótulo sempre enriba, nunca flotante, e nunha liña; obrigatorio cun «*» (docs/deseno.md P1-3);
 * axuda entre o rótulo e o control, erro debaixo. Grella de catro filas (rótulo, axuda, control, erro): dentro
 * dunha `.f-field-row` usa as filas da fila (subgrid), así os controis quedan á mesma altura
 * aínda que un rótulo ou unha axuda ocupen máis. O control leva [furarInput].
 */
@Component({
  selector: 'furar-field',
  host: { class: 'f-field' },
  imports: [RequiredMarkComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="f-label" [for]="fieldId()"
      >{{ label() }}
      @if (required()) {
        <furar-required-mark />
      }
    </label>
    @if (help()) {
      <span class="f-help" [id]="fieldId() + '-help'">{{ help() }}</span>
    }
    <div class="f-field__control"><ng-content /></div>
    @if (error(); as e) {
      <span class="f-field-error" [id]="fieldId() + '-error'">{{ e.key | transloco: e.params }}</span>
    }
  `,
})
export class FieldComponent {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly help = input<string>('');
  readonly required = input(false);
  readonly error = input<ControlError | null>(null);
}
