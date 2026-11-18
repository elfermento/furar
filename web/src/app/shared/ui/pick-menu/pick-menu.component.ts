// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';
import { MENU } from '../menu/menu.directives';

/** Unha opción: o nome á esquerda e `detail` á dereita (p. ex. o concello e o rol). */
export interface PickMenuOption {
  readonly id: string;
  readonly label: string;
  readonly detail?: string;
}

/** O que precisa a folla do móbil (`openPickSheet`): o título, as opcións e a quen volve o foco. */
export interface PickSheetRequest {
  readonly heading: string;
  readonly options: readonly PickMenuOption[];
  readonly from: HTMLElement;
}

/**
 * Botón que escolle unha opción dunha lista curta (docs/deseno.md §8): `furarButton` secundario
 * pequeno coa icona, o rótulo e a frecha. No escritorio abre un `[furarMenu]` debaixo, á dereita
 * (CDK, patrón APG «menu button»: Esc, Tab ou clic fóra pechan e o foco volve ao botón); escoller
 * emite `picked`. Con `sheet` (o móbil) non abre menú: emite `sheetRequested` e quen o usa abre a
 * folla desde abaixo (`openPickSheet`, que se carga á parte), que devolve a opción escollida.
 * Por baixo de 360 px o rótulo queda só para os lectores de pantalla (icona e frecha).
 */
@Component({
  selector: 'furar-pick-menu',
  imports: [ButtonDirective, IconComponent, NgTemplateOutlet, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-pick-menu' },
  template: `
    @if (sheet()) {
      <button
        #trigger
        type="button"
        furarButton
        size="sm"
        class="f-pick-menu__trigger"
        aria-haspopup="dialog"
        (click)="sheetRequested.emit({ heading: label(), options: options(), from: trigger })"
      >
        <ng-container [ngTemplateOutlet]="face" />
      </button>
    } @else {
      <button
        type="button"
        furarButton
        size="sm"
        class="f-pick-menu__trigger"
        [furarMenuTrigger]="menu"
        [menuPosition]="position"
      >
        <ng-container [ngTemplateOutlet]="face" />
      </button>
    }
    <ng-template #face>
      @if (icon(); as name) {
        <furar-icon class="f-pick-menu__icon" [name]="name" />
      }
      <span class="f-pick-menu__label">{{ label() }}</span>
      <furar-icon name="chevron-down" />
    </ng-template>
    <ng-template #menu>
      <div furarMenu class="f-pick-menu__menu" [attr.aria-label]="label()">
        @for (option of options(); track option.id) {
          <button type="button" furarMenuItem [attr.data-option]="option.id" (triggered)="picked.emit(option.id)">
            <span class="f-usermenu__option-name">{{ option.label }}</span>
            @if (option.detail) {
              {{ ' ' }}<span class="f-usermenu__value">{{ option.detail }}</span>
            }
          </button>
        }
      </div>
    </ng-template>
  `,
})
export class PickMenuComponent {
  /** O rótulo do botón e o nome do menú e da folla («Concello»). */
  readonly label = input.required<string>();
  readonly options = input.required<readonly PickMenuOption[]>();
  readonly icon = input<IconName | null>(null);
  /** Móbil: a lista vai nunha folla desde abaixo, que abre quen o usa (`sheetRequested`). */
  readonly sheet = input(false, { transform: booleanAttribute });
  /** A opción escollida no menú do escritorio. */
  readonly picked = output<string>();
  /** No móbil, abrir a folla con isto (`openPickSheet`). */
  readonly sheetRequested = output<PickSheetRequest>();

  /** Debaixo do botón, aliñado á súa dereita; se non cabe, á esquerda. */
  protected readonly position: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
  ];
}
