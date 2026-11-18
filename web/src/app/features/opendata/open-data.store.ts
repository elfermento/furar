// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { downloadOpenDataClosures } from '../../api/fn/open-data/download-open-data-closures';
import { downloadOpenDataClosuresCatalog } from '../../api/fn/open-data/download-open-data-closures-catalog';
import { downloadOpenDataFacilities } from '../../api/fn/open-data/download-open-data-facilities';
import { downloadOpenDataFacilitiesCatalog } from '../../api/fn/open-data/download-open-data-facilities-catalog';
import { downloadOpenDataOccupancy } from '../../api/fn/open-data/download-open-data-occupancy';
import { downloadOpenDataOccupancyCatalog } from '../../api/fn/open-data/download-open-data-occupancy-catalog';
import { getOpenData } from '../../api/fn/open-data/get-open-data';
import { getOpenDataCatalog } from '../../api/fn/open-data/get-open-data-catalog';
import type { OpenDataSummaryResponse } from '../../api/models/open-data-summary-response';
import type { StrictHttpResponse } from '../../api/strict-http-response';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { downloadBlob } from '../../shared/ui/export-menu/csv';
import type { DatasetKey, DownloadFormat } from './open-data';
import { dispositionName, fileName } from './open-data';

export type OpenDataSummary = OpenDataSummaryResponse;
export type OpenDataCustomField = OpenDataSummaryResponse['customFields'][number];

/** Unha descarga: o conxunto, o formato, o idioma e, na ocupación, as datas. */
export interface DownloadRequest {
  readonly dataset: DatasetKey;
  readonly format: DownloadFormat;
  readonly lang: string;
  readonly range: { from: IsoDate; to: IsoDate } | null;
}

/**
 * A única porta ao cliente xerado dos datos abertos (ADR-0034): o resumo e as descargas, dun
 * concello (`slug`) ou de todos (`null`). A descarga pídese como blob e gárdase cunha ligazón
 * temporal co nome que dá a API: a persoa nunca sae da web.
 */
@Injectable()
export class OpenDataStore {
  private readonly api = inject(Api);
  private readonly document = inject(DOCUMENT);

  summary(slug: string | null): Promise<OpenDataSummary> {
    return slug ? this.api.invoke(getOpenData, { slug }) : this.api.invoke(getOpenDataCatalog);
  }

  /** Pide o ficheiro e gárdao; devolve o nome. Os erros (404, 429, 5xx) chegan como ApiProblem. */
  async download(slug: string | null, request: DownloadRequest): Promise<string> {
    const { dataset, format, lang, range } = request;
    const dates = range ? { desde: range.from, ata: range.to } : {};
    let response: StrictHttpResponse<Blob>;
    if (dataset === 'OCCUPANCY') {
      response = slug
        ? await this.api.invoke$Response(downloadOpenDataOccupancy, { slug, format, lang, ...dates })
        : await this.api.invoke$Response(downloadOpenDataOccupancyCatalog, { format, lang, ...dates });
    } else if (dataset === 'CLOSURES') {
      response = slug
        ? await this.api.invoke$Response(downloadOpenDataClosures, { slug, format, lang })
        : await this.api.invoke$Response(downloadOpenDataClosuresCatalog, { format, lang });
    } else {
      response = slug
        ? await this.api.invoke$Response(downloadOpenDataFacilities, { slug, format, lang })
        : await this.api.invoke$Response(downloadOpenDataFacilitiesCatalog, { format, lang });
    }
    const name =
      dispositionName(response.headers.get('Content-Disposition')) ?? fileName(slug ?? 'todos', dataset, format, range);
    downloadBlob(this.document, name, response.body);
    return name;
  }
}
