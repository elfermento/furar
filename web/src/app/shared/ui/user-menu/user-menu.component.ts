// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';
import { CdkMenu } from '@angular/cdk/menu';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
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
import { ButtonDirective } from '../button/button.directive';
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
  /** `danger`: a acción que borra (eliminar a conta), en vermello e a última. */
  readonly tone?: 'danger';
}

/** Unha opción do segundo nivel (un concello): `detail` vai á dereita (o rol). */
export interface UserMenuOption {
  readonly id: string;
  readonly label: string;
  readonly detail?: string;
  /** A actual: leva ✓. */
  readonly current?: boolean;
  /** Idioma do rótulo, se non é o da páxina (p. ex. «Castellano» na web en galego). */
  readonly lang?: string;
}

/**
 * Cambio no propio menú: no primeiro nivel, unha fila `label` co valor actual á
 * dereita e ›; ao activala, o mesmo panel pasa á lista de `options` cunha fila «‹ label» para
 * volver.
 */
export interface UserMenuSwitcher {
  readonly label: string;
  readonly current: string;
  readonly options: readonly UserMenuOption[];
  /** Icona da fila do primeiro nivel (por defecto, `location`: o concello). */
  readonly icon?: IconName;
  /** Unha ligazón ao final do segundo nivel, tras unha liña (p. ex. «Novo concello»). */
  readonly extra?: UserMenuItem & { readonly link: readonly unknown[] };
}

let nextId = 0;

/** Unha fila do primeiro nivel do menú. */
type Row =
  | { readonly kind: 'separator'; readonly key: string }
  | {
      readonly kind: 'level';
      readonly key: string;
      readonly level: 'switch' | 'language';
      readonly switcher: UserMenuSwitcher;
    }
  | { readonly kind: 'item'; readonly key: string; readonly item: UserMenuItem };

const separator = (key: string): Row => ({ kind: 'separator', key });

/**
 * Menú da persoa con sesión (docs/deseno.md §8): botón-pastilla co nome á esquerda (só o nome, sen
 * o rol) e un círculo coas iniciais á dereita, sen chevron. Abre un `[furarMenu]` (CDK, patrón APG «menu
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
 *
 * Con `language` (o idioma, que non vai solto na barra), outra fila «Idioma ›»
 * co mesmo segundo nivel, xusto antes de «Pechar sesión» (ou ao final, tras unha liña); escoller
 * emite `languageSelected`. Sen `name` (sen sesión), o botón leva a icona da persoa e `guestText`
 * («Entrar») nunha pastilla; con `compact` (barra do móbil) ou sen texto, é redondo, só coa icona.
 * O nome é sempre `triggerLabel` («Entrar e idioma», que comeza polo texto visible), e o panel non
 * leva cabeceira.
 */
@Component({
  selector: 'furar-user-menu',
  imports: [ButtonDirective, IconComponent, RouterLink, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-usermenu' },
  templateUrl: './user-menu.component.html',
})
export class UserMenuComponent {
  /** Nome da persoa; baleiro, sen sesión (botón só coa icona). */
  readonly name = input.required<string>();
  /** Nome accesible do botón sen sesión («Entrar e idioma»). */
  readonly triggerLabel = input('');
  /** Texto visible do botón sen sesión («Entrar»); baleiro, só a icona. */
  readonly guestText = input('');
  /** Ancho do móbil: o botón sen sesión, redondo e só coa icona. */
  readonly compact = input(false);
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
  /** Fila «Idioma ›»; `null` = sen ela. */
  readonly language = input<UserMenuSwitcher | null>(null);
  /** O idioma escollido (o `id` da opción). */
  readonly languageSelected = output<string>();

  private readonly injector = inject(Injector);
  private readonly menu = viewChild(CdkMenu);
  private readonly menuElement = viewChild(CdkMenu, { read: ElementRef });
  protected readonly level = signal<'main' | 'switch' | 'language'>('main');
  /** O segundo nivel aberto (concello ou idioma), ou `null` no primeiro. */
  protected readonly active = computed(() => {
    const level = this.level();
    return level === 'switch' ? this.switcher() : level === 'language' ? this.language() : null;
  });
  /**
   * As filas do primeiro nivel, en orde: o cambio de concello (e unha liña), as entradas e o
   * idioma xusto antes de «Pechar sesión» (ou ao final, tras unha liña).
   */
  protected readonly rows = computed(() => {
    const rows: Row[] = [];
    const switcher = this.switcher();
    const language = this.language();
    if (switcher) rows.push({ kind: 'level', key: 'switch', level: 'switch', switcher }, separator('switch-sep'));
    const items = this.items();
    const beforeLogout = language !== null && items.some((item) => item.id === 'logout');
    for (const item of items) {
      if (item.id === 'logout' && language && beforeLogout) {
        rows.push({ kind: 'level', key: 'language', level: 'language', switcher: language });
      }
      if (item.separatorBefore) rows.push(separator(`${item.id}-sep`));
      rows.push({ kind: 'item', key: item.id, item });
    }
    if (language && !beforeLogout) {
      rows.push(separator('language-sep'), { kind: 'level', key: 'language', level: 'language', switcher: language });
    }
    return rows;
  });

  protected readonly headId = `furar-user-menu-${nextId++}`;
  protected readonly letters = computed(() => initials(this.name()));
  protected readonly detail = computed(() => [this.roleName(), this.context()].filter(Boolean).join(' · '));
  protected back(): void {
    this.go('main');
  }

  protected choose(id: string): void {
    if (this.level() === 'language') this.languageSelected.emit(id);
    else this.switched.emit(id);
  }

  /** Cambia o contido do panel e leva o foco á primeira fila do nivel novo (en ambos, a de cambio). */
  protected go(level: 'main' | 'switch' | 'language'): void {
    const from = this.level();
    if (from === level) return;
    this.level.set(level);
    afterNextRender(
      () => {
        const menu = this.menu();
        // Ao volver do idioma, o foco queda na súa fila (non é a primeira, coma a do concello).
        const rows = [...(this.menuElement()?.nativeElement.querySelectorAll('[role^="menuitem"]') ?? [])];
        const back = level === 'main' ? rows.findIndex((row) => row.getAttribute('data-item') === from) : -1;
        if (back > 0) menu?.setActiveMenuItem(back);
        else menu?.focusFirstItem('keyboard');
      },
      { injector: this.injector },
    );
  }

  protected readonly position: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -8 },
  ];
}
