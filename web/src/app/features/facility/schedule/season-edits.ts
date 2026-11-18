// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';
import type { Subscription } from 'rxjs';
import type { Season } from './schedule.store';
import { DEFAULT_HOURS, SeasonEditor, overlapOf, sameWeek } from './season-editor';

/**
 * As tempadas no modo edición da ficha (especificación, bloque 2, «Horario»): as gardadas, cada
 * unha co seu `SeasonEditor` (as mesmas tarxetas ca no asistente), as novas e as que se van
 * borrar. Nada vai á API ata que a barra garda; Cancelar bota todo.
 */
export class SeasonEdits {
  private readonly items = signal<readonly SeasonEditor[]>([]);
  private readonly gone = signal<ReadonlySet<string>>(new Set());
  private readonly tick = signal(0);
  private readonly subscriptions = new Map<string, Subscription>();
  private next = 0;

  /** Na orde na que se ven: as gardadas por datas (coma na API) e as novas ao final. */
  readonly list = this.items.asReadonly();
  /** Gardadas que se borrarán ao gardar. */
  readonly removed = this.gone.asReadonly();
  /** Xa se intentou gardar: os erros, á vista. */
  readonly tried = signal(false);
  readonly dirty = computed(() => {
    this.tick();
    return this.gone().size > 0 || this.items().some((season) => season.dirty);
  });

  /** Entra en edición coas gardadas. */
  start(seasons: readonly Season[]): void {
    this.clear();
    this.set(seasons.map((season) => new SeasonEditor(season.id, season)));
  }

  clear(): void {
    this.set([]);
    this.gone.set(new Set());
    this.tried.set(false);
  }

  /** «Engadir tempada»: sen nome nin datas, co horario da última. */
  add(): SeasonEditor {
    const season = new SeasonEditor(
      `new-${this.next++}`,
      { name: '', startDate: '', endDate: null, week: sameWeek(DEFAULT_HOURS) },
      { added: true, hasEnd: true },
    );
    const last = this.items().at(-1);
    if (last) season.copyHours(last);
    this.set([...this.items(), season]);
    return season;
  }

  /** Quita unha tempada: unha nova desaparece; unha gardada bórrase ao gardar. */
  remove(season: SeasonEditor): void {
    if (season.id) this.gone.update((ids) => new Set(ids).add(season.id as string));
    this.set(this.items().filter((other) => other !== season));
  }

  /** «Empezar co horario de» outra tempada. */
  copy(season: SeasonEditor, from: SeasonEditor): void {
    season.copyHours(from);
    this.tick.update((n) => n + 1);
  }

  /** Xa se borrou na API. */
  forget(id: string): void {
    this.gone.update((ids) => new Set([...ids].filter((other) => other !== id)));
  }

  /** Marca os erros de todas; falso se algunha ten ou se pisan (unha pechada enteira vale). */
  validate(): boolean {
    const list = this.items();
    for (const season of list) season.touchAll();
    this.tried.set(true);
    this.tick.update((n) => n + 1);
    return list.every((season) => season.ok(false) && !overlapOf(list, season));
  }

  /** Cambia a lista: «sucio» segue os formularios das que quedan. */
  private set(list: readonly SeasonEditor[]): void {
    for (const [key, subscription] of this.subscriptions) {
      if (!list.some((season) => season.key === key)) {
        subscription.unsubscribe();
        this.subscriptions.delete(key);
      }
    }
    for (const season of list) {
      if (!this.subscriptions.has(season.key)) {
        this.subscriptions.set(
          season.key,
          season.all.events.subscribe(() => this.tick.update((n) => n + 1)),
        );
      }
    }
    this.items.set(list);
  }
}
