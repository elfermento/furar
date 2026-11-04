// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

/**
 * Fila dunha lista (docs/deseno.md P1-7): rótulo, detalle (contido proxectado), valor
 * (`rowValue`) e, ou ben `link` (a fila enteira é a ligazón, estirada, con frecha), ou ben
 * accións (`rowActions`, botóns ao final; a fila deixa de ser ligazón). Nunca as dúas.
 */
@Component({
  selector: 'furar-row',
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-row">
      @if (icon(); as name) {
        <furar-icon class="f-row__icon" [name]="name" [size]="24" />
      }
      <div class="f-row__main">
        @if (link(); as commands) {
          <a class="f-row__label f-row__link" [routerLink]="commands">{{ label() }}</a>
        } @else {
          <p class="f-row__label">{{ label() }}</p>
        }
        <div class="f-row__detail"><ng-content /></div>
      </div>
      <div class="f-row__value"><ng-content select="[rowValue]" /></div>
      <div class="f-row__actions"><ng-content select="[rowActions]" /></div>
      @if (link()) {
        <furar-icon class="f-row__chevron" name="chevron-right" />
      }
    </div>
  `,
})
export class RowComponent {
  readonly label = input.required<string>();
  readonly link = input<string | readonly unknown[] | null>(null);
  readonly icon = input<IconName | null>(null);
}
