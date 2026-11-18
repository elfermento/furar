// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { formatDateLong, formatDayMonth } from '../../../shared/ui/calendar/local-date';
import type { ChipMenuOption } from '../../../shared/ui/menu/chip-menu.component';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import type { Translate } from '../../../shared/schedule/schedule';
import {
  maskToWeekdays,
  minutesText,
  weekdaysText,
  weekdaysToMask,
  yearAfter,
} from '../../../shared/schedule/schedule';
import type {
  CreateSeriesRequest,
  OrganizationRowResponse,
  SeriesRequestRequest,
  SeriesResponse,
} from './seasons.store';

export type { Translate } from '../../../shared/schedule/schedule';
export {
  capitalized,
  conflictDates,
  conflictText,
  conflictWhen,
  datesText,
  joinList,
  maskToWeekdays,
  minutesText,
  weekdaysText,
  weekdaysToMask,
  yearAfter,
} from '../../../shared/schedule/schedule';

/** O pago dunha tempada (ADR-0039, punto 11): gratis, pendente ou pagada. */
export type SeriesPayment = SeriesResponse['paymentStatus'];
/** O estado dunha entidade na táboa: con solicitude pendente, en tempada ou sen tempada. */
export type OrganizationStatus = OrganizationRowResponse['status'];

/** As tres cores do pago: as mesmas etiquetas ca o pago dunha reserva (`booking.mine.status.*`). */
export const PAYMENT_TONE: Record<SeriesPayment, TagTone> = { FREE: 'info', PENDING: 'warn', PAID: 'success' };
const PAYMENT_KEY: Record<SeriesPayment, string> = { FREE: 'free', PENDING: 'pending', PAID: 'paid' };
export const PAYMENTS: readonly SeriesPayment[] = ['PENDING', 'PAID', 'FREE'];

/** Clave do texto do pago dunha tempada. */
export function paymentKey(status: SeriesPayment): string {
  return `booking.mine.status.${PAYMENT_KEY[status]}`;
}

/** As opcións do chip-menú «Pago da tempada». */
export function paymentOptions(t: Translate): ChipMenuOption<SeriesPayment>[] {
  return PAYMENTS.map((value) => ({ value, label: t(paymentKey(value)), tone: PAYMENT_TONE[value] }));
}

export const ORGANIZATION_STATUSES: readonly OrganizationStatus[] = ['SEASON', 'REQUEST', 'NONE'];
export const STATUS_TONE: Record<OrganizationStatus, TagTone> = { SEASON: 'success', REQUEST: 'warn', NONE: 'muted' };

/** As partes dunha tempada que van nunha liña: «venres», «18:00–19:30», «do 20 nov. ao 18 xuñ.». */
export function scheduleParts(
  schedule: Pick<SeriesResponse, 'weekdays' | 'startMinute' | 'endMinute' | 'firstDate' | 'lastDate'>,
  lang: string,
  t: Translate,
): string[] {
  return [
    weekdaysText(schedule.weekdays, lang, t('booking.who.and')),
    minutesText(schedule.startMinute, schedule.endMinute, lang),
    t('booking.seasons.range', {
      from: formatDayMonth(schedule.firstDate, lang),
      to: formatDayMonth(schedule.lastDate, lang),
    }),
  ];
}

/** As filas do detalle dunha tempada: días e horas, datas, sesións (e canceladas) e se rematou. */
export function seriesRows(series: SeriesResponse, lang: string, t: Translate): ValueListItem[] {
  const label = (key: string) => t(`booking.seasons.detail.${key}`);
  const [days, hours, range] = scheduleParts(series, lang, t);
  const sessions = series.cancelled
    ? t('booking.seasons.detail.sessionsCancelled', { count: series.sessions, cancelled: series.cancelled })
    : String(series.sessions);
  const rows: ValueListItem[] = [
    { id: 'when', label: label('when'), value: `${days} · ${hours}` },
    { id: 'dates', label: label('dates'), value: range },
    { id: 'sessions', label: label('sessions'), value: sessions },
  ];
  if (series.endedFrom)
    rows.push({ id: 'ended', label: label('endedFrom'), value: formatDateLong(series.endedFrom, lang) });
  return rows;
}

/** A fila «Tempada» do detalle dunha sesión: «luns e mércores · 57 sesións». */
export function sessionSeriesRow(
  series: Pick<SeriesResponse, 'weekdays' | 'sessions'> | null | undefined,
  lang: string,
  t: Translate,
): ValueListItem | null {
  if (!series) return null;
  const days = weekdaysText(series.weekdays, lang, t('booking.who.and'));
  const value = `${days} · ${t('booking.seasons.detail.sessionsCount', { count: series.sessions })}`;
  return { id: 'series', label: t('booking.seasons.detail.season'), value };
}

/** «Pavillón · Pista central · Baloncesto»: a instalación e, en texto, a parte e o uso. */
export function placeParts(item: { facilityName: string; partName?: string | null; useName: string }): string[] {
  return [item.facilityName, item.partName, item.useName].filter((part): part is string => !!part);
}

