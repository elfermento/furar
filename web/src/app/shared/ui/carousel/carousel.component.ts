// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Directive,
  Injector,
  TemplateRef,
  afterNextRender,
  booleanAttribute,
  computed,
  contentChild,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';
import { CarouselDrag } from './carousel-drag';

let nextId = 0;

/** O que recibe o modelo de cada elemento: o elemento, a súa posición e o id do aviso. */
export interface CarouselItemContext<T> {
  readonly $implicit: T;
  readonly index: number;
  readonly total: number;
  /** Id do aviso (`hint`), para o `aria-describedby` do elemento enfocable. */
  readonly hintId: string | null;
}

/** Modelo de cada elemento: `<ng-template furarCarouselItem let-item let-i="index">`. */
@Directive({ selector: 'ng-template[furarCarouselItem]' })
export class CarouselItemDirective<T> {
  readonly template = inject<TemplateRef<CarouselItemContext<T>>>(TemplateRef);
}

/**
 * Tira horizontal de elementos (docs/deseno.md §8): desprázase co dedo, a roda ou as frechas, que
 * desaparecen nos extremos. Cada elemento píntao quen a usa cun `ng-template furarCarouselItem` (o
 * enfocable vai dentro). Con `reorderable`, ordénase **arrastrando**: mentres arrastras, as demais
 * apártanse (FLIP) para ensinar onde quedaría e, preto dun bordo ou fóra da tira, desprázase soa;
 * con teclado, Alt + ← / → move o enfocado e anúnciao. Non cambia a lista: emite `reordered` coas
 * claves na orde nova e quen a usa decide.
 *
 * `hint` é un aviso emerxente ao pasar o rato ou enfocar dentro (WCAG 1.4.13: pódese pasar o rato
 * por riba sen que desapareza, Esc péchao e non tapa o enfocado); `hintOpen` ábreo desde fóra (p.
 * ex. ao pasar pola imaxe grande).
 */
