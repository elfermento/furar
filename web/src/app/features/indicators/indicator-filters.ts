// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { isIsoDate, toUtc } from '../../shared/ui/calendar/local-date';
import type { GetIndicators$Params, IndicatorFiltersResponse } from './indicators.store';

/** Período no URL (`?periodo=`), en galego coma as rutas (ADR-0003). */
export type PeriodKey = 'semana' | 'mes' | 'trimestre' | 'ano' | 'datas';

export const PERIOD_KEYS: readonly PeriodKey[] = ['semana', 'mes', 'trimestre', 'ano', 'datas'];

/** Días como moito nun rango libre (P9: un ano). */
export const MAX_CUSTOM_DAYS = 366;

const API_KIND = { semana: 'WEEK', mes: 'MONTH', trimestre: 'QUARTER', ano: 'YEAR', datas: 'CUSTOM' } as const;

/**
 * Os filtros do panel tal como van no URL: `?periodo=semana|mes|trimestre|ano|datas&desde=&ata=
 * &instalacion=&uso=`. O uso vai polo seu nome: sen instalación, os usos co mesmo nome cóntanse
 * xuntos (P6); cunha instalación, só os seus.
 */
export interface IndicatorFilters {
  readonly period: PeriodKey;
  /** Só con `datas`. */
  readonly from: IsoDate | null;
  readonly to: IsoDate | null;
  readonly facilityId: string | null;
  readonly use: string | null;
}

export const DEFAULT_FILTERS: IndicatorFilters = { period: 'mes', from: null, to: null, facilityId: null, use: null };

const text = (value: unknown): string | null => (typeof value === 'string' && value.trim() ? value.trim() : null);

/** Días do rango, contando os dous extremos. */
export function rangeDays(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000) + 1;
}

/** Un rango libre válido: datas reais, en orde e ata 366 días. */
export function validRange(from: unknown, to: unknown): boolean {
  return isIsoDate(from) && isIsoDate(to) && from <= to && rangeDays(from, to) <= MAX_CUSTOM_DAYS;
}

/** Os filtros dun URL; o que non vale cae no valor por defecto (o mes en curso, todo). */
export function filtersFromParams(params: Params): IndicatorFilters {
  const raw = text(params['periodo']);
  let period: PeriodKey = PERIOD_KEYS.includes(raw as PeriodKey) ? (raw as PeriodKey) : 'mes';
  const custom = period === 'datas' && validRange(params['desde'], params['ata']);
  if (period === 'datas' && !custom) period = 'mes';
  return {
    period,
    from: custom ? (params['desde'] as IsoDate) : null,
    to: custom ? (params['ata'] as IsoDate) : null,
    facilityId: text(params['instalacion']),
    use: text(params['uso']),
  };
}

/** O URL duns filtros: sen os valores por defecto (o mes en curso, todas, todos). */
export function filtersToParams(filters: IndicatorFilters): Params {
  return {
    periodo: filters.period === 'mes' ? null : filters.period,
    desde: filters.period === 'datas' ? filters.from : null,
    ata: filters.period === 'datas' ? filters.to : null,
    instalacion: filters.facilityId,
    uso: filters.use,
  };
}

export function sameFilters(a: IndicatorFilters, b: IndicatorFilters): boolean {
  return (
    a.period === b.period && a.from === b.from && a.to === b.to && a.facilityId === b.facilityId && a.use === b.use
  );
}

/** Os usos do filtro (`useId` repetido) que corresponden ao nome escollido. */
export function useIdsFor(filters: IndicatorFilters, options: IndicatorFiltersResponse | null): string[] {
  if (!filters.use || !options) return [];
  return options.uses
    .filter((use) => use.name === filters.use && (!filters.facilityId || use.facilityId === filters.facilityId))
    .map((use) => use.id);
}

/** A petición do panel. */
export function panelParams(slug: string, filters: IndicatorFilters, useIds: readonly string[]): GetIndicators$Params {
  return {
    slug,
    period: API_KIND[filters.period],
    ...(filters.period === 'datas' && filters.from && filters.to ? { from: filters.from, to: filters.to } : {}),
    ...(filters.facilityId ? { facilityId: filters.facilityId } : {}),
    ...(useIds.length ? { useId: [...useIds] } : {}),
  };
}

/** Unha opción dun despregable: o valor (id da instalación ou nome do uso) e o texto. */
export interface FilterOption {
  readonly value: string;
  readonly label: string;
  readonly retired: boolean;
}

const collator = (lang: string) => new Intl.Collator(lang, { sensitivity: 'base', numeric: true });

/** As instalacións, por nome; as de baixa (só se teñen reservas) márcanse. */
export function facilityOptions(options: IndicatorFiltersResponse | null, lang: string): FilterOption[] {
  const compare = collator(lang);
  return (options?.facilities ?? [])
    .map((f) => ({ value: f.id, label: f.name, retired: f.status === 'WITHDRAWN' }))
    .sort((a, b) => compare.compare(a.label, b.label));
}

/**
 * Os usos: sen instalación, un por nome (arquivado só se o están todos os dese nome); cunha
 * instalación, os seus.
 */
export function useOptions(
  options: IndicatorFiltersResponse | null,
  facilityId: string | null,
  lang: string,
): FilterOption[] {
  const byName = new Map<string, boolean>();
  for (const use of options?.uses ?? []) {
    if (facilityId && use.facilityId !== facilityId) continue;
    byName.set(use.name, (byName.get(use.name) ?? true) && use.archived);
  }
  const compare = collator(lang);
  return [...byName]
    .map(([name, archived]) => ({ value: name, label: name, retired: archived }))
    .sort((a, b) => compare.compare(a.label, b.label));
}
