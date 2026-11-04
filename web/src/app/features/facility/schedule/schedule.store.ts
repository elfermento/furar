// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { createFacilitySeason } from '../../../api/fn/facilities/create-facility-season';
import { deleteFacilitySeason } from '../../../api/fn/facilities/delete-facility-season';
import { getFacilitySchedule } from '../../../api/fn/facilities/get-facility-schedule';
import { listPublicHolidays } from '../../../api/fn/facilities/list-public-holidays';
import { updateFacilitySeason } from '../../../api/fn/facilities/update-facility-season';
import type { CreateSeasonRequest } from '../../../api/models/create-season-request';
import type { HolidayNameResponse } from '../../../api/models/holiday-name-response';
import type { PublicHolidayResponse } from '../../../api/models/public-holiday-response';
import type { DayHoursRequest } from '../../../api/models/day-hours-request';
import type { ScheduleResponse } from '../../../api/models/schedule-response';
import type { SeasonResponse } from '../../../api/models/season-response';
import type { UpdateSeasonRequest } from '../../../api/models/update-season-request';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { isIsoDate } from '../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';
import type { LoadState } from '../detail/facility-detail.store';
import { VersionedElements } from '../detail/versioned-elements';

export type { ScheduleResponse, SeasonResponse };
/** Nome dun festivo nos dous idiomas. */
export type HolidayName = Required<HolidayNameResponse>;
export type Day = DayHoursRequest['day'];
/** As 8 filas da semana, na orde da API: de luns a domingo e festivos. */
export const DAYS: readonly Day[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
  'HOLIDAYS',
];
export const HOLIDAYS_ROW = DAYS.indexOf('HOLIDAYS');

export interface Interval {
  readonly startMinute: number;
  readonly endMinute: number;
}
export interface DayHours {
  readonly day: Day;
  readonly intervals: readonly Interval[];
}
/** Tempada xa normalizada: sempre as 8 filas, na orde de `DAYS`. */
export interface Season {
  readonly id: string;
  readonly name: string;
  readonly startDate: IsoDate;
  /** `null`: a tempada non ten fin. */
  readonly endDate: IsoDate | null;
  readonly version: number;
  readonly week: readonly DayHours[];
}
export type SeasonFields = Pick<CreateSeasonRequest, 'name' | 'startDate' | 'endDate'>;
export type SeasonBody = Omit<UpdateSeasonRequest, 'decisions'>;

export function toSeason(response: SeasonResponse): Season {
  const week = DAYS.map((day) => {
    const row = response.week?.find((hours) => hours.day === day);
    const intervals = (row?.intervals ?? [])
      .map((i) => ({ startMinute: i.startMinute ?? 0, endMinute: i.endMinute ?? 0 }))
      .sort((a, b) => a.startMinute - b.startMinute);
    return { day, intervals };
  });
  return {
    id: response.id ?? '',
    name: response.name ?? '',
    startDate: response.startDate ?? '',
    endDate: response.endDate ?? null,
    version: response.version ?? 0,
    week,
  };
}

/**
 * Horario dunha instalación (D1-23a): as tempadas coa súa semana, cada unha coa súa versión
 * (`VersionedElements`, contrato de estado da ficha), a vixente hoxe, «hoxe» e os festivos do ano.
 * Un por sección (`providers`). É o único sitio da sección que fai HTTP, co cliente xerado.
 */
@Injectable()
export class ScheduleStore {
  private readonly api = inject(Api);
  private readonly elements = new VersionedElements<Season>();
  private readonly loadState = signal<LoadState>('loading');
  private readonly currentId = signal<string | null>(null);
  private readonly todayDate = signal<IsoDate | null>(null);
  private readonly holidayDates = signal<ReadonlySet<IsoDate>>(new Set());
  private where: { slug: string; facilityId: string } | null = null;
  private readonly listeners: ((schedule: ScheduleResponse) => void)[] = [];
  private last: ScheduleResponse | null = null;
  /** Festivos por ano: o do horario chega co `GET …/schedule`; os demais, de `GET /holidays`. */
  private readonly holidayYears = new Map<number, Promise<ReadonlyMap<string, HolidayName>>>();

