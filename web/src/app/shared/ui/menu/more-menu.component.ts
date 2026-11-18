// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  booleanAttribute,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type { IsActiveMatchOptions } from '@angular/router';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

/** Un destino de «Máis»: texto xa traducido, icona e ruta. */
export interface MoreMenuLink {
  readonly label: string;
  readonly icon: IconName;
  readonly link: readonly string[];
  /** Cando conta como a páxina actual (`aria-current="page"`). */
  readonly options: IsActiveMatchOptions;
}

let nextId = 0;

/**
 * «Máis» dunha barra de navegación con máis destinos ca sitios (`overflow`): botón con icona e
 * texto que despregue enriba a lista das ligazóns que non caben. Patrón APG «disclosure» para
 * navegación (son ligazóns, non accións: sen `role="menu"`): o botón leva `aria-expanded` e
 * `aria-controls`, a lista é unha `<ul>` de ligazóns que se percorre co Tab; Esc pecha e devolve
 * o foco ao botón; picar fóra, saír co foco ou escoller unha ligazón pechan. A ligazón da páxina
 * actual leva `aria-current="page"` e ✓; con `active` (a páxina é dunha das entradas) o botón
 * márcase coma un destino activo e o seu nome remata en `currentLabel` («(páxina actual)»). Na
 * barra inferior abre enriba e leva icona; na superior («Concello ▾»), debaixo e cunha frecha.
 */
@Component({
  selector: 'furar-more-menu',
  imports: [RouterLink, RouterLinkActive, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-more-menu',
    '[attr.data-placement]': 'placement()',
    '(keydown.escape)': 'escape()',
    '(focusout)': 'focusOut($event)',
  },
  template: `
    <button
      #button
      type="button"
      [class]="'f-more-menu__button ' + buttonClass()"
      [attr.data-active]="active() ? '' : null"
      data-more
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="listId"
      (click)="open.set(!open())"
    >
      @if (icon(); as name) {
        <furar-icon [name]="name" [size]="24" />
      }
      {{ label() }}
      @if (active()) {
        <span class="sr-only">{{ currentLabel() }}</span>
      }
      @if (chevron()) {
        <furar-icon class="f-more-menu__chevron" name="chevron-down" />
      }
    </button>
    <ul class="f-more-menu__panel f-menu" [id]="listId" [hidden]="!open()">
      @for (item of links(); track item.label) {
        <li>
          <a
            class="f-menu__item"
            [routerLink]="item.link"
            routerLinkActive
            ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="item.options"
            (click)="open.set(false)"
          >
            <furar-icon [name]="item.icon" />
            {{ item.label }}
          </a>
        </li>
      }
    </ul>
  `,
})
export class MoreMenuComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly button = viewChild.required<ElementRef<HTMLButtonElement>>('button');

  /** «Máis», xa traducido: texto do botón. */
  readonly label = input.required<string>();
  readonly links = input.required<readonly MoreMenuLink[]>();
  /** A páxina actual é dunha das entradas. */
  readonly active = input(false);
  /** «(páxina actual)», xa traducido: remata o nome do botón con `active`. */
  readonly currentLabel = input('');
  /** Clase do botón, para que se vexa coma os outros sitios da barra. */
  readonly buttonClass = input('');
  /** Icona antes do texto («Máis» da barra inferior); `null`, sen icona. */
  readonly icon = input<IconName | null>('more');
  /** Frecha despois do texto, que xira ao abrir (o «Concello ▾» da barra superior). */
  readonly chevron = input(false, { transform: booleanAttribute });
  /** Onde abre a lista: enriba do botón (barra inferior) ou debaixo (barra superior). */
  readonly placement = input<'above' | 'below'>('above');

  protected readonly open = signal(false);
  protected readonly listId = `furar-more-menu-${nextId++}`;

  constructor() {
    const document = inject(DOCUMENT);
    // Picar fóra pecha; dentro xa o fan o botón e as ligazóns.
    const outside = (event: Event) => {
      if (this.open() && !this.host.contains(event.target as Node)) this.open.set(false);
    };
    document.addEventListener('click', outside, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('click', outside, true));
  }

  protected escape(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.button().nativeElement.focus();
  }

  /** O foco vai fóra do botón e da lista (Tab ao que segue): pecha. */
  protected focusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (next && !this.host.contains(next)) this.open.set(false);
  }
}
