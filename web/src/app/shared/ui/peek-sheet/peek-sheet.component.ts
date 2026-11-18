// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  model,
  untracked,
  viewChild,
} from '@angular/core';

/** O que hai que mover o dedo para que conte coma arrastre (e non coma toque). */
const DRAG_SLOP = 6;
/** Velocidade (px/ms) dun lanzamento: abre ou pecha aínda que se mova pouco. */
const FLICK = 0.5;

/**
 * Folla que asoma desde abaixo (o mapa a toda a pantalla do móbil): a superficie das follas (os
 * cantos de arriba redondos, a sombra), co asa e un título («60 instalacións»). Picar ou arrastrar
 * cara arriba estende a folla por riba do que hai detrás e mostra o contido proxectado; picar ou
 * arrastrar cara abaixo vólvea pregar. Mentres o dedo está pousado, a folla vai co dedo (transform
 * directo, sen transición); ao soltar remata cunha transición que decide a distancia ou a
 * velocidade. Esc e o botón «atrás» do navegador préganna antes de nada. Con menos movemento, sen
 * transición.
 */
@Component({
  selector: 'furar-peek-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents', '(keydown.escape)': 'onEscape($event)' },
  template: `
    <section #sheet class="f-peek" [attr.aria-label]="label()" [attr.data-expanded]="expanded() || null">
      <button
        type="button"
        class="f-peek__head"
        data-peek-toggle
        [attr.aria-expanded]="expanded()"
        (pointerdown)="onDown($event)"
        (pointermove)="onMove($event)"
        (pointerup)="onUp($event)"
        (pointercancel)="onUp($event)"
        (click)="onClick($event)"
      >
        <span class="f-peek__handle" aria-hidden="true"></span>
        <span class="f-peek__label">{{ label() }}</span>
      </button>
      <div class="f-peek__body" [attr.inert]="expanded() ? null : ''">
        <ng-content />
      </div>
    </section>
  `,
})
export class PeekSheetComponent {
  private readonly sheet = viewChild.required<ElementRef<HTMLElement>>('sheet');
  private readonly window = inject(DOCUMENT).defaultView;

  /** O título da folla, xa traducido: tamén o seu nome accesible. */
  readonly label = input.required<string>();
  /** Estendida (por riba do que hai detrás) ou pregada (só asoma o título). */
  readonly expanded = model(false);

  private drag: { y: number; from: number; max: number; last: number; t: number; v: number } | null = null;
  private moved = false;
  /** Ao estender, unha entrada no historial: «atrás» prega a folla en vez de saír da páxina. */
  private pushed = false;
  private popping = false;

  constructor() {
    const onPop = () => {
      if (!this.pushed) return;
      this.pushed = false;
      this.popping = true;
      this.expanded.set(false);
    };
    this.window?.addEventListener('popstate', onPop);
    inject(DestroyRef).onDestroy(() => this.window?.removeEventListener('popstate', onPop));
    effect(() => {
      const open = this.expanded();
      untracked(() => {
        if (open && !this.pushed) {
          this.window?.history.pushState({ ...this.window.history.state, furarPeek: true }, '');
          this.pushed = true;
        } else if (!open && this.pushed && !this.popping) {
          this.pushed = false;
          this.window?.history.back();
        }
        this.popping = false;
      });
    });
  }

  protected onEscape(event: Event): void {
    if (!this.expanded()) return;
    event.stopPropagation();
    this.expanded.set(false);
  }

  protected onClick(event: MouseEvent): void {
    // Un arrastre remata en «click»: xa decidiu ao soltar (o teclado, `detail` 0, sempre conmuta).
    if (this.moved && event.detail !== 0) {
      this.moved = false;
      return;
    }
    this.expanded.update((open) => !open);
  }

  protected onDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    const el = this.sheet().nativeElement;
    const max = el.offsetHeight - (el.querySelector<HTMLElement>('.f-peek__head')?.offsetHeight ?? 0);
    const from = this.expanded() ? 0 : max;
    this.drag = { y: event.clientY, from, max, last: from, t: event.timeStamp, v: 0 };
    this.moved = false;
  }

  protected onMove(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag) return;
    const dy = event.clientY - drag.y;
    if (!this.moved) {
      if (Math.abs(dy) < DRAG_SLOP) return;
      this.moved = true;
      (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
      this.sheet().nativeElement.setAttribute('data-dragging', '');
    }
    const raw = drag.from + dy;
    // Fóra dos topes, resistencia (un quinto do movemento) en vez de parar en seco.
    const y = raw < 0 ? raw * 0.2 : raw > drag.max ? drag.max + (raw - drag.max) * 0.2 : raw;
    const dt = Math.max(1, event.timeStamp - drag.t);
    drag.v = (y - drag.last) / dt;
    drag.last = y;
    drag.t = event.timeStamp;
    this.sheet().nativeElement.style.transform = `translateY(${y}px)`;
  }

  protected onUp(event: PointerEvent): void {
    const drag = this.drag;
    this.drag = null;
    if (!drag || !this.moved) return;
    (event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId);
    const el = this.sheet().nativeElement;
    el.removeAttribute('data-dragging');
    el.style.transform = '';
    const open = drag.v < -FLICK || (drag.v <= FLICK && drag.last < drag.max / 2);
    this.expanded.set(open);
  }
}
