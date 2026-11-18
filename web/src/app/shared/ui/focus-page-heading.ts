// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { Injector } from '@angular/core';
import { afterNextRender } from '@angular/core';

/**
 * Cando unha acción quita da páxina o elemento que tiña o foco (a tarxeta dunha espera, dun evento
 * ou dunha partida que se deixa), o foco quedaría no `body` e o teclado volvería empezar a páxina
 * sen aviso. Despois de pintar, se o foco se perdeu, vai ao título da páxina (WCAG 2.4.3), coma ao
 * cambiar de páxina. Se o foco segue nun elemento, non fai nada.
 */
export function focusPageHeadingIfLost(injector: Injector): void {
  afterNextRender(
    () => {
      const document = injector.get(DOCUMENT);
      const active = document.activeElement;
      if (active && active !== document.body) return;
      const heading = document.querySelector<HTMLElement>('main h1') ?? document.querySelector<HTMLElement>('h1');
      if (!heading) return;
      heading.tabIndex = -1;
      heading.focus();
    },
    { injector },
  );
}
