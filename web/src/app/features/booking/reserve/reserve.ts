// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, isIsoDate, weekdayIndex } from '../../../shared/ui/calendar/local-date';
import type { AvailableSlotResponse, PublicUseResponse } from './reserve.store';

/**
 * O escollido na páxina de reservar, que vive no URL (ADR-0025 punto 9: sen reter ocos): así
 * «Continuar» leva a Keycloak e a volta (o `returnUrl`) deixa a persoa no mesmo paso co mesmo
 * escollido, e a ligazón do listado (`?data=&hora=`) abre a páxina coa hora marcada.
 */
export interface ReserveSelection {
  /** `?uso=`; sen el, o primeiro uso (ou o que ten a hora pedida). */
  readonly useId: string | null;
  /** `?data=`; sen ela, hoxe segundo a API. */
  readonly date: IsoDate | null;
  /** `?hora=` «18:00», reloxo de parede en Europe/Madrid. */
  readonly time: string | null;
  /** `?franxas=`: franxas seguidas (1 se falta). */
  readonly slots: number;
  /** `?parte=`; sen ela, «Tanto me ten» (Furar dá a primeira libre). */
  readonly partId: string | null;
  /** `?cambiar=`: a reserva que se cambia (modo cambio). */
  readonly change: string | null;
  /** `?reserva=`: a reserva xa confirmada que se mostra. */
  readonly booking: string | null;
  /** `?como=`: reservar como unha entidade da que é responsable (ADR-0039); sen el, como a persoa. */
  readonly organizationId?: string | null;
  /**
   * `?deporte=` (repetible): vén dunha busca por deporte; ábrese o primeiro uso con algún deles e
   * as horas doutros usos só se suxiren entre os que os teñen (ADR-0025, revisión do 2026-11-18).
   */
  readonly sports?: readonly string[];
}

const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;
const ID = /^[\w-]{1,64}$/;

function text(params: Params, key: string): string | null {
  const value = params[key] as unknown;
  const first = Array.isArray(value) ? (value[0] as unknown) : value;
  return typeof first === 'string' && first ? first : null;
}

function id(params: Params, key: string): string | null {
  const value = text(params, key);
  return value && ID.test(value) ? value : null;
}

/** Le o URL; o que non vale (data ou hora mal escritas, franxas fóra de 1–16) queda sen poñer. */
export function readSelection(params: Params): ReserveSelection {
  const date = text(params, 'data');
  const time = text(params, 'hora');
  const slots = Number(text(params, 'franxas') ?? '1');
  return {
    useId: id(params, 'uso'),
    date: isIsoDate(date) ? date : null,
    time: time && TIME.test(time) ? time : null,
    slots: Number.isInteger(slots) && slots >= 1 && slots <= 16 ? slots : 1,
    partId: id(params, 'parte'),
    change: id(params, 'cambiar'),
    booking: id(params, 'reserva'),
    organizationId: id(params, 'como'),
    sports: sportKeys(params['deporte']),
  };
}

/** As claves de `?deporte=` que valen (código ou id); sen ningunha, `undefined`. */
function sportKeys(raw: unknown): string[] | undefined {
  const values = (Array.isArray(raw) ? raw : [raw]).filter(
    (value): value is string => typeof value === 'string' && ID.test(value),
  );
  return values.length ? values : undefined;
}

/** O uso que se abre: o do URL ou, se non hai (ou xa non existe), o primeiro co deporte buscado ou o primeiro. */
export function openedUse<T extends { readonly id: string; readonly sports?: readonly string[] }>(
  uses: readonly T[],
  { useId, sports }: Pick<ReserveSelection, 'useId' | 'sports'>,
): T | null {
  return uses.find((use) => use.id === useId) ?? uses.find((use) => useMatches(use, sports)) ?? uses[0] ?? null;
}

/** Se o uso ten algún dos deportes (sen deportes pedidos, si). */
export function useMatches(
  use: { readonly sports?: readonly string[] },
  sports: readonly string[] | undefined,
): boolean {
  return !sports?.length || !!use.sports?.some((sport) => sports.includes(sport));
}

