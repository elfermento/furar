// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { WritableSignal } from '@angular/core';

/** Marxe (px) desde os bordos da tira onde, arrastrando, se despraza soa. */
export const EDGE = 96;

/** O que precisa o arrastre da tira que o usa. */
export interface CarouselDragHost {
  readonly view: Window | null;
  readonly strip: () => HTMLElement;
  /** As claves na orde actual (sen arrastrar). */
  readonly keys: () => string[];
  /** Orde provisional mentres se arrastra; `null` cando non. */
  readonly preview: WritableSignal<readonly string[] | null>;
  /** Executa despois do seguinte pintado (para medir as posicións novas). */
  readonly afterRender: (work: () => void) => void;
  readonly reducedMotion: () => boolean;
  /** Soltouse con outra orde. */
  readonly dropped: (order: string[], key: string) => void;
}

const items = (strip: HTMLElement) => Array.from(strip.querySelectorAll<HTMLElement>('.f-carousel__item'));

/**
 * Ordenar a tira arrastrando (HTML drag and drop): mentres se arrastra, a orde vai nun signal
 * provisional e as demais deslízanse ao seu sitio novo (FLIP); preto dun bordo, ou fóra da tira, a
 * tira desprázase soa. Escóitase `dragover` en todo o documento: o punteiro pode saír da tira.
 */
export class CarouselDrag {
  private key: string | null = null;
  private pointerX = 0;
  private frame = 0;

  constructor(private readonly host: CarouselDragHost) {}

  get active(): boolean {
    return this.key !== null;
  }

  start(event: DragEvent): void {
    const key = (event.target as HTMLElement).closest<HTMLElement>('.f-carousel__item')?.dataset['key'];
    if (!key) return;
    this.key = key;
    event.dataTransfer?.setData('text/plain', '');
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    this.pointerX = event.clientX;
    // Despois do instantáneo que o navegador fai do elemento arrastrado.
    setTimeout(() => {
      if (this.key) this.host.preview.set(this.host.keys());
    });
    this.loop();
  }

  over(event: DragEvent): void {
    if (!this.key) return;
    event.preventDefault();
    this.pointerX = event.clientX;
    const rect = this.host.strip().getBoundingClientRect();
    if (event.clientX > rect.left && event.clientX < rect.right) this.placeAt(event.clientX);
  }

  drop(event: DragEvent): void {
    if (this.key) event.preventDefault();
  }

  end(): void {
    const key = this.key;
    if (!key) return;
    this.key = null;
    this.stop();
    const order = this.host.preview();
    this.host.preview.set(null);
    const before = this.host.keys();
    if (order && order.length === before.length && order.some((k, i) => k !== before[i])) {
      this.host.dropped([...order], key);
    }
  }

  stop(): void {
    this.host.view?.cancelAnimationFrame(this.frame);
  }

  /** Desprazamento automático preto dos bordos (ou fóra da tira) mentres se arrastra. */
  private loop(): void {
    const view = this.host.view;
    if (!view || !this.key) return;
    const strip = this.host.strip();
    const rect = strip.getBoundingClientRect();
    let speed = 0;
    if (this.pointerX < rect.left + EDGE) speed = -Math.max(4, (rect.left + EDGE - this.pointerX) / 4);
    else if (this.pointerX > rect.right - EDGE) speed = Math.max(4, (this.pointerX - (rect.right - EDGE)) / 4);
    if (speed) {
      strip.scrollLeft += speed;
      this.placeAt(Math.min(Math.max(this.pointerX, rect.left + 1), rect.right - 1));
    }
    this.frame = view.requestAnimationFrame(() => this.loop());
  }

  /** Coloca o arrastrado antes do primeiro elemento cuxa metade queda á dereita de `x`. */
  placeAt(x: number): void {
    const order = this.host.preview();
    const dragged = this.key;
    if (!order || !dragged) return;
    const others = items(this.host.strip()).filter((el) => el.dataset['key'] !== dragged);
    let index = others.length;
    for (let i = 0; i < others.length; i++) {
      const r = others[i].getBoundingClientRect();
      if (x < r.left + r.width / 2) {
        index = i;
        break;
      }
    }
    const next = order.filter((k) => k !== dragged);
    next.splice(index, 0, dragged);
    if (next.every((k, i) => k === order[i])) return;
    this.flip(() => this.host.preview.set(next));
  }

  /** FLIP: as demais deslízanse desde onde estaban ata o seu sitio novo. */
  private flip(change: () => void): void {
    const before = new Map(
      items(this.host.strip()).map((el) => [el.dataset['key'] ?? '', el.getBoundingClientRect().left]),
    );
    change();
    this.host.afterRender(() => {
      if (this.host.reducedMotion()) return;
      for (const el of items(this.host.strip())) {
        const key = el.dataset['key'] ?? '';
        const dx = (before.get(key) ?? 0) - el.getBoundingClientRect().left;
        if (!dx || key === this.key) continue;
        el.style.transition = 'none';
        el.style.transform = `translateX(${dx}px)`;
        requestAnimationFrame(() => {
          el.style.transition = '';
          el.style.transform = '';
        });
      }
    });
  }
}
