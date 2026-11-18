// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, Directive, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

/**
 * Lista de accións dun detalle: unha por fila, en caixa con bordo, coa icona, o
 * rótulo e o chevron. A destrutiva (`tone="danger"`, en vermello) vai a última. Sobre `<ul>`: cada
 * `<li>` leva un `<furar-action-item>` co seu `<button>` ou `<a>` dentro, que se estira sobre a
 * fila enteira; o nome da lista (`aria-label`) pono quen chama.
 *
 * ```html
 * <ul furarActionList [attr.aria-label]="'Accións'">
 *   <li><furar-action-item icon="edit-calendar"><a [routerLink]="…">Cambiar</a></furar-action-item></li>
 *   <li>
 *     <furar-action-item icon="event-busy" tone="danger">
 *       <button type="button" (click)="…">Cancelar</button>
 *     </furar-action-item>
 *   </li>
 * </ul>
 * ```
 *
 * Estilos en `styles/components/action-list.css`.
 */
@Directive({
  selector: '[furarActionList]',
  host: { class: 'f-action-list' },
})
export class ActionListDirective {}

/** Unha acción da lista: a icona, o botón ou a ligazón (o contido) e o chevron. */
@Component({
  selector: 'furar-action-item',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-action-item', '[attr.data-tone]': 'tone()' },
  template: `
    @if (icon(); as name) {
      <furar-icon class="f-action-item__icon" [name]="name" />
    }
    <span class="f-action-item__label"><ng-content /></span>
    <furar-icon class="f-action-item__chevron" name="chevron-right" />
  `,
})
export class ActionItemComponent {
  readonly icon = input<IconName | null>(null);
  /** `danger`: a destrutiva (cancelar, deixar de esperar), en vermello. */
  readonly tone = input<'danger' | null>(null);
}
