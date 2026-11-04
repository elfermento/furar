// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Barra de accións fixa ao pé (docs/deseno.md P1-12): cristal co seu fallback (§6) e o mesmo
 * ancho máximo ca a barra superior. Á esquerda, un estado opcional (`[actionBarStatus]`, p. ex.
 * «Cambios sen gardar»); á dereita, os botóns (o principal, o último). É unha rexión con nome para
 * que o lector a atope; no móbil vai enriba da barra inferior. Úsana o modo edición da ficha e,
 * co dominio 2, a reserva.
 */
@Component({
  selector: 'furar-action-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-action-bar' },
  template: `
    <div class="f-action-bar__bar f-glass" role="region" [attr.aria-label]="label()">
      <div class="f-action-bar__status">
        <ng-content select="[actionBarStatus]" />
      </div>
      <div class="f-action-bar__actions">
        <ng-content />
      </div>
    </div>
  `,
})
export class ActionBarComponent {
  /** Nome da rexión («Edición da instalación»). */
  readonly label = input.required<string>();
}
