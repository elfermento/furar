// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { CdkMenu } from '@angular/cdk/menu';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';
import { MENU } from '../menu/menu.directives';
import { initials } from './initials';

export { initials };

/** Unha entrada do menú: ligazón (`href` fóra da app, `link` dentro) ou acción (`selected`). */
export interface UserMenuItem {
  readonly id: string;
  readonly label: string;
  readonly icon: IconName;
  readonly href?: string;
  readonly link?: readonly unknown[];
  /** Liña divisoria antes desta entrada. */
  readonly separatorBefore?: boolean;
  /** En negra: a entrada principal (p. ex. a da xestión, a primeira do menú). */
  readonly strong?: boolean;
}

/** Unha opción do segundo nivel (un concello): `detail` vai á dereita (o rol). */
export interface UserMenuOption {
  readonly id: string;
  readonly label: string;
  readonly detail?: string;
  /** A actual: leva ✓. */
  readonly current?: boolean;
}

/**
 * Cambio no propio menú (maqueta 41): no primeiro nivel, unha fila `label` co valor actual á
 * dereita e ›; ao activala, o mesmo panel pasa á lista de `options` cunha fila «‹ label» para
 * volver.
 */
export interface UserMenuSwitcher {
  readonly label: string;
  readonly current: string;
  readonly options: readonly UserMenuOption[];
  /** Unha ligazón ao final do segundo nivel, tras unha liña (p. ex. «Novo concello»). */
  readonly extra?: UserMenuItem & { readonly link: readonly unknown[] };
}

let nextId = 0;

/**
 * Menú da persoa con sesión (docs/deseno.md §8): botón-pastilla co nome á esquerda (só o nome, sen
 * o rol; maqueta 50) e un círculo coas iniciais á dereita, sen chevron. Abre un `[furarMenu]` (CDK, patrón APG «menu
 * button»: Intro, Espazo ou frechas abren co foco no primeiro; frechas, Inicio/Fin; Esc ou Tab
 * pechan e o foco volve ao botón) cunha cabeceira (iniciais, nome, rol · contexto e correo) e as
 * entradas que lle pasen. As accións emiten `selected` co `id`.
 *
 * Con `switcher`, un segundo nivel no mesmo panel (submenú no sitio): a fila «label ›» (p. ex.
 * «Concello ›») abre coa frecha dereita, Intro, Espazo ou clic e o foco vai á primeira fila do novo
 * nivel; «‹ label» ou a frecha esquerda volven e o foco queda na fila de partida. Escoller unha opción emite
 * `switched` co `id` e pecha. Esc, Tab ou un clic fóra pechan en calquera nivel. Só o segundo
 * nivel ten a columna da ✓ (as opcións aliñadas entre si); o primeiro, todas as filas á mesma
 * marxe. O `extra` do cambio (p. ex. «＋ Novo concello») vai ao final do segundo nivel, tras unha
 * liña.
 */