/** Parámetros do URL dunha escolla: só o que ten valor (sen nada, `/reservar`). */
export function selectionParams(selection: ReserveSelection): Params {
  return {
    uso: selection.useId,
    data: selection.date,
    hora: selection.time,
    franxas: selection.slots > 1 ? selection.slots : null,
    parte: selection.partId,
    cambiar: selection.change,
    reserva: selection.booking,
    como: selection.organizationId ?? null,
    deporte: selection.sports?.length ? [...selection.sports] : undefined,
  };
}

export { formatDay } from '../../../shared/booking-cancel/booking-cancel';

/** «09:30» a partir do minuto do día (reloxo de parede en Europe/Madrid, como o dá a API). */
export function slotTime(minute: number): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const wrapped = ((minute % 1440) + 1440) % 1440;
  return `${pad(Math.floor(wrapped / 60))}:${pad(wrapped % 60)}`;
}

/** Un oco que se pode ofrecer coa duración escollida: as partes ou prazas libres en todas as franxas. */
export interface OfferedSlot {
  readonly slot: AvailableSlotResponse;
  readonly time: string;
  /** Partes libres durante toda a reserva (só usos por partes), na orde das partes. */
  readonly freeParts: readonly string[] | null;
  /** Prazas libres durante toda a reserva (só usos por prazas). */
  readonly freeSeats: number | null;
}

/**
 * Os ocos da API que valen para `slots` franxas seguidas: `maxSlots` chega e, nas partes e
 * prazas, queda algo libre en todas as franxas. Nunca inventa ocos: só filtra os que mandou a API.
 */
export function offeredSlots(
  available: readonly AvailableSlotResponse[],
  slots: number,
  slotMinutes: number,
): OfferedSlot[] {
  const byStart = new Map(available.map((slot) => [new Date(slot.start).getTime(), slot]));
  const offered: OfferedSlot[] = [];
  for (const slot of available) {
    if (slot.maxSlots < slots) continue;
    const span: AvailableSlotResponse[] = [];
    for (let k = 0; k < slots; k++) {
      const next = byStart.get(new Date(slot.start).getTime() + k * slotMinutes * 60_000);
      if (next) span.push(next);
    }
    const covered = span.length === slots ? span : [slot];
    const freeParts = slot.freeParts
      ? slot.freeParts.filter((part) => covered.every((each) => each.freeParts?.includes(part) ?? true))
      : null;
    // A API manda `null` (non `undefined`) fóra das prazas.
    const freeSeats =
      typeof slot.freeSeats === 'number' ? Math.min(...covered.map((each) => each.freeSeats ?? 0)) : null;
    if ((freeParts && !freeParts.length) || freeSeats === 0) continue;
    offered.push({ slot, time: slotTime(slot.startMinute), freeParts, freeSeats });
  }
  return offered;
}

/** As (ata) tres horas libres máis próximas que manda un 409 `booking.slot-taken`. */
export function takenTimes(raw: unknown): string[] {
  return (Array.isArray(raw) ? (raw as AvailableSlotResponse[]) : [])
    .filter((slot) => typeof slot?.startMinute === 'number')
    .slice(0, 3)
    .map((slot) => slotTime(slot.startMinute));
}

/** Unha hora do selector: libre (co seu oco) ou ocupada (ADR-0033: gris, con «Ocupada», elixible). */
export interface HourOption {
  readonly time: string;
  /** Inicio real, como o manda a API. */
  readonly start: string;
  readonly startMinute: number;
  /** O oco libre; `null` se a hora está ocupada. */
  readonly offered: OfferedSlot | null;
  /**
   * Ocupada no uso á vista pero libre noutro que vale: ese uso e, se é por partes,
   * a primeira parte libre a esa hora.
   */
  readonly part?: PartHint;
}

