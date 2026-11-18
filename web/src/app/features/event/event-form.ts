// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { madridDayMinute, madridInstant } from '../../shared/events/event-format';
import { maskToWeekdays, weekdaysToMask, yearAfter } from '../../shared/schedule/schedule';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { centsToInput, parseCents } from '../../shared/ui/money/money';
import type { EventKind } from './events';
import type { EventRequest, EventResponse } from './events.store';

export const NAME_MAX = 100;
export const DESCRIPTION_MAX = 2000;
export const PLACE_MAX = 200;
export const CAPACITY_MAX = 10_000;
export const PRICE_MAX_CENTS = 100_000;
/** O primeiro ano de nacemento que se admite (ADR-0041, punto 6). */
export const BIRTH_YEAR_MIN = 1900;

export type PlaceKind = 'FACILITY' | 'OTHER';
export type PricePeriod = EventResponse['pricePeriod'];
export const PRICE_PERIODS: readonly PricePeriod[] = ['ONCE', 'MONTH', 'QUARTER', 'SEASON'];

export type EventForm = ReturnType<typeof eventForm>;

const REQUIRED = { required: true };

/** Un número enteiro de 1 a {@link CAPACITY_MAX} ou baleiro (sen límite). */
function capacity(control: AbstractControl<string>): ValidationErrors | null {
  const text = (control.value ?? '').trim();
  if (!text) return null;
  const value = Number(text);
  return Number.isInteger(value) && value >= 1 && value <= CAPACITY_MAX
    ? null
    : { 'event.capacity-invalid': { max: CAPACITY_MAX } };
}

/** Un importe de 0,01 a 1.000,00 € ou baleiro (gratis). */
function price(control: AbstractControl<string>): ValidationErrors | null {
  const text = (control.value ?? '').trim();
  if (!text) return null;
  const cents = parseCents(text);
  return cents !== null && cents >= 1 && cents <= PRICE_MAX_CENTS
    ? null
    : { 'event.price-invalid': { max: PRICE_MAX_CENTS / 100 } };
}

/** Un ano de 1900 ao actual, ou baleiro (sen límite). */
function birthYear(control: AbstractControl<string>): ValidationErrors | null {
  const text = (control.value ?? '').trim();
  if (!text) return null;
  const value = Number(text);
  const now = Number(madridToday().slice(0, 4));
  return Number.isInteger(value) && value >= BIRTH_YEAR_MIN && value <= now
    ? null
    : { 'event.birth-years-invalid': {} };
}

/** Pon ou quita un erro calculado polo grupo (sen tocar os dos validadores propios do campo). */
function mark(control: AbstractControl, error: ValidationErrors | null, keys: readonly string[]): void {
  const others = Object.fromEntries(Object.entries(control.errors ?? {}).filter(([key]) => !keys.includes(key)));
  const next = { ...others, ...(error ?? {}) };
  control.setErrors(Object.keys(next).length ? next : null);
}

const GROUP_KEYS = [
  'required',
  'event.deadline-invalid',
  'booking.series-weekdays-required',
  'booking.series-time-invalid',
  'booking.series-range-invalid',
  'event.birth-years-invalid',
];

/**
 * O que depende doutro campo: a data e a hora (un día) ou o horario (con horario); o lugar
 * escollido (instalación, o seu uso e a parte, ou outro sitio); «Apuntarse ata» non despois do
 * evento; e «Nacidos desde» non despois de «ata».
 */
