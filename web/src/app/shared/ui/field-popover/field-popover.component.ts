// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ConnectedPosition, OverlayRef } from '@angular/cdk/overlay';
import {
  createBlockScrollStrategy,
  createFlexibleConnectedPositionStrategy,
  createGlobalPositionStrategy,
  createOverlayRef,
  createRepositionScrollStrategy,
} from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import type { ElementRef, TemplateRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  ViewContainerRef,
  afterNextRender,
  booleanAttribute,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';
import { RequiredMarkComponent } from '../required/required.component';

let nextId = 0;

/** Por baixo deste ancho o panel é unha folla desde abaixo (o mesmo corte ca o resto, 640 px). */
const SHEET_QUERY = '(max-width: 39.99rem)';

/** Debaixo do campo e, se non cabe, enriba; co ancho do campo como mínimo. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
];

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]';

/**
 * Campo-resumo que abre un panel (plan do dominio 2, D2-10; maquetas 11 e 21): un botón co rótulo
 * pequeno e o valor en texto («Data · Mércores 28») que abre en CDK Overlay un diálogo co contido
 * que proxecta quen o usa (calendario, horas, partes…). Por baixo de 640 px o panel é unha folla
 * desde abaixo, co velo e un botón de pechar visible.
 *
 * Pecha con Esc, co clic fóra (ou no velo) e co botón de pechar; o foco volve sempre ao campo. Ao
 * abrir, o foco vai ao primeiro control do panel (ou ao marcado con `data-autofocus`) e o Tab dá a
 * volta dentro. `next()` pecha este e abre o campo `nextField` (os campos dunha reserva pasan sós
 * ao seguinte); sen `nextField`, só pecha. Non garda valor: o texto do resumo (`value`) e o que
 * se escolle no panel son da feature. Con `look="summary"` é a fila-resumo do «Cando» (maqueta
 * 21): o nome accesible leva o rótulo, o valor e o detalle; con `look="filter"`, un campo da fila
 * de filtros (`furar-filter-bar`). Estilos en `styles/reservas.css`: a ruta leva
 * `bookingStylesResolver`.
 */
@Component({
  selector: 'furar-field-popover',
  imports: [ButtonDirective, IconComponent, RequiredMarkComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-field-popover', '[attr.data-look]': 'look()' },
  templateUrl: './field-popover.component.html',
})
export class FieldPopoverComponent {
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild.required<TemplateRef<unknown>>('panel');
  private readonly panelElement = viewChild<ElementRef<HTMLElement>>('panelElement');

  /** Rótulo pequeno do campo («Data», «Hora»); nomea tamén o panel. */
  readonly label = input.required<string>();
  /** Valor escollido en texto («Mércores 28»); baleiro, vese `placeholder`. */
  readonly value = input<string | null>(null);
  /**
   * `field` (por defecto): rótulo pequeno e valor dentro do campo (maqueta 11). `summary`: fila-resumo
   * da maqueta 21, co rótulo enriba, a icona, o valor grande, o `detail` en gris e a acción
   * («Cambiar ⌄») á dereita. `filter`: campo dunha fila de filtros (`furar-filter-field`), coma un
   * `furar-select`: o rótulo vai fóra e o nome accesible é «rótulo, valor».
   */
  readonly look = input<'field' | 'summary' | 'filter'>('field');
  /** Id do botón, para o `<label for>` dun `furar-filter-field`. */
  readonly triggerId = input<string | null>(null);
  /** Segunda liña en gris do resumo (só `summary`). */
  readonly detail = input<string | null>(null);
  /** Icona do resumo, na cor principal (só `summary`). */
  readonly icon = input<IconName | null>(null);
  /** Texto da acción do resumo («Cambiar»; só `summary`). */
  readonly action = input('');
  /** Texto do campo sen valor («Escoller»). */
  readonly placeholder = input('');
  /** Id da axuda ou do erro que describe o campo. */
  readonly describedBy = input<string | null>(null);
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Obrigatorio (só `field`): «*» tras o rótulo e «(obrigatorio)» no nome accesible. */
  readonly required = input(false, { transform: booleanAttribute });
  /** Campo que abre `next()` (o seguinte paso). */
  readonly nextField = input<FieldPopoverComponent | null>(null);

  readonly opened = output();
  readonly closed = output();

  protected readonly panelId = `furar-field-popover-${nextId++}`;
  /** Panel aberto (`aria-expanded`). */
  readonly expanded = signal(false);
  /** Aberto como folla desde abaixo (pantalla estreita). */
  protected readonly sheet = signal(false);
  private overlayRef: OverlayRef | null = null;
  private focusWatch: MutationObserver | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.focusWatch?.disconnect();
      this.overlayRef?.dispose();
    });
  }

  /** Abre o panel e leva o foco dentro. */
  open(): void {
    if (this.expanded() || this.disabled()) {
      return;
    }
    const sheet = this.document.defaultView?.matchMedia?.(SHEET_QUERY).matches ?? false;
    this.sheet.set(sheet);
    this.overlayRef?.dispose();
    const overlayRef = (this.overlayRef = createOverlayRef(
      this.injector,
      sheet ? this.sheetConfig() : this.popoverConfig(),
    ));
    overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainer));
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        this.close();
      }
    });
    overlayRef.backdropClick().subscribe(() => this.close());
    overlayRef.outsidePointerEvents().subscribe((event) => this.onOutsideClick(event));
    this.expanded.set(true);
    this.watchFocus(overlayRef.overlayElement);
    this.opened.emit();
    afterNextRender(() => this.focusInside(), { injector: this.injector });
  }

  /** Pecha o panel; por defecto o foco volve ao campo. */
  close(restoreFocus = true): void {
    if (!this.expanded()) {
      return;
    }
    this.expanded.set(false);
    this.focusWatch?.disconnect();
    this.focusWatch = null;
    this.overlayRef?.dispose();
    this.overlayRef = null;
    this.closed.emit();
    if (restoreFocus) {
      this.trigger().nativeElement.focus();
    }
  }

  /** Pecha este campo e abre o seguinte (`nextField`); se non hai, só pecha. */
  next(): void {
    const following = this.nextField();
    if (!following || following.disabled()) {
      this.close();
      return;
    }
    this.close(false);
    following.open();
  }

  protected toggle(): void {
    if (this.expanded()) {
      this.close();
    } else {
      this.open();
    }
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' && !this.expanded()) {
      event.preventDefault();
      this.open();
    }
  }

  /**
   * Diálogo: Tab dá a volta dentro do panel. Á man e non con `cdkTrapFocus`, porque as súas
   * áncoras (tabindex=0 con aria-hidden) fan saltar axe.
   */
  protected onPanelKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }
    const stops = this.stops();
    if (!stops.length) {
      event.preventDefault();
      return;
    }
    const index = stops.indexOf(event.target as HTMLElement);
    const next = index === -1 ? (event.shiftKey ? stops.length - 1 : 0) : index + (event.shiftKey ? -1 : 1);
    event.preventDefault();
    stops[(next + stops.length) % stops.length]?.focus();
  }

  private onOutsideClick(event: MouseEvent): void {
    // O clic no propio campo xa o pecha toggle(); aquí só os de fóra.
    if (this.trigger().nativeElement.contains(event.target as Node)) {
      return;
    }
    // Se o clic deixou o foco nun control, queda alí; se non, volve ao campo.
    const target = event.target as HTMLElement | null;
    this.close(!target?.closest?.(FOCUSABLE));
  }

  private focusInside(): void {
    const panel = this.panelElement()?.nativeElement;
    if (!panel) {
      return;
    }
    const wanted =
      panel.querySelector<HTMLElement>('[data-autofocus]') ?? this.stops().find((el) => !el.hasAttribute('data-close'));
    (wanted ?? panel).focus();
  }

  /**
   * Se o contido do panel cambia e leva consigo o control co foco (as horas do día anterior que se
   * substitúen ao chegar as do novo), o foco volve dentro do panel en vez de quedar no body (2.4.3).
   */
  private watchFocus(pane: HTMLElement): void {
    this.focusWatch?.disconnect();
    const view = this.document.defaultView;
    if (!view?.MutationObserver) {
      return;
    }
    this.focusWatch = new view.MutationObserver(() => {
      const focused = this.document.activeElement;
      if (this.expanded() && (!focused || focused === this.document.body)) {
        this.focusInside();
      }
    });
    this.focusWatch.observe(pane, { childList: true, subtree: true });
  }

  private stops(): HTMLElement[] {
    return [...(this.panelElement()?.nativeElement.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])];
  }

  private popoverConfig() {
    const origin = this.trigger().nativeElement;
    return {
      positionStrategy: createFlexibleConnectedPositionStrategy(this.injector, origin)
        .withPositions(POSITIONS)
        .withViewportMargin(8)
        .withPush(true),
      scrollStrategy: createRepositionScrollStrategy(this.injector),
      minWidth: origin.getBoundingClientRect().width || undefined,
      panelClass: 'f-field-popover-pane',
    };
  }

  private sheetConfig() {
    return {
      positionStrategy: createGlobalPositionStrategy(this.injector).bottom('0').left('0'),
      scrollStrategy: createBlockScrollStrategy(this.injector),
      width: '100%',
      hasBackdrop: true,
      backdropClass: 'f-field-popover-backdrop',
      panelClass: ['f-field-popover-pane', 'f-field-popover-pane--sheet'],
    };
  }
}