  /** Por orde de datas, como as devolve a API (as novas colócanse no seu sitio). */
  readonly seasons = computed(() => [...this.elements.list()].sort((a, b) => a.startDate.localeCompare(b.startDate)));
  readonly empty = this.elements.empty;
  readonly state = this.loadState.asReadonly();
  /** Tempada vixente hoxe («Agora»), ou `null`. */
  readonly currentSeasonId = this.currentId.asReadonly();
  readonly today = this.todayDate.asReadonly();
  /** Hoxe é festivo: aplícase a fila «Festivos». */
  readonly todayIsHoliday = computed(() => {
    const today = this.todayDate();
    return !!today && this.holidayDates().has(today);
  });

  async load(slug: string, facilityId: string): Promise<void> {
    this.where = { slug, facilityId };
    this.loadState.set('loading');
    try {
      await this.reload();
      this.loadState.set('ready');
    } catch {
      this.loadState.set('error');
    }
  }

  /** Volve ler o horario: os formularios con cambios conservan o escrito. */
  async reload(): Promise<void> {
    const { slug, facilityId } = this.target();
    const schedule = await this.api.invoke(getFacilitySchedule, { slug, facilityId });
    this.elements.set((schedule.seasons ?? []).map(toSeason));
    this.currentId.set(schedule.currentSeasonId ?? null);
    this.todayDate.set(isIsoDate(schedule.today) ? schedule.today : madridToday());
    this.holidayDates.set(new Set((schedule.holidays ?? []).flatMap((h) => (h.date ? [h.date] : []))));
    if (schedule.year) this.holidayYears.set(schedule.year, Promise.resolve(byDate(schedule.holidays ?? [])));
    this.last = schedule;
    for (const listener of this.listeners) listener(schedule);
  }

  /**
   * As partes da sección que viven no mesmo `GET …/schedule` (excepcións, días sen tempada):
   * reciben o último xa lido e cada recarga.
   */
  onReload(listener: (schedule: ScheduleResponse) => void): void {
    this.listeners.push(listener);
    if (this.last) listener(this.last);
  }

  /** O festivo dese día, ou `null` (tamén se a lista dese ano non se puido ler). */
  async holiday(date: IsoDate): Promise<HolidayName | null> {
    const year = Number(date.slice(0, 4));
    let list = this.holidayYears.get(year);
    if (!list) {
      list = this.api.invoke(listPublicHolidays, { year }).then(byDate);
      this.holidayYears.set(year, list);
      list.catch(() => this.holidayYears.delete(year));
    }
    try {
      return (await list).get(date) ?? null;
    } catch {
      return null;
    }
  }

  get(id: string): Season | undefined {
    return this.elements.get(id);
  }

  /** «+ Tempada»: baleira ou copiando a semana doutra (`copyFrom`). */
  create(body: CreateSeasonRequest): Promise<Season> {
    const { slug, facilityId } = this.target();
    return this.elements.create(async () =>
      toSeason(await this.api.invoke(createFacilitySeason, { slug, facilityId, body })),
    );
  }

  /** Garda a tempada enteira (nome, datas e semana) co `If-Match` da súa última versión. */
  update(id: string, body: SeasonBody, decisions?: BookingDecision[]): Promise<Season> {
    const { slug, facilityId } = this.target();
    return this.elements.save(id, async (ifMatch) =>
      toSeason(
        await this.api.invoke(updateFacilitySeason, {
          slug,
          facilityId,
          seasonId: id,
          'If-Match': ifMatch,
          body: { ...body, decisions },
        }),
      ),
    );
  }

  remove(id: string, decisions?: BookingDecision[]): Promise<void> {
    const { slug, facilityId } = this.target();
    return this.elements.remove(id, (ifMatch) =>
      this.api.invoke(deleteFacilitySeason, {
        slug,
        facilityId,
        seasonId: id,
        'If-Match': ifMatch,
        body: decisions ? { decisions } : undefined,
      }),
    );
  }

  /** Concello e instalación cargados (para os stores que comparten este horario). */
  target(): { slug: string; facilityId: string } {
    if (!this.where) throw new ApiProblem(0, 'common.unexpected', {}, [], undefined, '');
    return this.where;
  }
}

function byDate(holidays: readonly PublicHolidayResponse[]): ReadonlyMap<string, HolidayName> {
  return new Map(
    holidays.flatMap((h) =>
      h.date && h.name ? [[h.date, { gl: h.name.gl ?? '', es: h.name.es ?? '' }] as const] : [],
    ),
  );
}
