// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition, OverlayRef } from '@angular/cdk/overlay';
import {
  createFlexibleConnectedPositionStrategy,
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
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

let nextId = 0;

/** Retardo ao pasar o rato por riba antes de abrir: cruzar o «?» de paso non o fai pestanexar. */
export const HELP_TIP_ENTER_DELAY_MS = 120;
/** Tempo para pasar o rato do «?» ao texto sen que peche (1.4.13, «hoverable»). */
export const HELP_TIP_LEAVE_DELAY_MS = 200;

/** Enriba do «?» e, se non cabe, debaixo; aliñado ao seu comezo. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
];

/**
 * Axuda curta ao carón dun rótulo: un «?» de 18 px con contorno (sen recheo; zona de toque de 44 px
 * nunha pantalla táctil), que mostra o texto nunha capa pequena.
 *
 * - Co rato por riba (do «?» ou do propio texto) ábrese, cun pequeno retardo, e vese só mentres
 *   dura; ao saír pecha (cun retardo que deixa pasar o rato ao texto).
 * - Ao premer (clic, toque, ou Intro/Espazo co teclado) queda fixa ata picar fóra, Esc, levar o
 *   foco a outro control ou premer outra vez. Nunha pantalla táctil non hai rato: só premer.
 * - Co foco do teclado non se abre soa (non tapa o contido ao tabular): ábrese ao activala.
 *
 * WCAG 1.4.13: descartable (Esc), sobrevoable (o rato pode ir ao texto) e persistente. O texto
 * está sempre no DOM (oculto á vista) co id `textId`, así o botón e o control do campo poden
 * apuntalo con `aria-describedby`; a capa é só visual. A entrada esvaece agás con «Menos
 * movemento». Úsao `furar-field` co input `tip`.
 */
@Component({
  selector: 'furar-help-tip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-help-tip' },
  template: `
    <button
      #trigger
      type="button"
      class="f-help-tip__trigger"
      [attr.aria-label]="label()"
      [attr.aria-describedby]="describedId()"
      [attr.data-open]="expanded() || null"
      (pointerenter)="onPointerEnter($event)"
      (pointerleave)="onPointerLeave($event)"
      (blur)="onBlur($event)"
      (click)="onClick()"
    >
      <span aria-hidden="true">?</span>
    </button>
    <span class="sr-only" [id]="describedId()">{{ text() }}</span>
    <ng-template #panel>
      <div
        class="f-help-tip__panel"
        aria-hidden="true"
        [attr.data-help-tip]="describedId()"
        (pointerenter)="onPointerEnter($event)"
        (pointerleave)="onPointerLeave($event)"
      >
        {{ text() }}
      </div>
    </ng-template>
  `,
})
export class HelpTipComponent {
  private readonly injector = inject(Injector);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild.required<TemplateRef<unknown>>('panel');

  /** Nome do botón: a pregunta («Que é o DPD?») ou «Axuda sobre «rótulo»». */
  readonly label = input.required<string>();
  /** Texto da axuda. */
  readonly text = input.required<string>();
  /** Id do texto oculto (por defecto, un propio); `furar-field` pon `<fieldId>-help`. */
  readonly textId = input<string | null>(null);

  private readonly ownId = `furar-help-tip-${nextId++}`;
  protected readonly describedId = computed(() => this.textId() || this.ownId);
  /** Capa aberta. */
  readonly expanded = signal(false);

  /** Fixada cun clic, un toque ou co teclado: non pecha ao saír o rato. */
  private pinned = false;
  private hovering = false;
  private enterTimer: ReturnType<typeof setTimeout> | null = null;
  private leaveTimer: ReturnType<typeof setTimeout> | null = null;
  private overlayRef: OverlayRef | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.clearTimers();
      this.overlayRef?.dispose();
    });
  }

  /** Pecha a capa e esquece o estado (rato e fixada). */
  close(): void {
    this.clearTimers();
    this.pinned = false;
    this.hovering = false;
    this.overlayRef?.dispose();
    this.overlayRef = null;
    this.expanded.set(false);
  }

  protected onPointerEnter(event: PointerEvent): void {
    if (event.pointerType && event.pointerType !== 'mouse') return;
    this.hovering = true;
    this.clearTimers();
    if (this.expanded()) return;
    this.enterTimer = setTimeout(() => {
      this.enterTimer = null;
      if (this.hovering) this.open();
    }, HELP_TIP_ENTER_DELAY_MS);
  }

  protected onPointerLeave(event: PointerEvent): void {
    if (event.pointerType && event.pointerType !== 'mouse') return;
    this.hovering = false;
    this.clearTimers();
    this.leaveTimer = setTimeout(() => {
      this.leaveTimer = null;
      if (!this.pinned && !this.hovering) this.close();
    }, HELP_TIP_LEAVE_DELAY_MS);
  }

  protected onBlur(event: FocusEvent): void {
    // O foco foi a outro control (Tab ou un clic noutro sitio): pecha aínda que estivese fixada.
    if (event.relatedTarget && !this.hovering) this.close();
  }

  /** Clic, toque, Intro ou Espazo: fixa a capa (abrindoa se fai falta) ou, se xa estaba fixa, pecha. */
  protected onClick(): void {
    if (this.pinned) {
      this.close();
      return;
    }
    this.clearTimers();
    this.pinned = true;
    this.open();
  }

  private open(): void {
    if (this.expanded()) return;
    const origin = this.trigger().nativeElement;
    const overlayRef = (this.overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createFlexibleConnectedPositionStrategy(this.injector, origin)
        .withPositions(POSITIONS)
        .withViewportMargin(8)
        .withPush(true),
      scrollStrategy: createRepositionScrollStrategy(this.injector),
      panelClass: 'f-help-tip-pane',
    }));
    overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainer));
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        this.close();
      }
    });
    overlayRef.outsidePointerEvents().subscribe((event) => {
      if (!origin.contains(event.target as Node)) this.close();
    });
    this.expanded.set(true);
  }

  private clearTimers(): void {
    if (this.enterTimer) clearTimeout(this.enterTimer);
    if (this.leaveTimer) clearTimeout(this.leaveTimer);
    this.enterTimer = null;
    this.leaveTimer = null;
  }
}
