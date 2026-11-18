// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';

/** Un cambio pendente: alta (`base` nulo), cambio (con `body`) ou baixa (`body` nulo). */
export interface StagedChange<T, B> {
  readonly key: string;
  readonly base: T | null;
  readonly body: B | null;
  /** O que respondeu a API a última vez que se intentou gardar. */
  readonly problem: ApiProblem | null;
}

/** Como se garda cada cambio. `null`: quen garda volveu atrás (modal de reservas afectadas). */
export interface StagedRunner<T, B> {
  create(body: B): Promise<T | null>;
  update(id: string, body: B): Promise<T | null>;
  remove(id: string): Promise<boolean>;
}

/**
 * Cambios sen gardar dunha lista con versión por elemento (usos, excepcións, bloqueos) no modo
 * edición da ficha: o modal de cada elemento só deixa aquí o que se fixo e nada vai á API ata que
 * a barra garda (`flush`). A lista que se ve é a gardada cos cambios aplicados (`view`). Ao
 * gardar, primeiro as baixas (liberan datas e nomes), despois os cambios e ao final as altas,
 * unha detrás doutra; o que vai ben sae de aquí e o que falla queda co seu problema.
 */
export class StagedElements<T extends { readonly id: string }, B> {
  private readonly changes = signal<ReadonlyMap<string, StagedChange<T, B>>>(new Map());
  private next = 0;

  readonly dirty = computed(() => this.changes().size > 0);
  /** Os cambios que fallaron ao gardar, para o aviso da sección. */
  readonly failed = computed(() => [...this.changes().values()].filter((change) => change.problem));

  /** `preview`: como se ve un elemento co corpo pendente (para a lista e para abrir o modal). */
  constructor(private readonly preview: (body: B, base: T | null, key: string) => T) {}

  /** A lista como quedará: cambios aplicados, baixas fóra e altas ao final. */
  view(list: readonly T[]): T[] {
    const changes = this.changes();
    const kept = list.flatMap((item) => {
      const change = changes.get(item.id);
      if (!change) return [item];
      return change.body === null ? [] : [this.preview(change.body, item, item.id)];
    });
    const created = [...changes.values()]
      .filter((change) => change.base === null && change.body !== null)
      .map((change) => this.preview(change.body as B, null, change.key));
    return [...kept, ...created];
  }

  get(key: string): StagedChange<T, B> | undefined {
    return this.changes().get(key);
  }

  /** Garda o corpo dun elemento (`base` nulo e sen `key`: un novo). Devolve a súa clave. */
  stage(body: B, base: T | null, key?: string): string {
    const id = base?.id ?? key ?? `new-${this.next++}`;
    this.set(id, { key: id, base, body, problem: null });
    return id;
  }

  /** Quita un elemento: un novo desaparece sen máis; un gardado queda para borralo ao gardar. */
  remove(key: string, base: T | null): void {
    if (!base) {
      const next = new Map(this.changes());
      next.delete(key);
      this.changes.set(next);
      return;
    }
    this.set(key, { key, base, body: null, problem: null });
  }

  clear(): void {
    this.changes.set(new Map());
  }

  /** Garda todo; `true` se non quedou nada pendente. */
  async flush(runner: StagedRunner<T, B>): Promise<boolean> {
    const all = [...this.changes().values()];
    const order = [
      ...all.filter((c) => c.body === null),
      ...all.filter((c) => c.body !== null && c.base !== null),
      ...all.filter((c) => c.base === null),
    ];
    let ok = true;
    for (const change of order) {
      try {
        const done =
          change.body === null
            ? await runner.remove(change.key)
            : change.base === null
              ? (await runner.create(change.body)) !== null
              : (await runner.update(change.key, change.body)) !== null;
        if (done) {
          const next = new Map(this.changes());
          next.delete(change.key);
          this.changes.set(next);
        } else {
          ok = false;
        }
      } catch (error) {
        ok = false;
        this.set(change.key, { ...change, problem: asProblem(error) });
      }
    }
    return ok;
  }

  private set(key: string, change: StagedChange<T, B>): void {
    this.changes.set(new Map(this.changes()).set(key, change));
  }
}
