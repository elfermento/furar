// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** Só no móbil: no escritorio a collaxe vai na súa columna. */
const MOBILE = '(max-width: 639px)';
/** O tamaño da collaxe do escritorio (30 × 26 rem), que no móbil se reduce; en px co texto normal. */
const ART = { width: 480, height: 416 };
/** O ancho da collaxe en rem (home.css): co texto máis grande mide máis ca `ART.width`. */
const ART_REM = 30;
/** Por debaixo disto (px), a collaxe non se ve. */
const MIN = { width: 90, height: 80 };
/** Aire entre a collaxe e o texto, o botón ou o encabezado de despois (px). */
const GAP = 16;
/** Dúas palabras na mesma liña se as súas caixas empezan a menos disto (px). */
const SAME_LINE = 4;

interface Box {
  readonly left: number;
  readonly right: number;
  readonly top: number;
  readonly bottom: number;
}

/**
 * Móbil: a collaxe do escritorio (`.f-collage`) no oco baleiro á
 * dereita da cabeceira, sen mover nada (vai en posición absoluta). Busca a caixa máis grande que
 * colle entre o título (tamén á beira das liñas que deixan sitio á dereita), o botón «Buscar» e o
 * primeiro encabezado de despois, sen pisar ningún. Mide ao pintar, ao cambiar o tamaño, cando
 * cargan as letras e ao cambiar o contido (o texto do idioma, a collaxe ao chegar as instalacións); pon `data-art` e a posición e a escala en
 * variables CSS (home.css). Se a caixa non chega a 90 × 80 px, non se ve.
 */
@Directive({ selector: '[furarHeroArt]' })
export class HeroArtDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly view = inject(DOCUMENT).defaultView;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const view = this.view;
      if (!view) return;
      const measure = () => this.measure();
      const resize = view.ResizeObserver ? new view.ResizeObserver(measure) : null;
      resize?.observe(this.host);
      // O texto do título (o idioma) e a collaxe, que chega coas instalacións.
      const change = view.MutationObserver ? new view.MutationObserver(measure) : null;
      change?.observe(this.host, { subtree: true, childList: true, characterData: true });
      void view.document.fonts?.ready.then(measure);
      measure();
      destroyRef.onDestroy(() => {
        resize?.disconnect();
        change?.disconnect();
      });
    });
  }

  private measure(): void {
    const view = this.view;
    const host = this.host;
    const art = host.querySelector<HTMLElement>('.f-collage');
    const title = host.querySelector('h1');
    const button = host.querySelector<HTMLElement>('.f-hero__open');
    // O seu marco (a reixa da cabeceira, en `position: relative`); escondida non ten `offsetParent`.
    const frame = art?.parentElement;
    if (!view || !art || !title || !button || !view.matchMedia?.(MOBILE).matches) {
      host.removeAttribute('data-art');
      return;
    }
    const content = (host.querySelector('.f-hero__main') ?? host).getBoundingClientRect();
    const obstacles = [...wordLines(title), button.getBoundingClientRect()];
    const top = title.getBoundingClientRect().top;
    const next = nextHeading(host);
    const bottom = (next?.getBoundingClientRect().top ?? host.getBoundingClientRect().bottom) - GAP;
    const box = largest(obstacles, content.right, top, bottom);
    if (!box || !frame) {
      host.removeAttribute('data-art');
      return;
    }
    const at = frame.getBoundingClientRect();
    host.style.setProperty('--f-art-left', `${Math.round(box.left - at.left)}px`);
    host.style.setProperty('--f-art-top', `${Math.round(box.top - at.top)}px`);
    // A escala sobre o ancho de verdade (30 rem): co texto grande, a collaxe non sae pola dereita.
    const rem = Number.parseFloat(view.getComputedStyle(host.ownerDocument.documentElement).fontSize) || 16;
    host.style.setProperty('--f-art-scale', String((box.right - box.left) / (ART_REM * rem)));
    host.setAttribute('data-art', '');
  }
}

/** O primeiro encabezado das seccións de despois da cabeceira (o dos carruseis ou «Instalacións»). */
function nextHeading(hero: HTMLElement): HTMLElement | null {
  for (let next = hero.nextElementSibling; next; next = next.nextElementSibling) {
    const heading = next.querySelector<HTMLElement>('.f-home__heading');
    if (heading) return heading;
  }
  return null;
}

/**
 * A caixa máis grande coas proporcións da collaxe, pegada á beira dereita, entre `top` e `bottom`,
 * que non pisa ningún obstáculo (con `GAP` de aire): a escala máis grande que colle nalgún oco.
 */
export function largest(obstacles: readonly Box[], right: number, top: number, bottom: number): Box | null {
  for (let scale = 1; scale > 0; scale -= 0.01) {
    const [width, height] = [ART.width * scale, ART.height * scale];
    if (width < MIN.width || height < MIN.height) return null;
    const left = right - width;
    // Os obstáculos que lle quedan á altura (polo ancho), de arriba a abaixo.
    const near = obstacles.filter((o) => o.right + GAP > left).sort((a, b) => a.top - b.top);
    let from = top;
    for (const o of [...near, { left: 0, right, top: bottom + GAP, bottom: bottom + GAP }]) {
      const room = o.top - GAP - from;
      if (room >= height) {
        const start = from + (room - height) / 2;
        return { left, right, top: start, bottom: start + height };
      }
      from = Math.max(from, o.bottom + GAP);
    }
  }
  return null;
}

/** As caixas das palabras do elemento, unha por liña (do principio da primeira á fin da última). */
function wordLines(element: Element): Box[] {
  const lines: { left: number; right: number; top: number; bottom: number }[] = [];
  const walker = element.ownerDocument.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  const range = element.ownerDocument.createRange();
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    for (const word of (node.textContent ?? '').matchAll(/\S+/g)) {
      range.setStart(node, word.index);
      range.setEnd(node, word.index + word[0].length);
      const box = range.getBoundingClientRect();
      if (!box.width) continue;
      const line = lines.find((l) => Math.abs(l.top - box.top) < SAME_LINE);
      if (line) {
        line.left = Math.min(line.left, box.left);
        line.right = Math.max(line.right, box.right);
        line.bottom = Math.max(line.bottom, box.bottom);
      } else {
        lines.push({ left: box.left, right: box.right, top: box.top, bottom: box.bottom });
      }
    }
  }
  return lines;
}
