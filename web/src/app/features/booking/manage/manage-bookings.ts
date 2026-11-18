// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays } from '../../../shared/ui/calendar/local-date';
import type { ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { foldText } from '../../../shared/ui/combobox/combobox-search';
import type { FilterChipTone } from '../../../shared/ui/filter-chip/filter-chip.directive';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import type {
  BookingSort,
  BookingStatus,
  ListParams,
  ManagedBookingDetailResponse,
  ManagedBookingResponse,
  PaymentStatus,
} from './manage-bookings.store';

export type {
  BookingSort,
  BookingStatus,
  CancelChannel,
  ManagedBookingDetailResponse,
  ManagedBookingResponse,
  PaymentStatus,
} from './manage-bookings.store';

/** «Estado» nos filtros: as confirmadas ou as canceladas (pola persoa ou polo concello). */
export type StateFilter = 'CONFIRMED' | 'CANCELLED';

/** O que se filtra na lista («Filtros»): baleiro = todo. */
export interface BookingFilters {
  readonly sort: BookingSort;
  /** Primeiro día (incluído, Europe/Madrid) ou `null`. */
  readonly from: IsoDate | null;
  /** Último día (incluído) ou `null`: sen el, «desde» `from`. */
  readonly to: IsoDate | null;
  readonly facilityIds: readonly string[];
  readonly payments: readonly PaymentStatus[];
  readonly states: readonly StateFilter[];
}

/** Sen filtros: por día e hora, a opción marcada por defecto. */
export const NO_FILTERS: BookingFilters = {
  sort: 'START',
  from: null,
  to: null,
  facilityIds: [],
  payments: [],
  states: [],
};

/** Pestanas de «Cobros» (`GET …/bookings/summary`). */
export type PaymentsTab = 'pendingPayment' | 'toRefund' | 'cancelled';
export const PAYMENTS_TABS: readonly PaymentsTab[] = ['pendingPayment', 'toRefund', 'cancelled'];

/** De que vén a lista: «Ver máis» dunha sección, os filtros ou a busca (o título da lista). */
export type ListOrigin = 'latest' | PaymentsTab | 'upcoming' | 'results';

/** Días de «Cobros» (pendentes e canceladas): os da API (`COLLECTION_DAYS`), hoxe incluído. */
export const COLLECTION_DAYS = 30;

/** Opcións de «Pago», co ton da paleta de estados: Pendente, Pagada, A devolver, Devolta e Gratis. */
export const PAYMENT_OPTIONS: readonly { readonly value: PaymentStatus; readonly tone: FilterChipTone }[] = [
  { value: 'PENDING', tone: 'warn' },
  { value: 'PAID', tone: 'success' },
  { value: 'TO_REFUND', tone: 'danger' },
  { value: 'REFUNDED', tone: 'muted' },
  { value: 'NONE', tone: 'info' },
];

/** Opcións de «Estado»: Confirmadas e Canceladas. */
export const STATE_OPTIONS: readonly { readonly value: StateFilter; readonly tone: FilterChipTone }[] = [
  { value: 'CONFIRMED', tone: 'success' },
  { value: 'CANCELLED', tone: 'danger' },
];

const CANCELLED: readonly BookingStatus[] = ['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY'];

/** Os filtros de «Ver máis» de cada sección: os mesmos criterios cos que a API conta «Cobros». */
export function filtersFor(origin: ListOrigin, today: IsoDate): BookingFilters {
  const window = { from: today, to: addDays(today, COLLECTION_DAYS - 1) };
  switch (origin) {
    case 'latest':
      return { ...NO_FILTERS, sort: 'RECENT' };
    case 'pendingPayment':
      return { ...NO_FILTERS, ...window, payments: ['PENDING'], states: ['CONFIRMED'] };
    case 'toRefund':
      return { ...NO_FILTERS, payments: ['TO_REFUND'] };
    case 'cancelled':
      return { ...NO_FILTERS, ...window, states: ['CANCELLED'] };
    default:
      return NO_FILTERS;
  }
}

/** Cantos filtros hai postos (o número do botón «Filtros» e un chip por cada un). */
export function activeCount(filters: BookingFilters): number {
  return (filters.from ? 1 : 0) + filters.facilityIds.length + filters.payments.length + filters.states.length;
}

/**
 * Os parámetros de `GET …/bookings`. Sen filtros nin busca, as próximas: desde hoxe, por día e
 * hora (as pasadas, cun filtro de datas).
 */
export function listParams(slug: string, filters: BookingFilters, q: string, today: IsoDate): ListParams {
  const search = q.trim();
  const upcoming = !search && activeCount(filters) === 0 && filters.sort === 'START';
  const states = filters.states.flatMap((state) => (state === 'CONFIRMED' ? ['CONFIRMED' as const] : CANCELLED));
  return {
    slug,
    sort: filters.sort,
    ...(filters.from ? { from: filters.from } : upcoming ? { from: today } : {}),
    ...(filters.to ? { to: filters.to } : {}),
    ...(filters.facilityIds.length ? { facilityId: [...filters.facilityIds] } : {}),
    ...(filters.payments.length ? { payment: [...filters.payments] } : {}),
    ...(states.length ? { status: states } : {}),
    ...(search ? { q: search } : {}),
  };
}

/** Unha instalación que se pode escoller nos filtros e na descarga. */
export interface FacilityChoice {
  readonly id: string;
  readonly name: string;
}

/**
 * O que precisa `furar-multi-combobox` para escoller instalacións polo id: buscar polo nome (sen
 * acentos nin maiúsculas), o nome de cada id e o total para o anuncio.
 */
export function facilityPicker(
  facilities: readonly FacilityChoice[],
  t: (key: string, params?: Record<string, unknown>) => string,
) {
  const names = new Map(facilities.map((f) => [f.id, f.name]));
  const source: ComboboxSource<string> = async (query) => {
    const wanted = foldText(query);
    return facilities.filter((f) => foldText(f.name).includes(wanted)).map((f) => ({ value: f.id, label: f.name }));
  };
  return {
    source,
    key: (id: string) => id,
    display: (id: string) => names.get(id) ?? id,
    countText: (count: number) =>
      t('booking.manage.filters.' + (count === 1 ? 'facilityCountOne' : 'facilityCountOther'), { count }),
  };
}

/** Engade ou quita un valor dunha lista de filtro, na orde das opcións. */
export function toggle<T>(list: readonly T[], value: T, order: readonly T[]): T[] {
  const next = list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
  return order.filter((item) => next.includes(item));
}

/** Atallos do filtro de datas. */
// O filtro de datas vive en shared/ui (unha feature non importa outra): aquí só se reexporta.
export type { DatePreset } from '../../../shared/ui/date-filter/date-filter';
export { dateFilterText, pickDay, presetRange, shortDate } from '../../../shared/ui/date-filter/date-filter';
import { dateFilterText } from '../../../shared/ui/date-filter/date-filter';

/** «hoxe, lun 26 · 19:00–20:00» (Europe/Madrid); mañá tamén co seu nome. */
export function manageWhen(
  booking: Pick<ManagedBookingResponse, 'start' | 'end'>,
  lang: string,
  today: IsoDate,
  relative: { today: string; tomorrow: string },
): string {
  const start = inMadrid(booking.start);
  const end = inMadrid(booking.end);
  const [weekday, day] = formatDay(start.date, lang, 'short').split(' ');
  const name = `${weekday} ${day}`;
  const prefix = start.date === today ? relative.today : start.date === addDays(today, 1) ? relative.tomorrow : '';
  return `${prefix ? `${prefix}, ${name}` : name} · ${start.time}–${end.time}`;
}

/** «hai 5 min», «hai 3 días»: canto hai que se reservou (Intl.RelativeTimeFormat). */
export function agoText(instant: string, lang: string, now: Date = new Date()): string {
  const seconds = Math.round((new Date(instant).getTime() - now.getTime()) / 1000);
  const format = relativeFormat(lang);
  const abs = Math.abs(seconds);
  if (abs < 60) return format(0, 'minute');
  if (abs < 3600) return format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return format(Math.round(seconds / 3600), 'hour');
  // Os días, enteiros («hai 3 días»; «onte» con `numeric: 'auto'`).
  return format(Math.round(seconds / 86400), 'day');
}

type RelativeUnit = 'minute' | 'hour' | 'day';

/**
 * `Intl.RelativeTimeFormat` (curto para minutos e horas, longo para días, `numeric: 'auto'`).
 * Chrome e Chromium non traen datos `gl` e caían en inglés («this minute»): só para ese caso, os
 * mesmos textos de CLDR van aquí (como os nomes de días e meses en `local-date`).
 */
function relativeFormat(lang: string): (value: number, unit: RelativeUnit) => string {
  if (lang !== 'gl' || Intl.RelativeTimeFormat.supportedLocalesOf([lang]).length > 0) {
    const short = new Intl.RelativeTimeFormat(lang, { numeric: 'auto', style: 'short' });
    const long = new Intl.RelativeTimeFormat(lang, { numeric: 'auto' });
    return (value, unit) => (unit === 'day' ? long : short).format(value, unit);
  }
  return galicianRelative;
}

/** «este minuto», «hai 5 min», «hai 2 h», «onte», «antonte», «hai 3 días» (e en futuro, «en 5 min»). */
export function galicianRelative(value: number, unit: RelativeUnit): string {
  if (value === 0) return unit === 'day' ? 'hoxe' : unit === 'hour' ? 'esta hora' : 'este minuto';
  if (unit === 'day' && value === -1) return 'onte';
  if (unit === 'day' && value === -2) return 'antonte';
  if (unit === 'day' && value === 1) return 'mañá';
  if (unit === 'day' && value === 2) return 'pasadomañá';
  const n = Math.abs(value);
  const amount = unit === 'minute' ? `${n} min` : unit === 'hour' ? `${n} h` : `${n} ${n === 1 ? 'día' : 'días'}`;
  return value < 0 ? `hai ${amount}` : `en ${amount}`;
}

/** Cobros que adiantan (a acción principal) e os que desfán. */
export const FORWARD_PAYMENTS: readonly PaymentStatus[] = ['PAID', 'REFUNDED'];

/** As accións de cobro do detalle, na orde: primeiro a que adianta, despois a que desfai. */
export function paymentActions(detail: ManagedBookingDetailResponse): { target: PaymentStatus; undo: boolean }[] {
  return [...detail.paymentTargets]
    .filter((target) => target !== 'NONE')
    .map((target) => ({ target, undo: !FORWARD_PAYMENTS.includes(target) }))
    .sort((a, b) => Number(a.undo) - Number(b.undo));
}

/** Un filtro posto (chip coa ✕ na lista): `id` di que quitar e o texto vén traducido. */
export interface ActiveFilter {
  readonly id: string;
  readonly label: string;
}

/**
 * Os filtros postos, un por valor e na orde do diálogo: datas, instalacións, pago e estado.
 * `translate` recibe a clave baixo `booking.manage.filters.`.
 */
export function activeFilters(
  filters: BookingFilters,
  facilities: readonly { readonly id: string; readonly name: string }[],
  today: IsoDate,
  translate: (key: string, params?: Record<string, string>) => string,
): ActiveFilter[] {
  const chips: ActiveFilter[] = [];
  const dates = dateFilterText(filters.from, filters.to, today);
  if (dates) chips.push({ id: 'dates', label: translate(`dates.${dates.key}`, dates.params) });
  for (const id of filters.facilityIds) {
    const name = facilities.find((facility) => facility.id === id)?.name;
    if (name) chips.push({ id: `facility:${id}`, label: name });
  }
  for (const value of filters.payments)
    chips.push({ id: `payment:${value}`, label: translate(`paymentOption.${value}`) });
  for (const value of filters.states) chips.push({ id: `state:${value}`, label: translate(`stateOption.${value}`) });
  return chips;
}

/** Os filtros sen o do chip `id` (o da ✕). */
export function withoutFilter(filters: BookingFilters, id: string): BookingFilters {
  const [kind, value] = id.split(':');
  switch (kind) {
    case 'dates':
      return { ...filters, from: null, to: null };
    case 'facility':
      return { ...filters, facilityIds: filters.facilityIds.filter((item) => item !== value) };
    case 'payment':
      return { ...filters, payments: filters.payments.filter((item) => item !== value) };
    default:
      return { ...filters, states: filters.states.filter((item) => item !== value) };
  }
}

/** Os ids dos títulos da portada (as últimas e os cobros) e da lista. */
export function sectionIds(id: number): { latest: string; payments: string; list: string } {
  return {
    latest: `furar-manage-${id}-latest`,
    payments: `furar-manage-${id}-payments`,
    list: `furar-manage-${id}-list`,
  };
}
