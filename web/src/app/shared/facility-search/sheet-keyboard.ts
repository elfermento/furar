// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';

/**
 * O teclado do móbil na folla «Buscar», sen medilo nin mover nada: a folla xa está fixa arriba
 * (search-sheet.css), co campo no alto da pantalla, así que o teclado só tapa a parte de abaixo.
 * Só se sabe se a persoa escribe (`writing`): o foco no campo vén dela (premelo ou teclear; o foco
 * ao abrir a folla non conta). Mentres, o pé non se ve. Saír do campo (p. ex. ao desprazar as
 * suxestións) non o volve amosar, para que o corpo non mude de alto baixo o dedo: volve con
 * `release` (escoller, outro apartado ou saír co campo baleiro).
 */
export class SheetKeyboard {
  /** A persoa premeu o campo: o foco que vén é seu. */
  private pressed = false;
  /** A persoa escribe no campo: sen pé. */
  readonly writing = signal(false);

  /** `pointerdown` ou `touchstart` no campo. */
  press(): void {
    this.pressed = true;
  }

  /** O campo colleu o foco: só conta se o premeu a persoa. */
  focus(): void {
    if (this.pressed) this.writing.set(true);
  }

  /** Teclear tamén é escribir (p. ex. co foco xa posto ao abrir). */
  typed(): void {
    this.writing.set(true);
  }

  /** Saíu do campo: o pé segue agochado mentres teña texto (`empty`, o campo baleiro). */
  blur(empty: boolean): void {
    this.pressed = false;
    if (empty) this.release();
  }

  /** Volve o pé. */
  release(): void {
    this.pressed = false;
    this.writing.set(false);
  }
}

/** O aire entre a cabeceira da folla e o campo «Onde» levado arriba (px). */
const FIELD_GAP = 8;

/** O corpo da folla que contén `el` (o único que se despraza: nunca a fiestra). */
function sheetBody(el: Element): HTMLElement | null {
  return el.closest<HTMLElement>('.f-dialog__body');
}

/** Despraza o corpo ata `top`, suave salvo con movemento reducido. */
function scrollBody(body: HTMLElement, top: number, instant = false): void {
  const reduce = instant || body.ownerDocument.documentElement.getAttribute('data-motion') === 'reduce';
  body.scrollTo({ top: Math.max(0, top), behavior: reduce ? 'auto' : 'smooth' });
}

/**
 * Premeron «Onde»: o campo sobe ata quedar xusto baixo a cabeceira (o título «Onde» sae por
 * riba), para que as suxestións teñan todo o sitio enriba do teclado. Abaixo vai o aire que faga
 * falta para chegar aínda co campo baleiro (`--f-ssheet-room`). `instant`, sen animación (ao
 * teclear: o navegador corta a animación con cada tecla).
 */
export function fieldToTop(field: HTMLElement, instant = false): void {
  const body = sheetBody(field);
  const box = field.closest<HTMLElement>('.f-ssheet__where') ?? field;
  if (!body) return;
  body.style.setProperty('--f-ssheet-room', `${Math.max(0, body.clientHeight - box.offsetHeight - FIELD_GAP)}px`);
  const delta = box.getBoundingClientRect().top - body.getBoundingClientRect().top - FIELD_GAP;
  if (Math.abs(delta) > 1) scrollBody(body, body.scrollTop + delta, instant);
}

/** Quita o aire de `fieldToTop` (ao deixar «Onde»). */
export function dropRoom(host: HTMLElement): void {
  host.querySelector<HTMLElement>('.f-dialog__body')?.style.removeProperty('--f-ssheet-room');
}

/** A tarxeta enteira á vista, no medio do corpo (se non colle, arriba). */
export function centerCard(card: HTMLElement): void {
  const body = sheetBody(card);
  if (!body) return;
  const from = body.getBoundingClientRect();
  const box = card.getBoundingClientRect();
  const offset = box.top - from.top;
  const room = body.clientHeight - box.height;
  scrollBody(body, body.scrollTop + offset - (room > 0 ? room / 2 : 0));
}
