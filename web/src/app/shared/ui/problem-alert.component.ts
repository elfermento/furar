// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { CalloutComponent } from './callout/callout.component';

/**
 * Erro da API traducido ao idioma activo (o `code` RFC 9457 → errors.<code>).
 * role="alert": lector de pantalla anúncialo ao aparecer. Leva o traceId para soporte. Baleiro
 * (`data-empty`), sae do fluxo: o role="alert" segue no DOM, pero non deixa oco nunha grella.
 */
@Component({
  selector: 'furar-problem-alert',
  imports: [TranslocoPipe, CalloutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-alert-slot', '[attr.data-empty]': 'problem() ? null : ""' },
  template: `
    <div role="alert">
      @if (problem(); as p) {
        <furar-callout class="mb-group" tone="danger">
          <p class="font-bold">{{ p.messageKey | transloco: p.params }}</p>
          @if (p.traceId) {
            <p class="text-muted">{{ 'errors.reference' | transloco: { id: p.traceId } }}</p>
          }
        </furar-callout>
      }
    </div>
  `,
})
export class ProblemAlertComponent {
  readonly problem = input<ApiProblem | null>(null);
}
