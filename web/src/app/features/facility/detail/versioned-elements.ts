// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';
import { ifMatch } from './facility-detail.store';

/** Un elemento con versión propia: uso, tempada, excepción ou bloqueo. */
export interface Versioned {
  readonly id: string;
  readonly version: number;
}

/**
 * Elementos dunha sección con versión por elemento (contrato de estado da ficha, D1-21a): cada
 * un garda co seu `If-Match`, e os gardados do mesmo elemento van en cola, sempre coa última
 * versión. Elementos distintos gárdanse á vez. Un erro (412 incluído) non toca a lista: a sección
 * conserva o escrito e decide.
 *
 * Úsano os stores das seccións (usos, horario, bloqueos); a orde da lista é a da API.
 */
export class VersionedElements<T extends Versioned> {
  private readonly items = signal<readonly T[]>([]);
  private readonly tails = new Map<string, Promise<unknown>>();

  readonly list = this.items.asReadonly();
  readonly empty = computed(() => this.items().length === 0);

  /** Lista enteira da API (carga ou recarga). */
  set(items: readonly T[]): void {
    this.items.set(items);
  }

  get(id: string): T | undefined {
    return this.items().find((item) => item.id === id);
  }

  /** Alta: non hai versión que mandar; o novo vai ao final. */
  async create(send: () => Promise<T>): Promise<T> {
    const created = await send();
    this.items.update((items) => [...items, created]);
    return created;
  }

  /** Cambio dun elemento co `If-Match` da súa última versión. */
  save(id: string, send: (ifMatch: string) => Promise<T>): Promise<T> {
    return this.enqueue(id, async () => {
      const saved = await send(this.ifMatchOf(id));
      this.items.update((items) =>
        items.map((item) => (item.id === id && saved.version >= item.version ? saved : item)),
      );
      return saved;
    });
  }

  /** Borrado (ou arquivado que o quita da vista) co `If-Match` da súa última versión. */
  remove(id: string, send: (ifMatch: string) => Promise<unknown>): Promise<void> {
    return this.enqueue(id, async () => {
      await send(this.ifMatchOf(id));
      this.items.update((items) => items.filter((item) => item.id !== id));
    });
  }

  private ifMatchOf(id: string): string {
    const item = this.get(id);
    if (!item) throw new Error(`Elemento descoñecido: ${id}`);
    return ifMatch(item.version);
  }

  private enqueue<R>(id: string, work: () => Promise<R>): Promise<R> {
    const result = (this.tails.get(id) ?? Promise.resolve()).then(work, work);
    const tail = result.catch(() => undefined);
    this.tails.set(id, tail);
    void tail.then(() => {
      if (this.tails.get(id) === tail) this.tails.delete(id);
    });
    return result;
  }
}
