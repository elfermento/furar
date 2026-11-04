// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkMenu, CdkMenuItem, CdkMenuItemRadio, CdkMenuTrigger } from '@angular/cdk/menu';
import { Directive, inject, output } from '@angular/core';

/**
 * Menú despregable (D1-05a; docs/deseno.md §8) sobre CDK Menu, co patrón APG «menu button»:
 *
 * ```html
 * <button type="button" [furarMenuTrigger]="menu">Accións</button>
 * <ng-template #menu>
 *   <div furarMenu>
 *     <button type="button" furarMenuItemRadio [checked]="x === 'a'" (triggered)="pick('a')">A</button>
 *   </div>
 * </ng-template>
 * ```
 *
 * Teclado (CDK): no botón, Intro, Espazo ou frechas abren co foco no primeiro elemento; dentro,
 * frechas, Inicio/Fin e a primeira letra moven; Intro ou Espazo activan e pechan; Esc ou Tab pechan
 * e o foco volve ao botón. O botón leva `aria-haspopup="menu"` e `aria-expanded`.
 */
@Directive({
  selector: '[furarMenuTrigger]',
  hostDirectives: [
    {
      directive: CdkMenuTrigger,
      inputs: ['cdkMenuTriggerFor: furarMenuTrigger', 'cdkMenuPosition: menuPosition', 'cdkMenuTriggerData: menuData'],
      outputs: ['cdkMenuOpened: menuOpened', 'cdkMenuClosed: menuClosed'],
    },
  ],
})
export class MenuTriggerDirective {}

/**
 * Panel do menú (`role="menu"`): vai dentro do `<ng-template>` que abre `[furarMenuTrigger]`.
 * Esc pecha só o menú: non sobe ao documento, onde a capa de debaixo (un modal do CDK, que escoita
 * as teclas da última capa con oínte) tamén se pecharía.
 */
@Directive({
  selector: '[furarMenu]',
  hostDirectives: [CdkMenu],
  host: { class: 'f-menu', '(keydown)': 'keepEscape($event)' },
})
export class MenuDirective {
  protected keepEscape(event: KeyboardEvent): void {
    if (event.key === 'Escape') event.stopPropagation();
  }
}

/** Acción do menú (`role="menuitem"`), sobre un `<button type="button">`. */
@Directive({
  selector: '[furarMenuItem]',
  hostDirectives: [
    {
      directive: CdkMenuItem,
      inputs: ['cdkMenuItemDisabled: disabled'],
      outputs: ['cdkMenuItemTriggered: triggered'],
    },
  ],
  host: { class: 'f-menu__item' },
})
export class MenuItemDirective {}

/**
 * Opción única do menú (`role="menuitemradio"` con `aria-checked`), para escoller un valor: a
 * marcada leva ✓ e negra ademais do tinte (non só cor, 1.4.1). Quen usa o menú decide que pasa en
 * `triggered` e actualiza `checked`.
 */
@Directive({
  selector: '[furarMenuItemRadio]',
  hostDirectives: [
    {
      directive: CdkMenuItemRadio,
      inputs: ['cdkMenuItemChecked: checked', 'cdkMenuItemDisabled: disabled'],
      outputs: ['cdkMenuItemTriggered: triggered'],
    },
  ],
  host: { class: 'f-menu__item' },
})
export class MenuItemRadioDirective {}

/**
 * Entrada que cambia o contido do mesmo menú sen pechalo (un submenú no sitio: «Concello ›» e
 * «‹ Concello» do menú da persoa). É un `menuitem` máis para as frechas e a primeira letra, pero ao
 * activala (clic, Intro ou Espazo) emite `activated` e o menú segue aberto: quen a usa cambia as
 * entradas e move o foco.
 */
@Directive({
  selector: '[furarMenuItemStay]',
  hostDirectives: [{ directive: CdkMenuItem, inputs: ['cdkMenuItemDisabled: disabled'] }],
  host: { class: 'f-menu__item' },
})
export class MenuItemStayDirective {
  readonly activated = output();

  constructor() {
    const item = inject(CdkMenuItem);
    // O CDK pecha todo o menú ao activar unha entrada sen submenú; esta non.
    item.trigger = () => {
      if (!item.disabled) this.activated.emit();
    };
  }
}

/** As pezas do menú, para `imports`. */
export const MENU = [
  MenuTriggerDirective,
  MenuDirective,
  MenuItemDirective,
  MenuItemRadioDirective,
  MenuItemStayDirective,
] as const;
