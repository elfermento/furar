// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';

/**
 * Desactivar ou activar algo que se conserva (un campo personalizado, unha opción): botón redondo só
 * con icona, o ollo tachado mentres está activo («Desactivar») e o ollo cando non («Activar»). O
 * nome accesible (`label`, «Desactivar o campo Nivel») e o texto ao pasar o rato (`tooltip`,
 * «Desactivar») pásaos quen o usa, xa traducidos.
 */
@Component({
  selector: 'furar-active-toggle-button',
  imports: [ButtonDirective, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <button
      type="button"
      furarButton
      size="sm"
      round
      [busy]="busy()"
      [attr.aria-label]="label()"
      [attr.title]="tooltip() || null"
      (click)="pressed.emit()"
    >
      <furar-icon [name]="active() ? 'visibility-off' : 'visibility'" />
    </button>
  `,
})
export class ActiveToggleButtonComponent {
  /** Está activo: o botón desactiva. */
  readonly active = input.required<boolean>();
  /** Nome accesible completo: que fai e con que. */
  readonly label = input.required<string>();
  readonly tooltip = input('');
  readonly busy = input(false, { transform: booleanAttribute });
  readonly pressed = output();
}
