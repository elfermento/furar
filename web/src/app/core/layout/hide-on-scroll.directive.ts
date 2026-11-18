// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Directive, ElementRef, effect, inject } from '@angular/core';
import { ScrollHide } from './scroll-hide';

/** Atributo de `<html>` mentres a barra está escondida: `--f-topbar-offset` pasa a 0 (site.css). */
export const BAR_HIDDEN_ATTR = 'data-topbar-hidden';

/**
 * Barra fixa arriba que se esconde ao baixar e volve ao subir (ScrollHide, 8 px de marxe), en
 * todas as páxinas e anchos. Marca o elemento con `data-hidden` (o CSS móveo só con `transform`;
 * sen animación con menos movemento) e `<html>` con `data-topbar-hidden`, para que o que vai
 * pegado debaixo (`--f-topbar-offset`) suba con ela. Non se esconde co foco dentro nin cun menú
 * ou panel dela aberto (`aria-expanded="true"` dentro): o teclado nunca a perde.
 */
@Directive({
  selector: '[furarHideOnScroll]',
  host: {
    '[attr.data-hidden]': 'state.hidden() || null',
    '(focusin)': 'state.focus(true)',
    '(focusout)': 'onFocusOut($event)',
  },
})
export class HideOnScrollDirective {
  protected readonly state = new ScrollHide();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    const document = inject(DOCUMENT);
    const view = document.defaultView;
    const root = document.documentElement;
    effect(() => root.toggleAttribute(BAR_HIDDEN_ATTR, this.state.hidden()));
    if (!view) return;
    let frame = 0;
    const read = () => {
      // O rebote de iOS pasa de 0 e do final: sen el, o volver do rebote parecería subir.
      const max = Math.max(0, root.scrollHeight - view.innerHeight);
      const y = Math.min(Math.max(view.scrollY, 0), max || Infinity);
      this.state.update(y, {
        top: this.host.offsetHeight,
        pinned: this.host.querySelector('[aria-expanded="true"]') !== null,
      });
    };
    const onScroll = () => {
      view.cancelAnimationFrame(frame);
      frame = view.requestAnimationFrame(read);
    };
    view.addEventListener('scroll', onScroll, { passive: true });
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('scroll', onScroll);
      view.cancelAnimationFrame(frame);
      root.removeAttribute(BAR_HIDDEN_ATTR);
    });
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !this.host.contains(next)) {
      this.state.focus(false);
    }
  }
}
