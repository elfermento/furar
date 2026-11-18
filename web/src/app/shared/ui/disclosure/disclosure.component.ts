// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

let nextId = 0;

/**
 * Pregable (patrón «disclosure» das prácticas ARIA): un botón co título, `aria-expanded` e
 * `aria-controls`, a frecha á dereita e o contido debaixo. Pechado por defecto e independente dos
 * demais; sen animación con `prefers-reduced-motion`. Estilos en `styles/components/disclosure.css`.
 *
 * ```html
 * <furar-disclosure [heading]="'Detalles'">…</furar-disclosure>
 * ```
 */
@Component({
  selector: 'furar-disclosure',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-disclosure', '[attr.data-open]': "open() ? '' : null" },
  template: `
    <button
      type="button"
      class="f-disclosure__toggle"
      [id]="buttonId"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="panelId"
      (click)="open.set(!open())"
    >
      <span>{{ heading() }}</span>
      <furar-icon name="chevron-down" class="f-disclosure__chevron" />
    </button>
    <div class="f-disclosure__panel" [id]="panelId" [hidden]="!open()">
      <ng-content />
    </div>
  `,
})
export class DisclosureComponent {
  /** O título do botón. */
  readonly heading = input.required<string>();
  /** Aberto ou pechado (pechado por defecto). */
  readonly open = model(false);

  private readonly id = nextId++;
  protected readonly buttonId = `furar-disclosure-${this.id}`;
  protected readonly panelId = `furar-disclosure-${this.id}-panel`;
}