function whole(group: AbstractControl): ValidationErrors | null {
  const form = group as EventForm;
  const c = form.controls;
  const v = form.getRawValue();
  const scheduled = v.kind === 'SCHEDULED';
  const facility = v.placeKind === 'FACILITY';
  const editingSchedule = scheduled && !v.locked;
  const set = (control: AbstractControl, error: ValidationErrors | null) => mark(control, error, GROUP_KEYS);

  if (!v.locked) {
    set(c.facilityId, facility && !v.facilityId ? REQUIRED : null);
    set(c.place, !facility && !v.place.trim() ? REQUIRED : null);
  }
  set(c.date, !scheduled && !v.locked && !v.date ? REQUIRED : null);
  set(c.minute, !scheduled && !v.locked && v.minute === null ? REQUIRED : null);
  set(c.useId, editingSchedule && facility && !v.useId ? REQUIRED : null);
  set(c.partId, editingSchedule && facility && v.partRequired && !v.partId ? REQUIRED : null);
  set(c.weekdays, editingSchedule && !v.weekdays ? { 'booking.series-weekdays-required': {} } : null);
  set(
    c.startMinute,
    !editingSchedule
      ? null
      : v.startMinute === null || v.endMinute === null
        ? REQUIRED
        : v.endMinute <= v.startMinute || v.startMinute % 15 || v.endMinute % 15
          ? { 'booking.series-time-invalid': {} }
          : null,
  );
  const today = madridToday();
  set(
    c.firstDate,
    !editingSchedule
      ? null
      : !v.firstDate || !v.lastDate
        ? REQUIRED
        : v.firstDate < today || v.lastDate < v.firstDate || yearAfter(v.firstDate) < v.lastDate
          ? { 'booking.series-range-invalid': {} }
          : null,
  );
  const last = scheduled ? v.lastDate : v.date;
  set(
    c.deadline,
    v.deadlineOn && !v.deadline
      ? REQUIRED
      : v.deadlineOn && v.deadline && last && v.deadline > last
        ? { 'event.deadline-invalid': {} }
        : null,
  );
  const from = Number(v.birthYearFrom.trim());
  const to = Number(v.birthYearTo.trim());
  set(
    c.birthYearTo,
    v.birthYearFrom.trim() && v.birthYearTo.trim() && from > to ? { 'event.birth-years-invalid': {} } : null,
  );
  return null;
}

/** «Nome (copia)»: o nome dunha copia, sen pasar do máximo. */
function copyName(name: string): string {
  const suffix = ' (copia)';
  return `${name.slice(0, NAME_MAX - suffix.length)}${suffix}`;
}

/**
 * O formulario do evento: baleiro (alta), co que hai (edición) ou, con `copy`, unha copia sen
 * persoas (o nome con « (copia)» e as datas baleiras se xa pasaron). Ao editar unha actividade, o
 * horario, as datas e o lugar non se cambian (`locked`).
 */
export function eventForm(event: EventResponse | null, copy = false) {
  const today = madridToday();
  const source = event;
  const editing = !!event && !copy;
  const start = source ? madridDayMinute(source.startsAt) : null;
  const deadline = source?.registrationDeadline ? madridDayMinute(source.registrationDeadline).day : null;
  const schedule = source?.schedule ?? null;
  const pastDates = copy && !!schedule && schedule.firstDate < today;
  const pastDay = copy && !!start && start.day < today;
  const name = (text: string | undefined) => (text && copy ? copyName(text) : (text ?? ''));
  return new FormGroup(
    {
      /** Editando: o tipo, o horario e o lugar xa non se cambian. */
      locked: new FormControl(editing, { nonNullable: true }),
      kind: new FormControl<EventKind>(source?.kind ?? 'ONE_DAY', { nonNullable: true }),
      nameGl: new FormControl(name(source?.name.gl), {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(NAME_MAX)],
      }),
      nameEs: new FormControl(name(source?.name.es), {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(NAME_MAX)],
      }),
      descriptionGl: new FormControl(source?.description?.gl ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(DESCRIPTION_MAX)],
      }),
      descriptionEs: new FormControl(source?.description?.es ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(DESCRIPTION_MAX)],
      }),
      date: new FormControl<IsoDate | null>(pastDay || source?.kind === 'SCHEDULED' ? null : (start?.day ?? null)),
      minute: new FormControl<number | null>(start && source?.kind !== 'SCHEDULED' ? start.minute : 600),
      weekdays: new FormControl(schedule ? weekdaysToMask(schedule.weekdays) : 0, { nonNullable: true }),
      startMinute: new FormControl<number | null>(schedule?.startMinute ?? null),
      endMinute: new FormControl<number | null>(schedule?.endMinute ?? null),
      firstDate: new FormControl<IsoDate | null>(pastDates ? null : (schedule?.firstDate ?? null)),
      lastDate: new FormControl<IsoDate | null>(pastDates ? null : (schedule?.lastDate ?? null)),
      capacity: new FormControl(source?.capacity ? String(source.capacity) : '', {
        nonNullable: true,
        validators: [capacity],
      }),
      placeKind: new FormControl<PlaceKind>(source && !source.facility ? 'OTHER' : 'FACILITY', { nonNullable: true }),
      facilityId: new FormControl(source?.facility?.id ?? '', { nonNullable: true }),
      useId: new FormControl(schedule?.useId ?? '', { nonNullable: true }),
      partId: new FormControl(schedule?.partId ?? '', { nonNullable: true }),
      /** O uso escollido vai por partes (dío o diálogo, que coñece os usos). */
      partRequired: new FormControl(false, { nonNullable: true }),
      place: new FormControl(source?.place ?? '', { nonNullable: true, validators: [Validators.maxLength(PLACE_MAX)] }),
      price: new FormControl(source?.priceCents ? centsToInput(source.priceCents) : '', {
        nonNullable: true,
        validators: [price],
      }),
      pricePeriod: new FormControl<PricePeriod>(source?.pricePeriod ?? 'ONCE', { nonNullable: true }),
      birthYearFrom: new FormControl(source?.birthYearFrom ? String(source.birthYearFrom) : '', {
        nonNullable: true,
        validators: [birthYear],
      }),
      birthYearTo: new FormControl(source?.birthYearTo ? String(source.birthYearTo) : '', {
        nonNullable: true,
        validators: [birthYear],
      }),
      deadlineOn: new FormControl(deadline !== null && !copy, { nonNullable: true }),
      deadline: new FormControl<IsoDate | null>(copy ? null : deadline),
      published: new FormControl(source?.published ?? false, { nonNullable: true }),
      approval: new FormControl(source?.approval ?? false, { nonNullable: true }),
    },
    { validators: [whole] },
  );
}

