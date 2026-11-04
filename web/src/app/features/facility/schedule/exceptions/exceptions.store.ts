// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../../../api/api';
import { createFacilityScheduleException } from '../../../../api/fn/facilities/create-facility-schedule-exception';
import { deleteFacilityScheduleException } from '../../../../api/fn/facilities/delete-facility-schedule-exception';
import { updateFacilityScheduleException } from '../../../../api/fn/facilities/update-facility-schedule-exception';
import type { ScheduleExceptionRequest } from '../../../../api/models/schedule-exception-request';
import type { ScheduleExceptionResponse } from '../../../../api/models/schedule-exception-response';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import type { BookingDecision } from '../../affected-bookings/affected-bookings.store';
import { VersionedElements } from '../../detail/versioned-elements';
import type { HolidayName, ScheduleResponse } from '../schedule.store';
import { ScheduleStore } from '../schedule.store';

export type { ScheduleExceptionResponse };

/** Excepción dun día: pechado, ou aberto cun tramo (P9); o motivo é opcional. */
export interface ScheduleException {
  readonly id: string;
  readonly date: IsoDate;
  readonly open: boolean;
  readonly startMinute: number | null;
  readonly endMinute: number | null;
  readonly reason: string;
  readonly version: number;
}
/** Días seguidos sen tempada, cos dous extremos incluídos. */
export interface DateSpan {
  readonly from: IsoDate;
  readonly to: IsoDate;
}
export type ExceptionBody = Omit<ScheduleExceptionRequest, 'decisions'>;

export function toException(response: ScheduleExceptionResponse): ScheduleException {
  const open = response.open ?? false;
  return {
    id: response.id ?? '',
    date: response.date ?? '',
    open,
    startMinute: open ? (response.startMinute ?? null) : null,
    endMinute: open ? (response.endMinute ?? null) : null,
    reason: response.reason ?? '',
    version: response.version ?? 0,
  };
}

/**
 * Excepcións do horario e días sen tempada (D1-23b). Chegan no mesmo `GET …/schedule` ca as
 * tempadas, así que se len do `ScheduleStore` en cada recarga; cada excepción garda co seu
 * `If-Match` (`VersionedElements`, contrato de estado da ficha). Un por sección (`providers`).
 */
@Injectable()
export class ExceptionsStore {
  private readonly api = inject(Api);
  private readonly schedule = inject(ScheduleStore);
  private readonly elements = new VersionedElements<ScheduleException>();
  private readonly gapList = signal<readonly DateSpan[]>([]);

  /** Por data, como as devolve a API (as novas colócanse no seu sitio). */
  readonly exceptions = computed(() => [...this.elements.list()].sort((a, b) => a.date.localeCompare(b.date)));
  readonly empty = this.elements.empty;
  /** Días sen tempada de hoxe a un ano vista. */
  readonly gaps = this.gapList.asReadonly();
  readonly today = this.schedule.today;

  constructor() {
    this.schedule.onReload((schedule: ScheduleResponse) => {
      this.elements.set((schedule.exceptions ?? []).map(toException));
      this.gapList.set(
        (schedule.gaps ?? []).flatMap((gap) => (gap.from && gap.to ? [{ from: gap.from, to: gap.to }] : [])),
      );
    });
  }

  /** O festivo dese día, ou `null`. */
  holiday(date: IsoDate): Promise<HolidayName | null> {
    return this.schedule.holiday(date);
  }

  get(id: string): ScheduleException | undefined {
    return this.elements.get(id);
  }

  /** Volve ler o horario enteiro: os formularios abertos conservan o escrito. */
  reload(): Promise<void> {
    return this.schedule.reload();
  }

  create(body: ExceptionBody, decisions?: BookingDecision[]): Promise<ScheduleException> {
    const { slug, facilityId } = this.schedule.target();
    return this.elements.create(async () =>
      toException(
        await this.api.invoke(createFacilityScheduleException, { slug, facilityId, body: { ...body, decisions } }),
      ),
    );
  }

  update(id: string, body: ExceptionBody, decisions?: BookingDecision[]): Promise<ScheduleException> {
    const { slug, facilityId } = this.schedule.target();
    return this.elements.save(id, async (ifMatch) =>
      toException(
        await this.api.invoke(updateFacilityScheduleException, {
          slug,
          facilityId,
          exceptionId: id,
          'If-Match': ifMatch,
          body: { ...body, decisions },
        }),
      ),
    );
  }

  remove(id: string, decisions?: BookingDecision[]): Promise<void> {
    const { slug, facilityId } = this.schedule.target();
    return this.elements.remove(id, (ifMatch) =>
      this.api.invoke(deleteFacilityScheduleException, {
        slug,
        facilityId,
        exceptionId: id,
        'If-Match': ifMatch,
        body: decisions ? { decisions } : undefined,
      }),
    );
  }
}
