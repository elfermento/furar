// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
export type { SportChip } from '../../../../shared/facility-search/facility-search';
export {
  hasCoveredChip,
  matchesCovered,
  matchesSports,
  sportChips,
} from '../../../../shared/facility-search/facility-search';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';

/** Vista do listado: reixa de tarxetas ou mapa. */
export type ListView = 'list' | 'map';

/** Filtros do listado, que viven no URL (`?busca=…&deporte=…&deporte=…&cuberta=si&data=…&vista=mapa`). */
export interface ListFilters {
  /** Deportes marcados (chave: código ou id do concello); baleiro, todos. */
  readonly sports: readonly string[];
  /** Só as cubertas. */
  readonly covered: boolean;
  /** Día dos ocos (nunca antes de hoxe). */
  readonly date: IsoDate;
  readonly view: ListView;
  /** Texto escrito no buscador sen escoller suxestión («torre»); baleiro, sen filtro. */
  readonly text: string;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Le os filtros do URL; o que non vale (data pasada ou mal escrita, vista descoñecida) queda no valor por defecto. */
export function readFilters(params: Params, today: IsoDate): ListFilters {
  const raw = params['deporte'] as string | string[] | undefined;
  const sports = [...new Set((Array.isArray(raw) ? raw : raw ? [raw] : []).filter(Boolean))];
  const date = params['data'] as string | undefined;
  const text = params['busca'] as string | string[] | undefined;
  return {
    sports,
    covered: params['cuberta'] === 'si',
    date: date && ISO_DATE.test(date) && date > today ? date : today,
    view: params['vista'] === 'mapa' ? 'map' : 'list',
    text: typeof text === 'string' ? text.trim() : '',
  };
}

/** Parámetros do URL para uns filtros: só o que non é o valor por defecto (sen nada, `/instalacions`). */
export function filterParams(filters: ListFilters, today: IsoDate): Params {
  return {
    deporte: filters.sports.length ? [...filters.sports] : null,
    cuberta: filters.covered ? 'si' : null,
    data: filters.date !== today ? filters.date : null,
    vista: filters.view === 'map' ? 'mapa' : null,
    busca: filters.text.trim() || null,
  };
}

/** «09:30» a partir do minuto do día (reloxo de parede en Europe/Madrid, como o dá a API). */
export { slotTime } from '../../../../shared/facility-card/facility-card';