/** «Apuntarse ata» un día: ata o remate dese día ou, nun evento dun día, ata que empeza. */
function deadlineInstant(day: IsoDate, startsAt: string | null): string {
  const end = madridInstant(day, 23 * 60 + 59);
  return startsAt && end > startsAt ? startsAt : end;
}

/** O horario que se manda (vista previa e crear), se está completo; `skip`, os días ocupados. */
export function scheduleRequest(form: EventForm, skip: readonly string[] = []): EventRequest['schedule'] | null {
  const v = form.getRawValue();
  const facility = v.placeKind === 'FACILITY';
  if (!v.weekdays || v.startMinute === null || v.endMinute === null || !v.firstDate || !v.lastDate) return null;
  if (facility && (!v.facilityId || !v.useId || (v.partRequired && !v.partId))) return null;
  return {
    weekdays: maskToWeekdays(v.weekdays),
    startMinute: v.startMinute,
    endMinute: v.endMinute,
    firstDate: v.firstDate,
    lastDate: v.lastDate,
    ...(facility ? { useId: v.useId } : {}),
    ...(facility && v.partId ? { partId: v.partId } : {}),
    ...(skip.length ? { skip: [...skip] } : {}),
  };
}

/** O corpo da petición; nunha actividade, co horario e os días ocupados que se saltan. */
export function eventBody(form: EventForm, skip: readonly string[] = []): EventRequest {
  const v = form.getRawValue();
  const scheduled = v.kind === 'SCHEDULED';
  const other = v.placeKind === 'OTHER';
  const capacityText = v.capacity.trim();
  const description = v.descriptionGl.trim() || v.descriptionEs.trim();
  const startsAt = scheduled ? null : madridInstant(v.date ?? madridToday(), v.minute ?? 0);
  const year = (text: string) => (text.trim() ? Number(text.trim()) : undefined);
  const priceCents = parseCents(v.price) ?? undefined;
  const body: EventRequest = {
    kind: v.kind,
    nameGl: v.nameGl.trim(),
    nameEs: v.nameEs.trim(),
    descriptionGl: description ? v.descriptionGl.trim() : undefined,
    descriptionEs: description ? v.descriptionEs.trim() : undefined,
    capacity: capacityText ? Number(capacityText) : undefined,
    priceCents,
    pricePeriod: scheduled && priceCents ? v.pricePeriod : 'ONCE',
    birthYearFrom: year(v.birthYearFrom),
    birthYearTo: year(v.birthYearTo),
    registrationDeadline: v.deadlineOn && v.deadline ? deadlineInstant(v.deadline, startsAt) : undefined,
    published: v.published,
    approval: v.approval,
  };
  // Editando unha actividade, o horario e o lugar van coma estaban (a API comproba que non cambian).
  return {
    ...body,
    ...(startsAt ? { startsAt } : {}),
    facilityId: other ? undefined : v.facilityId,
    place: other ? v.place.trim() : undefined,
    ...(scheduled ? { schedule: scheduleRequest(form, skip) ?? undefined } : {}),
  };
}
