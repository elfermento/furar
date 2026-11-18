// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { firstValueFrom } from 'rxjs';
import type { DialogService } from '../ui/dialog/dialog.service';
import type { IncidentReportData } from './incident-report';

/**
 * Abre «Avisar dun problema» ou «Nova incidencia» (folla no móbil). O diálogo cárgase á parte: non
 * entra na carga inicial (orzamento, D27). Devolve se se enviou.
 */
export async function openIncidentReport(dialogs: DialogService, data: IncidentReportData): Promise<boolean> {
  const { IncidentReportDialogComponent } = await import('./incident-report-dialog.component');
  const ref = dialogs.open<boolean, IncidentReportData>(IncidentReportDialogComponent, {
    data,
    sheet: true,
    autoFocus: '[data-incident-type] button',
  });
  return (await firstValueFrom(ref.closed, { defaultValue: false })) ?? false;
}
