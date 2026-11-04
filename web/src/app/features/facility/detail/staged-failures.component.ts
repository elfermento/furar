// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';

/** Un elemento que non se gardou: o seu nome e o que dixo a API. */
export interface StagedFailure {
  readonly name: string;
  readonly problem: ApiProblem;
}

/**
 * Aviso dunha lista do modo edición cos elementos que non se gardaron (`role="alert"`): cada un co
 * seu nome e o erro. Os cambios seguen pendentes; ao abrir o elemento, o erro vai ao seu campo.
 */
@Component({
  selector: 'furar-facility-staged-failures',
  imports: [CalloutComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="alert">
      @if (failures().length) {
        <furar-callout tone="danger" [heading]="'facility.detail.edit.failed' | transloco">
          <ul class="gap-unit grid">
            @for (failure of failures(); track $index) {
              <li>
                <span class="font-bold">{{ failure.name }}:</span>
                {{ failure.problem.messageKey | transloco: failure.problem.params }}
              </li>
            }
          </ul>
        </furar-callout>
      }
    </div>
  `,
})
export class StagedFailuresComponent {
  readonly failures = input<readonly StagedFailure[]>([]);
}
