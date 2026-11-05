// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { exportBookings } from '../../../api/fn/bookings/export-bookings';
import { downloadBlob } from '../../../shared/ui/export-menu/csv';
import type { ExportParams } from './export-bookings';
import { exportFileName } from './export-bookings';

/**
 * «Descargar CSV» (maqueta 60): a API fai o CSV e mándao por tandas (non se piden as páxinas da
 * lista unha a unha); aquí gárdase co nome do concello e do período.
 */
@Injectable()
export class ExportBookingsStore {
  private readonly api = inject(Api);
  private readonly document = inject(DOCUMENT);

  /** Pide o CSV e gárdao no navegador; devolve o nome do ficheiro. */
  async download(params: ExportParams): Promise<string> {
    const blob = await this.api.invoke(exportBookings, {
      ...params,
      ...(params.facilityId ? { facilityId: [...params.facilityId] } : {}),
      ...(params.status ? { status: [...params.status] } : {}),
    });
    const file = exportFileName(params.slug, params.from, params.to);
    downloadBlob(this.document, file, blob);
    return file;
  }
}
