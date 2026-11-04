// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import { ToastService } from './toast.service';

/** Canto se ve un aviso sen o rato nin o foco enriba. */
export const TOAST_DURATION_MS = 6000;

/**
 * Toast de «feito» (docs/deseno.md §8, «Toast de feito»), un só para toda a app, no shell. Flotante ao pé, coa
 * icona á altura do texto; desaparece só aos 6 s, e a conta párase mentres o rato ou o foco están
 * enriba (WCAG 2.2.1) e volve empezar ao saír. ✕ e `Esc` pechan; `Esc` fóra do toast só se non hai
 * ningunha capa aberta (a capa pécha antes). A rexión `role="status"` existe sempre, baleira, para
 * que o lector anuncie o texto ao aparecer; nunca colle o foco.
 */
@Component({
  selector: 'furar-toast',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-toast-region',
    role: 'status',
    'aria-live': 'polite',
    '(document:keydown.escape)': 'onEscape($event)',
  },
  template: `
    @if (toasts.current(); as toast) {
      <div
        class="f-toast"
        data-toast
        (mouseenter)="hold('pointer', true)"
        (mouseleave)="hold('pointer', false)"
        (focusin)="hold('focus', true)"
        (focusout)="onFocusOut($event)"
      >
        <furar-icon class="f-toast__icon" name="success" />
        <p class="f-toast__text">
          <span class="sr-only">{{ 'ui.tone.success' | transloco }}</span>
          {{ toast.key | transloco: toast.params }}
        </p>
        <button
          type="button"
          furarButton="quiet"
          size="sm"
          round
          class="f-toast__close"
          data-toast-close
          [attr.aria-label]="'ui.toast.close' | transloco"
          (click)="close(toast.id)"
        >
          <furar-icon name="close" />
        </button>
      </div>
    }
  `,
})
export class ToastComponent {
  protected readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private readonly view = this.document.defaultView;

  private timer: number | null = null;
  private remaining = TOAST_DURATION_MS;
  private startedAt = 0;
  private readonly held = new Set<'pointer' | 'focus'>();

  constructor() {
    effect(() => {
      const toast = this.toasts.current();
      this.stop();
      this.held.clear();
      this.remaining = TOAST_DURATION_MS;
      if (toast) this.start(toast.id);
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  /** O rato ou o foco enriba paran a conta; cando saen os dous, segue co que lle quedaba. */
  protected hold(reason: 'pointer' | 'focus', on: boolean): void {
    const toast = this.toasts.current();
    if (!toast) return;
    if (on) {
      this.held.add(reason);
      if (this.timer !== null) {
        this.remaining = Math.max(0, this.remaining - (Date.now() - this.startedAt));
        this.stop();
      }
      return;
    }
    this.held.delete(reason);
    if (this.held.size === 0 && this.timer === null) this.start(toast.id);
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !this.host.nativeElement.contains(next)) this.hold('focus', false);
  }

  protected onEscape(event: Event): void {
    const toast = this.toasts.current();
    if (!toast || event.defaultPrevented) return;
    const inside = this.host.nativeElement.contains(this.document.activeElement);
    if (!inside && this.document.querySelector('.cdk-overlay-pane')) return;
    this.close(toast.id);
  }

  /** Pecha; se o foco estaba no toast, pasa ao contido (`#contido`) para que non se perda. */
  protected close(id: number): void {
    const inside = this.host.nativeElement.contains(this.document.activeElement);
    this.toasts.dismiss(id);
    if (inside) this.document.getElementById('contido')?.focus();
  }

  private start(id: number): void {
    if (!this.view) return;
    this.startedAt = Date.now();
    this.timer = this.view.setTimeout(() => {
      this.timer = null;
      this.toasts.dismiss(id);
    }, this.remaining);
  }

  private stop(): void {
    if (this.timer !== null) this.view?.clearTimeout(this.timer);
    this.timer = null;
  }
}
