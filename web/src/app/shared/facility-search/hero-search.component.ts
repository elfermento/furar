// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  contentChild,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { mediaMatches } from '../../core/layout/language-menu';
import { ButtonDirective } from '../ui/button/button.directive';
import { IconComponent } from '../ui/icon/icon.component';
import { reducedMotion } from '../ui/motion/reduced-motion';
import { FacilitySearchComponent } from './facility-search.component';

export { HeroArtDirective } from './hero-art.directive';

/** Baixando máis ca isto (px) desde arriba, a barra recóllese na pílula «Buscar». */
const COLLAPSE_FROM = 160;
/** O que hai que baixar dunha vez para que conte coma «baixar» (non un tremer do dedo). */
const SCROLL_SLOP = 8;

/**
 * A barra de busca das portadas (`furar-facility-search`, proxectada): escondida ata que se pulsa
 * «Buscar» na cabeceira (`open`) ou hai filtros (`pinned`); despois, fixa arriba ata deixar a
 * páxina (home.css, `.f-hero__search`).
 *
 * No escritorio, sae desde o botón ata o seu sitio (FLIP: transform e opacidade, 280 ms). Ao
 * baixar pola páxina recóllese na pílula «Buscar» arriba no medio, co número de filtros postos
 * (`count`); subir non a abre: só picar a pílula, que a estende outra vez desde ela, co foco no
 * texto. Con menos movemento, sen animación.
 */
@Component({
  selector: 'furar-hero-search',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-hero__search',
    '[attr.data-open]': 'shown() || null',
    '[attr.data-collapsed]': 'collapsed() || null',
  },
  template: `
    @if (collapsed()) {
      <button
        type="button"
        furarButton="primary"
        size="lg"
        class="f-hero__pill"
        data-search-pill
        [attr.aria-label]="
          count() ? ('home.search.pill' | transloco: { count: count() }) : ('home.search.submit' | transloco)
        "
        (click)="expand($event)"
      >
        <furar-icon name="search" />
        {{ 'home.search.submit' | transloco }}
        @if (count()) {
          <span class="f-search__count" aria-hidden="true">{{ count() }}</span>
        }
      </button>
    }
    <div #bar class="f-hero__bar"><ng-content /></div>
  `,
})
export class HeroSearchComponent {
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly view = inject(DOCUMENT).defaultView;
  private readonly bar = viewChild.required<ElementRef<HTMLElement>>('bar');
  private readonly search = contentChild(FacilitySearchComponent);

  /** Hai filtros: a barra está fóra (e queda aínda que se quiten). */
  readonly pinned = input(false);
  /** Os filtros postos: o número da pílula. */
  readonly count = input(0);
  /** Escritorio (≥ 640 px): o botón «Buscar» grande, a barra sae desde el e recóllese ao baixar. */
  readonly wide = mediaMatches(this.view, '(min-width: 640px)', inject(DestroyRef));

  private readonly opened = signal(false);
  /** A barra está fóra (fixa arriba). */
  readonly shown = computed(() => this.opened() || this.pinned());
  /** Recollida na pílula (escritorio, ao baixar). */
  protected readonly collapsed = signal(false);
  private lastY = 0;

  constructor() {
    effect(() => {
      if (this.pinned()) untracked(() => this.opened.set(true));
    });
    const onScroll = () => this.onScroll();
    this.view?.addEventListener('scroll', onScroll, { passive: true });
    inject(DestroyRef).onDestroy(() => this.view?.removeEventListener('scroll', onScroll));
  }

  /** «Buscar» da cabeceira (o seu `click`): a barra sae desde o botón, co foco no texto. */
  open(event?: Event): void {
    const origin = event?.currentTarget as HTMLElement | null | undefined;
    const from = this.shown() && !this.collapsed() ? undefined : origin?.getBoundingClientRect();
    this.opened.set(true);
    this.collapsed.set(false);
    this.reveal(from);
  }

  /** A pílula: a barra volve estenderse desde ela. */
  protected expand(event: Event): void {
    const from = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.collapsed.set(false);
    this.reveal(from);
  }

  private reveal(from: DOMRect | undefined): void {
    this.lastY = this.view?.scrollY ?? 0;
    afterNextRender(
      () => {
        const bar = this.bar().nativeElement;
        this.search()?.focus();
        if (!from || !this.wide() || reducedMotion(this.view) || !bar.animate) return;
        const to = bar.getBoundingClientRect();
        if (!to.width || !to.height) return;
        const start = `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${from.width / to.width}, ${from.height / to.height})`;
        bar.animate(
          [
            { transform: start, opacity: 0.4 },
            { transform: 'none', opacity: 1 },
          ],
          { duration: 280, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
        );
      },
      { injector: this.injector },
    );
  }

  /** Baixar recolle a barra na pílula (non se o foco está nela: alguén está escribindo ou escollendo). */
  private onScroll(): void {
    const y = this.view?.scrollY ?? 0;
    if (Math.abs(y - this.lastY) < SCROLL_SLOP) return;
    const down = y > this.lastY;
    this.lastY = y;
    if (!down || y < COLLAPSE_FROM || !this.shown() || this.collapsed() || !this.wide()) return;
    if (this.host.contains(this.host.ownerDocument.activeElement)) return;
    this.collapsed.set(true);
  }
}
