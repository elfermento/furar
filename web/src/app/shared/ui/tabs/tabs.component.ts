// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  afterRenderEffect,
  computed,
  contentChildren,
  inject,
  input,
  model,
  viewChild,
} from '@angular/core';
import { HORIZONTAL, rovingIndex } from '../roving-index';
import { TabStripDirective } from './tab-strip.directive';

let nextId = 0;

/**
 * Pestanas (plan do dominio 1, D1-05c; APG Tabs): un `tablist` con `<button [furarTab]="valor">`
 * e un único `tabpanel` co contido que proxecta quen o usa (o da pestana escollida). As accións
 * («+ Tempada») van fóra do `tablist`, no oco `[furarTabsActions]`, na mesma liña.
 *
 * Teclado: o `tablist` é unha soa parada de tabulador (a pestana escollida); frechas esquerda e
 * dereita moven dando a volta, Inicio e Fin van á primeira e á última. Por defecto a activación
 * é automática (a frecha xa escolle); con `activation="manual"` a frecha só move o foco e escolle
 * Intro ou Espazo, para cando cambiar de pestana custa (cambios sen gardar).
 *
 * Aspecto (docs/deseno.md, pestanas): `look="pill"` (por defecto), cápsulas; `look="line"`, o das
 * seccións (texto en negra suave e a escollida en tinta cun subliñado `primary`, igual ca
 * `furar-tab-nav`), con `size="sm"` para as que van dentro doutra sección. As pestanas van nunha
 * soa fila: se non caben, desprázanse dentro da súa franxa (`furarTabStrip`), con fundido no
 * bordo con máis pestanas e a escollida á vista; a páxina nunca se despraza de lado.
 */
@Component({
  selector: 'furar-tabs',
  imports: [TabStripDirective],
  host: {
    class: 'f-tabs',
    '[attr.data-look]': 'look()',
    '[attr.data-size]': 'size()',
    '(keydown)': 'onKeydown($event)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-tabs__bar">
      <div role="tablist" class="f-tabs__list" furarTabStrip [attr.aria-label]="label()" [hidden]="!tabs().length">
        <ng-content select="[furarTab]" />
      </div>
      <ng-content select="[furarTabsActions]" />
    </div>
    <div
      class="f-tabs__panel"
      [id]="panelId"
      [attr.role]="current() ? 'tabpanel' : null"
      [attr.aria-labelledby]="current()?.id ?? null"
    >
      <ng-content />
    </div>
  `,
})
export class TabsComponent<T = unknown> {
  protected readonly tabs = contentChildren<TabDirective<T>>(TabDirective);

  /** Nome do `tablist` («Tempadas»). */
  readonly label = input.required<string>();
  /** Valor da pestana escollida; quen usa o compoñente debe escoller unha. */
  readonly selected = model<T | null>(null);
  readonly activation = input<'automatic' | 'manual'>('automatic');
  readonly look = input<'pill' | 'line'>('pill');
  readonly size = input<'md' | 'sm'>('md');
  private readonly strip = viewChild.required(TabStripDirective);

  readonly panelId = `furar-tabs-${nextId++}-panel`;
  /** A pestana escollida, ou `null` se `selected` non casa con ningunha. */
  readonly current = computed(() => this.tabs().find((tab) => tab.value() === this.selected()) ?? null);
  /** A que recibe o tabulador: a escollida ou, se non hai, a primeira. */
  readonly tabStop = computed(() => this.current() ?? this.tabs()[0] ?? null);

  constructor() {
    // A escollida, á vista dentro da franxa (sen mover a páxina).
    afterRenderEffect(() => this.strip().reveal(this.current()?.element.nativeElement));
  }

  select(tab: TabDirective<T>): void {
    this.selected.set(tab.value());
  }

  protected onKeydown(event: KeyboardEvent): void {
    const tabs = this.tabs();
    const current = tabs.findIndex((tab) => tab.element.nativeElement === event.target);
    const target = tabs[rovingIndex(event.key, current, tabs.length, HORIZONTAL) ?? -1];
    if (!target) return;
    event.preventDefault();
    target.element.nativeElement.focus();
    if (this.activation() === 'automatic') this.select(target);
  }
}

/** Unha pestana de `furar-tabs`: `<button type="button" [furarTab]="season.id">Inverno</button>`. */
@Directive({
  selector: 'button[furarTab]',
  host: {
    role: 'tab',
    class: 'f-tabs__tab',
    '[id]': 'id',
    '[attr.aria-selected]': 'tabs.current() === this',
    '[attr.aria-controls]': 'tabs.panelId',
    '[attr.tabindex]': 'tabs.tabStop() === this ? 0 : -1',
    '(click)': 'tabs.select(this)',
  },
})
export class TabDirective<T = unknown> {
  protected readonly tabs = inject<TabsComponent<T>>(TabsComponent);
  readonly element = inject<ElementRef<HTMLButtonElement>>(ElementRef);
  readonly id = `furar-tab-${nextId++}`;
  /** Valor da pestana (obrigatorio; `null` só mentres Angular non fixa as entradas). */
  readonly value = input<T | null>(null, { alias: 'furarTab' });
}
