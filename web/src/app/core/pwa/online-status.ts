// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/** Estado da conexión para o aviso accesible «sen conexión» (PWA básica, ADR-0014). */
@Injectable({ providedIn: 'root' })
export class OnlineStatus {
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly state = signal(this.window?.navigator.onLine ?? true);
  readonly online = this.state.asReadonly();

  constructor() {
    const update = () => this.state.set(this.window?.navigator.onLine ?? true);
    this.window?.addEventListener('online', update);
    this.window?.addEventListener('offline', update);
    inject(DestroyRef).onDestroy(() => {
      this.window?.removeEventListener('online', update);
      this.window?.removeEventListener('offline', update);
    });
  }
}
