// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * «✕» pequeno que baleira un só campo ou parte dunha barra (o texto, o día ou o deporte do
 * buscador): 28 px de área, icona `close` de 18 px, sen fondo e na tinta suave. O nome accesible
 * é obrigatorio e di que quita («Quitar a data»). Quen o usa decide a onde vai o foco despois,
 * porque o botón desaparece co valor.
 */
@Component({
  selector: 'furar-clear-button',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <button type="button" class="f-clear-button" [attr.aria-label]="label()" (click)="pressed.emit()">
      <furar-icon name="close" />
    </button>
  `,
})
export class ClearButtonComponent {
  /** Nome accesible, xa traducido: «Quitar a data». */
  readonly label = input.required<string>();
  readonly pressed = output();
}
