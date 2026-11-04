// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/** A mesma lista branca ca o servidor (`JsoupLegalHtmlSanitizer`, ADR-0017 punto 8). */
const TAGS = new Set(['H2', 'H3', 'P', 'STRONG', 'EM', 'UL', 'OL', 'LI', 'A', 'BR']);
/** Contido que non é texto: nin el nin o seu texto chegan á páxina. */
const DROP = new Set(['SCRIPT', 'STYLE', 'TEMPLATE', 'NOSCRIPT', 'IFRAME', 'OBJECT', 'SVG', 'MATH']);
const LINK = /^(https:\/\/\S+|mailto:[^@\s]+@[^@\s]+\.[^@\s]+)$/i;

/**
 * Pinta HTML de prosa (texto propio das páxinas legais, texto de Furar) copiando só a lista branca:
 * `h2`, `h3`, `p`, `strong`, `em`, `ul`, `ol`, `li`, `br` e `a` con `href` https ou de correo (e
 * `rel="noopener noreferrer"`); o `id` dun título só se está en `anchors` (as anclas fixas dos textos
 * de Furar, ás que levan as ligazóns con fragmento). O HTML analízase nun documento inerte (`DOMParser`: nada se executa
 * nin se descarga) e créanse elementos novos, sen atributos copiados: nin `innerHTML` nin
 * `bypassSecurityTrust*`. O servidor xa o saneou; isto é a segunda barreira, e evita levar o
 * saneador de Angular á carga inicial (orzamento, D27).
 */
@Directive({ selector: '[furarProseHtml]' })
export class ProseHtmlDirective {
  readonly html = input<string | null>(null, { alias: 'furarProseHtml' });
  /** Os `id` que poden levar os títulos (`h2`, `h3`); calquera outro desaparece. */
  readonly anchors = input<readonly string[]>([]);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const document = inject(DOCUMENT);
    effect(() => {
      const parsed = new DOMParser().parseFromString(this.html() ?? '', 'text/html');
      host.replaceChildren(...copy(parsed.body, document, new Set(this.anchors())));
    });
  }
}

function copy(source: Node, document: Document, anchors: ReadonlySet<string>): Node[] {
  const nodes: Node[] = [];
  for (const child of Array.from(source.childNodes)) {
    if (child.nodeType === Node.TEXT_NODE) {
      nodes.push(document.createTextNode(child.textContent ?? ''));
    } else if (child instanceof Element) {
      const name = child.tagName.toUpperCase();
      if (DROP.has(name)) continue;
      const children = copy(child, document, anchors);
      if (!TAGS.has(name)) {
        nodes.push(...children);
        continue;
      }
      const element = document.createElement(name.toLowerCase());
      if (name === 'A') {
        const href = child.getAttribute('href')?.trim() ?? '';
        if (LINK.test(href)) element.setAttribute('href', href);
        element.setAttribute('rel', 'noopener noreferrer');
      }
      const id = child.getAttribute('id');
      if ((name === 'H2' || name === 'H3') && id && anchors.has(id)) element.id = id;
      element.append(...children);
      nodes.push(element);
    }
  }
  return nodes;
}