/** O borrador do formulario dunha tempada (a xestión ou a entidade). */
export interface SeasonDraft {
  readonly organizationId: string | null;
  readonly facilityId: string | null;
  readonly useId: string | null;
  readonly partId: string | null;
  /** Máscara do selector de días (luns = 1). */
  readonly weekdays: number;
  readonly startMinute: number | null;
  readonly endMinute: number | null;
  readonly firstDate: IsoDate | null;
  readonly lastDate: IsoDate | null;
  readonly note: string;
}

export const EMPTY_DRAFT: SeasonDraft = {
  organizationId: null,
  facilityId: null,
  useId: null,
  partId: null,
  weekdays: 0,
  startMinute: null,
  endMinute: null,
  firstDate: null,
  lastDate: null,
  note: '',
};

/** O que xa ten unha tempada (unha solicitude ou outra serie) como borrador. */
export function draftOf(
  item: Pick<
    SeriesRequestRequest,
    'facilityId' | 'useId' | 'partId' | 'weekdays' | 'startMinute' | 'endMinute' | 'firstDate' | 'lastDate'
  > & { organizationId?: string | null },
): SeasonDraft {
  return {
    organizationId: item.organizationId ?? null,
    facilityId: item.facilityId,
    useId: item.useId,
    partId: item.partId ?? null,
    weekdays: weekdaysToMask(item.weekdays),
    startMinute: item.startMinute,
    endMinute: item.endMinute,
    firstDate: item.firstDate,
    lastDate: item.lastDate,
    note: '',
  };
}

/** Os campos do formulario que poden levar erro. */
export type DraftField = 'organization' | 'facility' | 'use' | 'part' | 'weekdays' | 'hours' | 'dates' | 'note';
export type DraftErrors = Partial<Record<DraftField, ControlError>>;

/** O máximo da nota dunha solicitude (ADR-0039, punto 10). */
export const NOTE_MAX = 500;
const required: ControlError = { key: 'errors.validation.required', params: {} };

/**
 * Os erros do borrador antes de mandalo (os mesmos códigos ca a API): o que falta, ningún día,
 * horas que non van de 15 en 15 ou co fin antes do inicio, e datas ao revés ou de máis dun ano.
 */
export function draftErrors(
  draft: SeasonDraft,
  options: { organization: boolean; part: boolean; today: IsoDate },
): DraftErrors {
  const errors: DraftErrors = {};
  if (options.organization && !draft.organizationId) errors.organization = required;
  if (!draft.facilityId) errors.facility = required;
  if (!draft.useId) errors.use = required;
  if (options.part && !draft.partId) errors.part = required;
  if (!draft.weekdays) errors.weekdays = { key: 'errors.booking.series-weekdays-required', params: {} };
  if (draft.startMinute === null || draft.endMinute === null) errors.hours = required;
  else if (draft.endMinute <= draft.startMinute || draft.startMinute % 15 || draft.endMinute % 15) {
    errors.hours = { key: 'errors.booking.series-time-invalid', params: {} };
  }
  if (!draft.firstDate || !draft.lastDate) errors.dates = required;
  else if (
    draft.firstDate < options.today ||
    draft.lastDate < draft.firstDate ||
    yearAfter(draft.firstDate) < draft.lastDate
  ) {
    errors.dates = { key: 'errors.booking.series-range-invalid', params: {} };
  }
  if (draft.note.length > NOTE_MAX) errors.note = { key: 'errors.customfield.too-long', params: { max: NOTE_MAX } };
  return errors;
}

/** O horario que se manda (vista previa, crear, aprobar), se o borrador está completo. */
export function scheduleOf(draft: SeasonDraft): Omit<CreateSeriesRequest, 'skip'> | null {
  const { organizationId, facilityId, useId, startMinute, endMinute, firstDate, lastDate } = draft;
  if (!organizationId || !facilityId || !useId || startMinute === null || endMinute === null) return null;
  if (!firstDate || !lastDate || !draft.weekdays) return null;
  return {
    organizationId,
    facilityId,
    useId,
    ...(draft.partId ? { partId: draft.partId } : {}),
    weekdays: maskToWeekdays(draft.weekdays),
    startMinute,
    endMinute,
    firstDate,
    lastDate,
  };
}

/** A solicitude da entidade (sen entidade: vai na ruta). */
export function requestBody(draft: SeasonDraft): SeriesRequestRequest | null {
  const schedule = scheduleOf({ ...draft, organizationId: draft.organizationId ?? '-' });
  if (!schedule) return null;
  const note = draft.note.trim();
  return {
    facilityId: schedule.facilityId,
    useId: schedule.useId,
    ...(schedule.partId ? { partId: schedule.partId } : {}),
    weekdays: schedule.weekdays,
    startMinute: schedule.startMinute,
    endMinute: schedule.endMinute,
    firstDate: schedule.firstDate,
    lastDate: schedule.lastDate,
    ...(note ? { note } : {}),
  };
}
