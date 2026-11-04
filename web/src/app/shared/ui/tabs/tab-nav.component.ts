// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  afterRenderEffect,
  contentChildren,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLinkActive } from '@angular/router';
import { HORIZONTAL, rovingIndex } from '../roving-index';
import { TabStripDirective } from './tab-strip.directive';

/**
 * Pestanas que son páxinas (docs/deseno.md, pestanas): un `nav` de ligazóns, cada unha co seu URL,
 * e `aria-current="page"` na da páxina á vista (`routerLinkActive` con `ariaCurrentWhenActive`).
 * O mesmo aspecto ca `furar-tabs` en liña (`look="line"`): sen título enriba, texto en negra
 * suave e a activa en tinta cun subliñado `primary`. Nunha soa fila: se non caben, desprázanse
 * dentro da franxa (`furarTabStrip`), con fundido no bordo con máis pestanas e a activa á vista.
 *
 * Teclado: cada ligazón é unha parada de tabulador (son ligazóns); ademais, as frechas esquerda e
 * dereita moven o foco dando a volta, e Inicio e Fin van aos extremos, sen navegar (Intro navega).
 * Ao navegar dentro das pestanas o foco queda na pestana (`data-keep-focus`, ShellComponent).
 *
 * ```html
 * <furar-tab-nav [label]="'Concello'">
 *   <a furarTabLink routerLink="xeral" routerLinkActive ariaCurrentWhenActive="page">Xeral</a>
 * </furar-tab-nav>
 * ```
 */
@Component({
  selector: 'furar-tab-nav',
  imports: [TabStripDirective],
  host: {
    class: 'f-tabs',
    'data-look': 'line',
    '[attr.data-size]': 'size()',
    '(keydown)': 'onKeydown($event)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="f-tabs__bar" data-keep-focus [attr.aria-label]="label()">
      <div class="f-tabs__list" furarTabStrip>
        <ng-content />
      </div>
    </nav>
  `,
})
export class TabNavComponent {
  private readonly links = contentChildren(TabLinkDirective);
  private readonly strip = viewChild.required(TabStripDirective);

  /** Nome do `nav` («Concello», «Páxinas legais»). */
  readonly label = input.required<string>();
  /** `sm`: pestanas dentro doutra sección (máis baixas e un chisco máis pequenas). */
  readonly size = input<'md' | 'sm'>('md');

  constructor() {
    // Cando `routerLinkActive` marca a pestana activa (tamén na primeira carga, que pode chegar
    // despois do primeiro pintado), despois de pintar: ao centro da franxa.
    afterRenderEffect(() => this.strip().reveal(this.links().find((link) => link.active())?.element));
  }

  protected onKeydown(event: KeyboardEvent): void {
    const links = this.links().map((link) => link.element);
    const index = links.indexOf(event.target as HTMLAnchorElement);
    const target = links[rovingIndex(event.key, index, links.length, HORIZONTAL) ?? -1];
    if (!target) return;
    event.preventDefault();
    target.focus();
    target.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }
}

/** Unha pestana de `furar-tab-nav`: `<a furarTabLink routerLink="…" routerLinkActive ariaCurrentWhenActive="page">`. */
@Directive({
  selector: 'a[furarTabLink]',
  host: { class: 'f-tabs__tab' },
})
export class TabLinkDirective {
  readonly element = inject<ElementRef<HTMLAnchorElement>>(ElementRef).nativeElement;
  private readonly isActive = signal(false);
  /** É a pestana da páxina á vista (o que di o seu `routerLinkActive`). */
  readonly active = this.isActive.asReadonly();

  constructor() {
    inject(RouterLinkActive, { self: true, optional: true })
      ?.isActiveChange.pipe(takeUntilDestroyed())
      .subscribe((active) => this.isActive.set(active));
  }
}
