// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** Ancho mínimo que ten que sobrar para dicir que hai máis pestanas a un lado. */
const EDGE = 1;

/**
 * Franxa de pestanas nunha soa fila (docs/deseno.md, pestanas): se non caben, a fila desprázase
 * dentro da franxa, nunca a páxina. Marca con `data-fade` (`l`, `r` ou `l r`) os bordos con máis
 * pestanas, para o fundido do CSS, e `reveal()` centra unha pestana (a activa) sen mover a páxina.
 */
@Directive({
  selector: '[furarTabStrip]',
  exportAs: 'furarTabStrip',
  host: { '(scroll)': 'update()' },
})
export class TabStripDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly view = inject(DOCUMENT).defaultView;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.update();
      const ResizeObserverCtor = this.view?.ResizeObserver;
      if (!ResizeObserverCtor) return;
      const observer = new ResizeObserverCtor(() => this.update());
      observer.observe(this.host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  /** Recalcula os bordos con máis pestanas. */
  update(): void {
    const el = this.host;
    const max = el.scrollWidth - el.clientWidth;
    const fade: string[] = [];
    if (max > EDGE && el.scrollLeft > EDGE) fade.push('l');
    if (max > EDGE && el.scrollLeft < max - EDGE) fade.push('r');
    if (fade.length) el.dataset['fade'] = fade.join(' ');
    else delete el.dataset['fade'];
  }

  /** Leva `tab` ao centro da franxa, se a franxa se despraza (a páxina non se move). */
  reveal(tab: HTMLElement | null | undefined): void {
    const el = this.host;
    if (tab && el.scrollWidth > el.clientWidth) {
      // A franxa é `position: relative` (tabs.css): `offsetLeft` cóntase desde ela.
      el.scrollLeft = tab.offsetLeft - (el.clientWidth - tab.offsetWidth) / 2;
    }
    this.update();
  }
}
