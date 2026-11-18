// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';

/** O que acompaña a cada posición do scroll. */
export interface ScrollHideFrame {
  /** Algo da barra está aberto (un menú, un panel): non se esconde. */
  readonly pinned?: boolean;
  /** Ata aquí (px desde arriba) é «arriba de todo»: vese sempre. Normalmente, o alto da barra. */
  readonly top?: number;
}

/**
 * Barra que se esconde ao baixar e volve ao subir (sen DOM, para probala soa). Conta o percorrido
 * nun mesmo sentido: `delta` px cara abaixo escóndena e `delta` px cara arriba móstrana, así que
 * un tremor do dedo non a fai pestanexar. Arriba de todo vese sempre. Co foco dentro ou cun menú
 * dela aberto, vese sempre: o foco nunca queda tapado (WCAG 2.4.7).
 */
export class ScrollHide {
  private readonly scrolledAway = signal(false);
  private readonly focusInside = signal(false);
  private last = 0;
  /** Percorrido no sentido actual: positivo baixando, negativo subindo. */
  private travel = 0;

  readonly hidden = computed(() => this.scrolledAway() && !this.focusInside());

  constructor(private readonly delta = 8) {}

  /** Nova posición do scroll (px desde arriba). */
  update(y: number, frame: ScrollHideFrame = {}): void {
    const step = y - this.last;
    this.last = y;
    if (y <= (frame.top ?? 0) || frame.pinned) {
      this.travel = 0;
      this.scrolledAway.set(false);
      return;
    }
    if (step === 0) return;
    this.travel = Math.sign(step) === Math.sign(this.travel) ? this.travel + step : step;
    if (this.travel >= this.delta) {
      this.scrolledAway.set(true);
    } else if (this.travel <= -this.delta) {
      this.scrolledAway.set(false);
    }
  }

  /** O foco entrou na barra (ou saíu dela): co foco dentro, vese. */
  focus(inside: boolean): void {
    this.focusInside.set(inside);
    if (inside) {
      this.travel = 0;
      this.scrolledAway.set(false);
    }
  }
}
