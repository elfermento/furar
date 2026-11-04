// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * Cambios do tema na páxina: un só `MutationObserver` sobre `style` e `class` de `<html>`, onde
 * ThemeService pon as variables do concello e a impresión marca o seu alcance. `version()` cambia
 * con cada cambio; quen depende das cores (furar-chart) le os tokens de novo e decide se redebuxa.
 */
@Injectable({ providedIn: 'root' })
export class ThemeChanges {
  private readonly count = signal(0);
  readonly version = this.count.asReadonly();

  constructor() {
    const document = inject(DOCUMENT);
    const view = document.defaultView;
    if (!view?.MutationObserver) return;
    const observer = new view.MutationObserver(() => this.count.update((n) => n + 1));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class'] });
    inject(DestroyRef).onDestroy(() => observer.disconnect());
  }
}