@Component({
  selector: 'furar-user-menu',
  imports: [IconComponent, RouterLink, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-usermenu' },
  template: `
    <!-- O nome é o texto do botón; no móbil, oculto á vista pero non aos lectores (só se ven as iniciais). -->
    <button
      type="button"
      class="f-usermenu__trigger"
      [furarMenuTrigger]="menu"
      [menuPosition]="position"
      (menuClosed)="level.set('main')"
    >
      <span class="f-usermenu__who">
        <span class="f-usermenu__name">{{ name() }}</span>
      </span>
      {{ ' ' }}<span class="f-usermenu__avatar" aria-hidden="true">{{ letters() }}</span>
    </button>
    <ng-template #menu>
      <div class="f-usermenu__panel">
        <div class="f-usermenu__head" [id]="headId">
          <span class="f-usermenu__avatar f-usermenu__avatar--lg" aria-hidden="true">{{ letters() }}</span>
          <span class="f-usermenu__lines">
            <span class="f-usermenu__name">{{ name() }}</span>
            @if (detail()) {
              <span class="f-usermenu__detail">{{ detail() }}</span>
            }
            @if (email()) {
              <span class="f-usermenu__detail">{{ email() }}</span>
            }
          </span>
        </div>
        <!-- Un só panel de menú (CDK): os dous niveis cambian as súas filas, non o panel. -->
        <div
          furarMenu
          class="f-usermenu__items"
          [attr.data-level]="level()"
          [attr.aria-labelledby]="level() === 'main' ? headId : null"
          [attr.aria-label]="level() === 'switch' ? switcher()?.label : null"
        >
          @if (level() === 'switch' && switcher(); as switcher) {
            <button
              type="button"
              furarMenuItemStay
              class="f-usermenu__back"
              data-item="back"
              (activated)="back()"
              (keydown.arrowleft)="back()"
            >
              <furar-icon name="chevron-left" />{{ switcher.label }}
            </button>
            @for (option of switcher.options; track option.id) {
              <button
                type="button"
                furarMenuItemRadio
                class="f-usermenu__option"
                [checked]="option.current === true"
                [attr.data-option]="option.id"
                (triggered)="switched.emit(option.id)"
                (keydown.arrowleft)="back()"
              >
                <span class="f-usermenu__option-name">{{ option.label }}</span>
                @if (option.detail) {
                  {{ ' ' }}<span class="f-usermenu__value">{{ option.detail }}</span>
                }
              </button>
            }
            @if (switcher.extra; as extra) {
              <hr role="separator" class="f-usermenu__sep" />
              <a furarMenuItem [routerLink]="extra.link" [attr.data-item]="extra.id" (keydown.arrowleft)="back()">
                <furar-icon [name]="extra.icon" />{{ extra.label }}
              </a>
            }
          } @else {
            @if (switcher(); as switcher) {
              <button
                type="button"
                furarMenuItemStay
                class="f-usermenu__switch"
                data-item="switch"
                aria-haspopup="menu"
                aria-expanded="false"
                (activated)="forward()"
                (keydown.arrowright)="forward()"
              >
                <furar-icon name="location" /><span class="f-usermenu__option-name">{{ switcher.label }}</span
                >{{ ' ' }}<span class="f-usermenu__value">{{ switcher.current }}</span>
                <furar-icon name="chevron-right" />
              </button>
              <hr role="separator" class="f-usermenu__sep" />
            }
            @for (item of items(); track item.id) {
              @if (item.separatorBefore) {
                <hr role="separator" class="f-usermenu__sep" />
              }
              @if (item.href) {
                <a furarMenuItem [href]="item.href" [attr.data-item]="item.id" [class.f-usermenu__strong]="item.strong">
                  <furar-icon [name]="item.icon" />{{ item.label }}
                </a>
              } @else if (item.link) {
                <a
                  furarMenuItem
                  [routerLink]="item.link"
                  [attr.data-item]="item.id"
                  [class.f-usermenu__strong]="item.strong"
                >
                  <furar-icon [name]="item.icon" />{{ item.label }}
                </a>
              } @else {
                <button
                  type="button"
                  furarMenuItem
                  [attr.data-item]="item.id"
                  [class.f-usermenu__strong]="item.strong"
                  (triggered)="selected.emit(item.id)"
                >
                  <furar-icon [name]="item.icon" />{{ item.label }}
                </button>
              }
            }
          }
        </div>
      </div>
    </ng-template>
  `,
})
export class UserMenuComponent {
  readonly name = input.required<string>();
  /** Rol en texto («Xestión»). */
  readonly roleName = input('');
  /** Onde ten ese rol («Concello de Narón»): vai na cabeceira despois do rol. */
  readonly context = input('');
  readonly email = input('');
  readonly items = input.required<readonly UserMenuItem[]>();
  readonly selected = output<string>();
  /** Segundo nivel (cambiar de concello); `null` = só o primeiro. */
  readonly switcher = input<UserMenuSwitcher | null>(null);
  /** A opción escollida no segundo nivel. */
  readonly switched = output<string>();

  private readonly injector = inject(Injector);
  private readonly menu = viewChild(CdkMenu);
  protected readonly level = signal<'main' | 'switch'>('main');

  protected readonly headId = `furar-user-menu-${nextId++}`;
  protected readonly letters = computed(() => initials(this.name()));
  protected readonly detail = computed(() => [this.roleName(), this.context()].filter(Boolean).join(' · '));
  protected forward(): void {
    this.go('switch');
  }

  protected back(): void {
    this.go('main');
  }

  /** Cambia o contido do panel e leva o foco á primeira fila do nivel novo (en ambos, a de cambio). */
  private go(level: 'main' | 'switch'): void {
    if (this.level() === level) return;
    this.level.set(level);
    afterNextRender(() => this.menu()?.focusFirstItem('keyboard'), { injector: this.injector });
  }

  protected readonly position: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -8 },
  ];
}
