// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import type { ActivityScope } from './activity';
import type { ActivityFilters, ActivityRole } from './activity-filters';
import { NO_FILTERS, filtersFromParams, filtersToParams, hasFilters, toggle } from './activity-filters';
import type { ActivityPeriod } from './activity-period.component';
import type { ActivityColumn, ActivityColumnView, DatePreset, FilterChip } from './activity-view';
import { presetRange } from './activity-view';

/** Un filtro dunha columna despois de premer unha opción: suma ou quita; data e resultado, substitúen. */
function toggleFilter(f: ActivityFilters, column: ActivityColumn, value: string, today: IsoDate): ActivityFilters {
  const change: Record<ActivityColumn, () => Partial<ActivityFilters>> = {
    date: () => presetRange(value as DatePreset | '', today),
    person: () => ({ people: toggle(f.people, value) }),
    role: () => ({ roles: toggle(f.roles, value as ActivityRole) }),
    action: () => ({ actions: toggle(f.actions, value) }),
    place: () => ({ places: toggle(f.places, value) }),
    outcome: () => ({ outcome: f.outcome === value ? null : (value as ActivityFilters['outcome']) }),
  };
  return { ...f, ...change[column]() };
}

/** Os filtros sen o dunha columna («Quitar»). */
function clearFilter(f: ActivityFilters, column: ActivityColumn): ActivityFilters {
  const cleared: Record<ActivityColumn, Partial<ActivityFilters>> = {
    date: { from: null, to: null },
    person: { people: [] },
    role: { roles: [] },
    action: { actions: [] },
    place: { places: [] },
    outcome: { outcome: null },
  };
  return { ...f, ...cleared[column] };
}
/** O que se espera tras teclear na busca
 antes de levala ao URL. */
const QUERY_DEBOUNCE_MS = 250;

/**
 * Os filtros do rexistro, no URL: os das cabeceiras e a busca. Cada cambio navega ao mesmo enderezo con outros parámetros; a páxina le
 * `filters` e carga. A busca vai ao URL cando se deixa de teclear.
 */
@Injectable()
export class ActivityFiltersState {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly scope: ActivityScope = this.route.snapshot.data['scope'] === 'platform' ? 'platform' : 'municipality';
  readonly today = madridToday();
  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });
  readonly filters = computed(() => filtersFromParams(this.params(), this.scope));
  /** O que vai á API: sen o texto (fíltrase nas filas) nin, na plataforma, o que ela non ten. */
  readonly server = computed(
    () => {
      const f = this.filters();
      return this.scope === 'platform' ? { ...NO_FILTERS, from: f.from, to: f.to } : { ...f, text: '' };
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );
  /** Hai algún filtro (o baleiro di «con estes filtros»). */
  readonly filtered = computed(() => hasFilters(this.filters()));
  /** O texto da busca mentres se escribe. */
  readonly query = signal(this.filters().text);
  private queryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // O texto do URL (atrás, adiante) volve á caixa, se non se está a escribir.
    effect(() => {
      const text = this.filters().text;
      untracked(() => {
        if (!this.queryTimer) this.query.set(text);
      });
    });
    inject(DestroyRef).onDestroy(() => {
      if (this.queryTimer) clearTimeout(this.queryTimer);
    });
  }

  /** Unha opción dun filtro: suma ou quita; na data e no resultado, substitúe. */
  toggle(column: ActivityColumnView['key'], value: string): void {
    if (column !== 'municipality') this.navigate(toggleFilter(this.filters(), column, value, this.today));
  }

  clear(column: ActivityColumnView['key']): void {
    if (column !== 'municipality') this.navigate(clearFilter(this.filters(), column));
  }

  period(period: ActivityPeriod): void {
    this.navigate({ ...this.filters(), from: period.from, to: period.to });
  }

  /** O «×» dun chip quita o seu valor. */
  remove(chip: FilterChip | undefined): void {
    if (!chip) return;
    if (chip.column === 'date') this.clear('date');
    else this.toggle(chip.column, chip.value);
  }

  /** «Borrar filtros»: as columnas e a busca. */
  clearAll(): void {
    this.query.set('');
    this.navigate(NO_FILTERS);
  }

  typed(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    if (this.queryTimer) clearTimeout(this.queryTimer);
    this.queryTimer = setTimeout(() => {
      this.queryTimer = null;
      this.navigate({ ...this.filters(), text: value });
    }, QUERY_DEBOUNCE_MS);
  }

  private navigate(filters: ActivityFilters): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: filtersToParams(filters) });
  }
}
