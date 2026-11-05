// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, addMonths, startOfMonth } from '../../../shared/ui/calendar/local-date';
import type { BookingFilters, BookingStatus, StateFilter } from './manage-bookings';

/** Meses seguidos que abarca como moito unha descarga (`ExportBookingsUseCase.MAX_MONTHS`). */
export const EXPORT_MAX_MONTHS = 12;

/** O que se descarga: o período (obrigatorio), as instalacións e os estados (baleiro = todo). */
export interface ExportChoice {
  readonly from: IsoDate | null;
  readonly to: IsoDate | null;
  readonly facilityIds: readonly string[];
  readonly states: readonly StateFilter[];
}

/**
 * Ao abrir, os filtros da lista: as súas datas (só «desde», sen o último día) ou, sen datas, o mes
 * de hoxe; as instalacións e os estados tal cal.
 */
export function exportChoice(filters: BookingFilters, today: IsoDate): ExportChoice {
  const month = startOfMonth(today);
  return {
    from: filters.from ?? month,
    to: filters.from ? filters.to : addDays(addMonths(month, 1), -1),
    facilityIds: filters.facilityIds,
    states: filters.states,
  };
}

/** Por que non se pode descargar aínda: falta un día ou o período pasa de doce meses. */
export type ExportRangeError = 'required' | 'tooLong';

/** O mesmo límite ca a API: do 1 de xaneiro ao 31 de decembro vale; ao 1 de xaneiro seguinte, non. */
export function exportRangeError(from: IsoDate | null, to: IsoDate | null): ExportRangeError | null {
  if (!from || !to) return 'required';
  return to < addMonths(from, EXPORT_MAX_MONTHS) ? null : 'tooLong';
}

const CANCELLED: readonly BookingStatus[] = ['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY'];

/** Os parámetros de `GET …/bookings/export`. */
export interface ExportParams {
  readonly slug: string;
  readonly from: IsoDate;
  readonly to: IsoDate;
  readonly facilityId?: string[];
  readonly status?: BookingStatus[];
}

/** Os parámetros da descarga (o período xa validado). */
export function exportParams(slug: string, choice: ExportChoice & { from: IsoDate; to: IsoDate }): ExportParams {
  const status = choice.states.flatMap((state) => (state === 'CONFIRMED' ? ['CONFIRMED' as const] : CANCELLED));
  return {
    slug,
    from: choice.from,
    to: choice.to,
    ...(choice.facilityIds.length ? { facilityId: [...choice.facilityIds] } : {}),
    ...(status.length ? { status } : {}),
  };
}

/** `furar-naron-reservas-2026-11-01-2026-11-30.csv`, o mesmo nome ca o da API. */
export function exportFileName(slug: string, from: IsoDate, to: IsoDate): string {
  return `furar-${slug}-reservas-${from}-${to}.csv`;
}
