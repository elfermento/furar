// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Directive, ElementRef, afterNextRender, inject, input } from '@angular/core';

/** Opción activa: a pulsada (`aria-pressed`) ou a marcada (modo radio, `aria-checked`). */
const ACTIVE = ':scope > button:is([aria-pressed="true"], [aria-checked="true"])';

/**
 * Selector de opcións (docs/deseno.md P1-11): carril de botóns co ancho xusto das súas opcións e
 * un fondo que se despraza ata a escollida. Modo `aria-pressed` sobre un contedor role="group" con
 * nome (idioma) ou modo radio (`mode="radio"`, formularios). `furarSegmented="primary"` sobre a
 * cabeceira.
 *
 * O fondo é o `::before` do carril: a directiva mide a opción activa e escribe a súa posición e
 * tamaño en variables CSS por CSSOM (compatible coa CSP). Mentres non hai medida (primeira
 * pintura, jsdom), a opción activa pinta o seu propio fondo. Sen movemento, con
 * `prefers-reduced-motion`, e sen fondo deslizante con cores forzadas (a activa vai en Highlight).
 */
@Directive({
  selector: '[furarSegmented]',
  host: { class: 'f-segmented', '[attr.data-surface]': 'surface()' },
})
export class SegmentedDirective {
  readonly surface = input<'surface' | 'primary', 'surface' | 'primary' | ''>('surface', {
    alias: 'furarSegmented',
    transform: (value: 'surface' | 'primary' | '') => value || 'surface',
  });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.place();
      const observers: { disconnect(): void }[] = [];
      if (typeof MutationObserver !== 'undefined') {
        const mutations = new MutationObserver(() => this.place());
        mutations.observe(this.host, {
          subtree: true,
          childList: true,
          attributes: true,
          attributeFilter: ['aria-pressed', 'aria-checked'],
        });
        observers.push(mutations);
      }
      if (typeof ResizeObserver !== 'undefined') {
        const sizes = new ResizeObserver(() => this.place());
        sizes.observe(this.host);
        observers.push(sizes);
      }
      destroyRef.onDestroy(() => observers.forEach((o) => o.disconnect()));
    });
  }

  /** Leva o fondo á opción activa (ou escóndeo se non hai ningunha). */
  private place(): void {
    const active = this.host.querySelector<HTMLElement>(ACTIVE);
    if (!active || active.offsetWidth === 0) {
      this.host.removeAttribute('data-thumb');
      return;
    }
    const style = this.host.style;
    style.setProperty('--f-seg-x', `${active.offsetLeft}px`);
    style.setProperty('--f-seg-y', `${active.offsetTop}px`);
    style.setProperty('--f-seg-w', `${active.offsetWidth}px`);
    style.setProperty('--f-seg-h', `${active.offsetHeight}px`);
    this.host.setAttribute('data-thumb', '');
  }
}
