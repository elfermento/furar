// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { recordIncident } from '../../api/fn/incidents/record-incident';
import { reportIncident } from '../../api/fn/incidents/report-incident';
import type { IncidentResponse } from '../../api/models/incident-response';
import type { ReportIncidentRequest } from '../../api/models/report-incident-request';

/** Que pasa: o tipo dunha incidencia. */
export type IncidentType = ReportIncidentRequest['type'];

export interface IncidentReportBody {
  readonly facilityId: string;
  readonly bookingId?: string;
  readonly type: IncidentType;
  readonly description: string;
  readonly photos: readonly File[];
}

/**
 * A única porta ao cliente xerado para avisar dun problema (ADR-0036): a veciñanza
 * (`reportIncident`, con sesión) ou o persoal («Nova incidencia», `recordIncident`). Multipart.
 */
@Injectable({ providedIn: 'root' })
export class IncidentReportStore {
  private readonly api = inject(Api);

  async report(slug: string, body: IncidentReportBody): Promise<string> {
    const created = await this.api.invoke(reportIncident, {
      slug,
      body: {
        facilityId: body.facilityId,
        ...(body.bookingId ? { bookingId: body.bookingId } : {}),
        type: body.type,
        description: body.description,
        photos: [...body.photos],
      },
    });
    return created.id;
  }

  record(slug: string, body: IncidentReportBody): Promise<IncidentResponse> {
    return this.api.invoke(recordIncident, {
      slug,
      body: { facilityId: body.facilityId, type: body.type, description: body.description, photos: [...body.photos] },
    });
  }
}
