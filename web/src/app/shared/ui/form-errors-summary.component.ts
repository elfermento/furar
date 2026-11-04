// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CalloutComponent } from './callout/callout.component';

/** Aviso de formulario con erros, anunciado ao enviar (WCAG 3.3.1). */
@Component({
  selector: 'furar-form-errors-summary',
  imports: [TranslocoPipe, CalloutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="alert">
      @if (count() > 0) {
        <furar-callout class="mb-group" tone="danger">
          <p class="font-bold">{{ 'forms.errorsSummary' | transloco: { count: count() } }}</p>
        </furar-callout>
      }
    </div>
  `,
})
export class FormErrorsSummaryComponent {
  readonly count = input(0);
}
