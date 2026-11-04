// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { Signal } from '@angular/core';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

/**
 * Rexistro das partes da pantalla con cambios sen gardar (D1-05b; plan §1.2.17). Cada
 * `furar-edit-section` dáse de alta co seu sinal `dirty`; a garda `unsavedChangesGuard` pregunta
 * antes de saír da ruta e, só mentres algunha estea sucia, o navegador avisa ao pechar ou
 * recargar a lapela (`beforeunload`). Sen seccións sucias non hai escoita: así non se perde a
 * caché de ida e volta do navegador.
 */
@Injectable({ providedIn: 'root' })
export class UnsavedChangesService {
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly sources = signal<readonly { readonly dirty: Signal<boolean> }[]>([]);

  /** Hai polo menos unha parte con cambios sen gardar. */
  readonly hasUnsaved = computed(() => this.sources().some((source) => source.dirty()));

  constructor() {
    effect((onCleanup) => {
      const target = this.window;
      if (!this.hasUnsaved() || !target) {
        return;
      }
      const warn = (event: BeforeUnloadEvent): void => {
        // O texto do aviso pono o navegador; `returnValue` é para os que aínda o piden.
        event.preventDefault();
        event.returnValue = '';
      };
      target.addEventListener('beforeunload', warn);
      onCleanup(() => target.removeEventListener('beforeunload', warn));
    });
  }

  /** Segue `dirty` ata que se chame á función que devolve (ao destruír quen o rexistrou). */
  track(dirty: Signal<boolean>): () => void {
    const entry = { dirty };
    this.sources.update((list) => [...list, entry]);
    return () => this.sources.update((list) => list.filter((source) => source !== entry));
  }
}
