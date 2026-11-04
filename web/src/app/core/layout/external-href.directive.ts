// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive, ElementRef, effect, inject, input } from '@angular/core';

const HTTPS = /^https:\/\/\S+$/i;

/**
 * `href` dunha ligazón á web do concello no marco (pé). Só https: o resto non pon nada. Vai como
 * directiva porque `[href]` meteria o saneador de URL de Angular na carga inicial (orzamento, D27)
 * só para esta ligazón, que xa chega validada (`externalLegalUrl`).
 */
@Directive({ selector: 'a[furarExternalHref]' })
export class ExternalHrefDirective {
  readonly url = input<string | null>(null, { alias: 'furarExternalHref' });

  constructor() {
    const anchor = inject<ElementRef<HTMLAnchorElement>>(ElementRef).nativeElement;
    effect(() => {
      const url = this.url();
      if (url && HTTPS.test(url)) anchor.setAttribute('href', url);
      else anchor.removeAttribute('href');
    });
  }
}