@Component({
  selector: 'furar-carousel',
  imports: [NgTemplateOutlet, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-carousel',
    '[attr.data-reorderable]': 'reorderable() ? "" : null',
    '[attr.data-dragging]': 'dragging() ? "" : null',
    '(mouseenter)': 'showHint()',
    '(mouseleave)': 'hideHint()',
    '(keydown.escape)': 'closeHint()',
  },
  templateUrl: './carousel.component.html',
})
export class CarouselComponent<T> {
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);

  /** Nome da lista («Fotos da instalación»). */
  readonly label = input.required<string>();
  readonly items = input.required<readonly T[]>();
  /** Clave estable de cada elemento (tamén o que leva `reordered`). */
  readonly key = input.required<(item: T) => string>();
  readonly reorderable = input(false, { transform: booleanAttribute });
  /**
   * A tira é parada de tabulador (para desprazala con frechas) cando os elementos non teñen nada
   * enfocable dentro (axe «scrollable-region-focusable»).
   */
  readonly scrollFocusable = input(false, { transform: booleanAttribute });
  readonly hint = input('');
  /** Abre o aviso desde fóra (ademais do rato e o foco na tira). */
  readonly hintOpen = input(false, { transform: booleanAttribute });
  readonly previousLabel = input('');
  readonly nextLabel = input('');
  /** As claves na orde nova, tras arrastrar ou Alt + frecha. */
  readonly reordered = output<string[]>();

  protected readonly template = contentChild<CarouselItemDirective<T>>(CarouselItemDirective);
  private readonly strip = viewChild.required<ElementRef<HTMLUListElement>>('strip');
  protected readonly hintId = `furar-carousel-${nextId++}-hint`;

  /** Orde provisional mentres se arrastra (claves). */
  private readonly preview = signal<readonly string[] | null>(null);
  protected readonly dragging = computed(() => this.preview() !== null);
  protected readonly shown = computed(() => {
    const order = this.preview();
    if (!order) return this.items();
    const byKey = new Map(this.items().map((item) => [this.key()(item), item]));
    return order.map((k) => byKey.get(k)).filter((item): item is T => item !== undefined);
  });

  protected readonly atStart = signal(true);
  protected readonly atEnd = signal(true);
  protected readonly announcement = signal('');
  private readonly hover = signal(false);
  private readonly focused = signal(false);
  private readonly dismissed = signal(false);
  protected readonly hintShown = computed(
    () => !this.dismissed() && !this.dragging() && (this.hover() || this.focused() || this.hintOpen()),
  );

  private hideTimer: ReturnType<typeof setTimeout> | undefined;
  private readonly drag = new CarouselDrag({
    view: this.document.defaultView,
    strip: () => this.strip().nativeElement,
    keys: () => this.items().map((item) => this.key()(item)),
    preview: this.preview,
    afterRender: (work) => afterNextRender(work, { injector: this.injector }),
    reducedMotion: () => this.reducedMotion(),
    dropped: (order, key) => {
      this.reordered.emit(order);
      this.announce(order.indexOf(key), order.length);
    },
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.measure();
      const resize = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => this.measure());
      resize?.observe(this.strip().nativeElement);
      const onDragOver = (event: DragEvent) => this.drag.over(event);
      const onDrop = (event: DragEvent) => this.drag.drop(event);
      const onDragEnd = () => this.drag.end();
      // No documento: o punteiro pode saír da tira e segue desprazando.
      this.document.addEventListener('dragover', onDragOver);
      this.document.addEventListener('drop', onDrop);
      this.document.addEventListener('dragend', onDragEnd);
      destroyRef.onDestroy(() => {
        resize?.disconnect();
        this.document.removeEventListener('dragover', onDragOver);
        this.document.removeEventListener('drop', onDrop);
        this.document.removeEventListener('dragend', onDragEnd);
        this.drag.stop();
        clearTimeout(this.hideTimer);
      });
    });
  }

  /** Pasa unha páxina (o 80 % do ancho visible) cara a un lado. */
  protected page(direction: -1 | 1): void {
    const strip = this.strip().nativeElement;
    strip.scrollBy({ left: direction * strip.clientWidth * 0.8, behavior: this.behavior() });
  }

  /** Mostra ou esconde as frechas segundo onde está a tira. */
  measure(): void {
    const strip = this.strip().nativeElement;
    this.atStart.set(strip.scrollLeft < 4);
    this.atEnd.set(strip.scrollLeft + strip.clientWidth > strip.scrollWidth - 4);
  }

  protected showHint(): void {
    clearTimeout(this.hideTimer);
    this.dismissed.set(false);
    this.hover.set(true);
  }

  protected hideHint(): void {
    clearTimeout(this.hideTimer);
    this.hideTimer = setTimeout(() => this.hover.set(false), 150);
  }

  protected closeHint(): void {
    this.dismissed.set(true);
  }

  protected focusIn(): void {
    this.dismissed.set(false);
    this.focused.set(true);
  }

  protected focusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !this.strip().nativeElement.contains(next)) this.focused.set(false);
  }

  /** Alt + ← / → move o elemento enfocado unha posición. */
  protected keydown(event: KeyboardEvent): void {
    if (!this.reorderable() || !event.altKey || (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')) return;
    const item = (event.target as HTMLElement).closest<HTMLElement>('.f-carousel__item');
    const key = item?.dataset['key'];
    if (!key) return;
    event.preventDefault();
    const keys = this.items().map((i) => this.key()(i));
    const from = keys.indexOf(key);
    const to = from + (event.key === 'ArrowLeft' ? -1 : 1);
    if (from < 0 || to < 0 || to >= keys.length) return;
    keys.splice(to, 0, ...keys.splice(from, 1));
    this.reordered.emit(keys);
    this.announce(to, keys.length);
    afterNextRender(
      () => {
        const moved = this.strip().nativeElement.querySelector<HTMLElement>(
          `.f-carousel__item[data-key="${CSS.escape(key)}"]`,
        );
        moved?.querySelector<HTMLElement>('button, a, [tabindex]')?.focus();
      },
      { injector: this.injector },
    );
  }

  protected dragStart(event: DragEvent): void {
    if (this.reorderable()) this.drag.start(event);
  }

  private announce(index: number, total: number): void {
    this.announcement.set(this.transloco.translate('ui.carousel.moved', { position: index + 1, total }));
  }

  private reducedMotion(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }

  private behavior(): ScrollBehavior {
    return this.reducedMotion() ? 'auto' : 'smooth';
  }
}