/** Onde segue libre a hora: o uso, a parte (só nos usos por partes) e o nome do uso se non é por partes. */
export interface PartHint {
  readonly useId: string;
  readonly partId: string | null;
  /** `null` nun uso por partes («Só unha parte»); se non, o nome do uso. */
  readonly label: string | null;
}

/** Os ocos libres (dunha franxa) doutro uso que vale. */
export interface PartsUseSlots {
  readonly useId: string;
  /** Nome do uso, se non é por partes. */
  readonly label: string | null;
  readonly offered: readonly OfferedSlot[];
}

/**
 * Marca as horas ocupadas que están libres noutro dos usos que valen (o primeiro, na orde da ficha):
 * ao tocala, pásase a ese uso (coa parte escollida, se vai por partes).
 */
export function withPartHints(hours: readonly HourOption[], partsUses: readonly PartsUseSlots[]): HourOption[] {
  return hours.map((hour) => {
    if (hour.offered) return hour;
    const at = new Date(hour.start).getTime();
    for (const use of partsUses) {
      const slot = use.offered.find((each) => new Date(each.slot.start).getTime() === at);
      if (!slot) continue;
      const partId = slot.freeParts?.[0] ?? null;
      if (slot.freeParts && !partId) continue;
      return { ...hour, part: { useId: use.useId, partId, label: slot.freeParts ? null : use.label } };
    }
    return hour;
  });
}

/**
 * As horas do selector, libres e ocupadas mesturadas por hora. As ocupadas só as di a API
 * (`busy`); unha hora que xa se ofrece como libre non sae dúas veces.
 */
export function hourOptions(
  offered: readonly OfferedSlot[],
  busy: readonly { readonly start: string; readonly startMinute: number }[],
): HourOption[] {
  const free = offered.map((slot) => ({
    time: slot.time,
    start: slot.slot.start,
    startMinute: slot.slot.startMinute,
    offered: slot,
  }));
  const taken = new Set(free.map((option) => option.start));
  const occupied = busy
    .filter((slot) => !taken.has(slot.start))
    .map((slot) => ({
      time: slotTime(slot.startMinute),
      start: slot.start,
      startMinute: slot.startMinute,
      offered: null,
    }));
  return [...free, ...occupied].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime());
}

/**
 * Último día que se pode escoller: hoxe máis a antelación máxima do uso. Sen norma, a API manda
 * `null` (non `undefined`) e non hai límite.
 */
export function lastBookableDay(
  today: IsoDate | null,
  rules: Pick<PublicUseResponse['rules'], 'maxAdvanceDays'> | undefined,
): IsoDate | null {
  const days = rules?.maxAdvanceDays;
  return today && typeof days === 'number' ? addDays(today, days) : null;
}

/** Máximo de opcións de duración que se ofrecen (o selector é dun a catro). */
export const MAX_DURATION_OPTIONS = 4;

/**
 * Duracións que se poden escoller (1…N franxas): N é a duración máxima do uso ou, sen norma, a
 * máis longa que permite algún oco do día; nunca máis de catro opcións.
 */
export function durationOptions(use: PublicUseResponse, available: readonly AvailableSlotResponse[]): number[] {
  const longest = Math.max(1, ...available.map((slot) => slot.maxSlots));
  const max = Math.min(use.rules.maxSlots ?? longest, longest, MAX_DURATION_OPTIONS);
  return Array.from({ length: Math.max(1, max) }, (_, i) => i + 1);
}

/** O uso ábrese ese día da semana e, se só vale unhas datas, cae nalgunha delas. */
export function useOpensOn(use: PublicUseResponse, day: IsoDate): boolean {
  if (!(use.weekdays & (1 << weekdayIndex(day)))) return false;
  return use.availability !== 'DATES' || use.periods.some((period) => period.from <= day && day <= period.to);
}

/** Importe total en céntimos (prezo × franxas; nas prazas, por persoa); `null` se non é de pago. */
export function totalCents(use: PublicUseResponse, slots: number): number | null {
  return use.price.kind === 'PAID' && typeof use.price.amountCents === 'number' ? use.price.amountCents * slots : null;
}
