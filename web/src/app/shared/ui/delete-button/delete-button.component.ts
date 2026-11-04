// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';

/**
 * Botón de borrar (docs/deseno.md §8): `[furarButton="danger"]` coa papeleira, redondo (só icona)
 * ou con texto. O nome accesible é obrigatorio e di que borra («Eliminar a foto 2»); se hai texto
 * visible, o nome empeza por el (WCAG 2.5.3). Non pregunta: se borra algo que non se recupera,
 * quen o usa abre antes `furar-confirm`.
 */
@Component({
  selector: 'furar-delete-button',
  imports: [ButtonDirective, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <button
      type="button"
      furarButton="danger"
      size="sm"
      [round]="!text()"
      [disabled]="disabled()"
      [attr.aria-label]="label()"
      [attr.title]="tooltip() || null"
      (click)="pressed.emit()"
    >
      <furar-icon name="trash" />
      @if (text()) {
        <span>{{ text() }}</span>
      }
    </button>
  `,
})
export class DeleteButtonComponent {
  /** Nome accesible completo: que borra. */
  readonly label = input.required<string>();
  /** Texto visible («Eliminar»); sen el, só a icona nun botón redondo. */
  readonly text = input('');
  /** Texto curto ao pasar o rato («Eliminar»), para o botón só con icona. */
  readonly tooltip = input('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly pressed = output();
}
