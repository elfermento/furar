// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';

/**
 * Barra que se esconde ao baixar e volve ao subir (sen DOM, para probala soa). Pasados `threshold`
 * px, baixar escóndea e subir móstraa; arriba de todo vese sempre. Mentres algo dentro ten o foco
 * vese sempre: o foco nunca queda tapado (WCAG 2.4.7).
 */
export class ScrollHide {
  private readonly scrolledAway = signal(false);
  private readonly focusInside = signal(false);
  private last = 0;

  readonly hidden = computed(() => this.scrolledAway() && !this.focusInside());

  constructor(private readonly threshold = 80) {}

  /** Nova posición do scroll (px desde arriba). */
  update(y: number): void {
    if (y <= this.threshold || y < this.last) {
      this.scrolledAway.set(false);
    } else if (y > this.last) {
      this.scrolledAway.set(true);
    }
    this.last = y;
  }

  /** O foco entrou na barra (ou saíu dela): co foco dentro, vese. */
  focus(inside: boolean): void {
    this.focusInside.set(inside);
    if (inside) {
      this.scrolledAway.set(false);
    }
  }
}
