// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoPeriod } from '../../../shared/ui/calendar/iso-period';
import { minusPeriod, parseIsoPeriod, periodText } from '../../../shared/ui/calendar/iso-period';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { formatInstant } from '../../../shared/ui/calendar/madrid-time';
import type { CsvRow } from '../../../shared/ui/export-menu/csv';
import { actionKey, actorView, targetView } from './activity';
import type { ActivityEventResponse, ActivityRetention, ExportBlockedActivityRequest } from './activity.store';

// Conservación e bloqueo do rexistro (ADR-0020, revisión do 2026-10-29; LOPDGDD art. 32).

const ONE_YEAR: IsoPeriod = { years: 1, months: 0, days: 0 };
const THREE_YEARS: IsoPeriod = { years: 3, months: 0, days: 0 };

/** Os prazos lidos; un que non se entenda cae no de por defecto. */
export function retentionPeriods(retention: ActivityRetention | null): { visible: IsoPeriod; retention: IsoPeriod } {
  return {
    visible: parseIsoPeriod(retention?.visible) ?? ONE_YEAR,
    retention: parseIsoPeriod(retention?.retention) ?? THREE_YEARS,
  };
}

/** O texto dun período («1 ano», «18 meses»): a clave e a cantidade, ou un ano se non se pode dicir. */
export function periodLabel(period: IsoPeriod): { key: string; count: number } {
  return periodText(period) ?? { key: 'common.period.yearsOne', count: 1 };
}

/**
 * Os días (Europe/Madrid) que se poden exportar bloqueados: desde o que aínda se garda ata o
 * primeiro que xa non se ve. O servidor recorta igual o período ao tramo bloqueado.
 */
export function blockedDays(
  retention: ActivityRetention | null,
  today: IsoDate,
): { readonly first: IsoDate; readonly last: IsoDate } {
  const periods = retentionPeriods(retention);
  return { first: minusPeriod(today, periods.retention), last: minusPeriod(today, periods.visible) };
}

/** O primeiro día que se ve na lista (o calendario dos filtros non deixa ir máis atrás). */
export function firstVisibleDay(retention: ActivityRetention | null, today: IsoDate): IsoDate {
  return minusPeriod(today, retentionPeriods(retention).visible);
}

/** Para que se exportan os bloqueados (LOPDGDD art. 32.2), na orde do despregable. */
export type DisclosureReason = ExportBlockedActivityRequest['reason'];
export const DISCLOSURE_REASONS: readonly DisclosureReason[] = [
  'COURT_ORDER',
  'PUBLIC_PROSECUTOR',
  'DATA_PROTECTION_AUTHORITY',
  'OPEN_PROCEEDINGS',
];

/** Máximo da referencia (o mesmo ca a API). */
export const REFERENCE_MAX = 80;

/** A referencia sen espazos nos extremos vale: non baleira, curta e sen caracteres de control. */
export function validReference(value: string): boolean {
  const text = value.trim();
  // eslint-disable-next-line no-control-regex
  return text.length > 0 && text.length <= REFERENCE_MAX && !/[\u0000-\u001f\u007f]/.test(text);
}

/** Textos xa traducidos que pide o CSV (quen o chama traduce coa súa clave). */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/**
 * O CSV da exportación dos bloqueados (UTF-8 con BOM, `;`; `toCsv`): unha fila por evento, do máis
 * vello ao máis novo, coa persoa e o seu correo (aquí a identidade bloqueada vese), os roles, a
 * acción, sobre que, o resultado e o identificador da petición.
 */
export function blockedCsv(
  events: readonly ActivityEventResponse[],
  t: Translate,
  facilities: ReadonlyMap<string, string>,
  slug: string | null,
): CsvRow[] {
  const header: CsvRow = [
    t('activity.col.date'),
    t('activity.col.person'),
    t('activity.exportBlocked.csv.email'),
    t('activity.exportBlocked.csv.roles'),
    t('activity.col.action'),
    t('activity.col.target'),
    t('activity.col.outcome'),
    t('activity.exportBlocked.csv.request'),
  ];
  const rows = events.map((event): CsvRow => {
    const actor = actorView(event.actor);
    const key = actionKey(event.action);
    const target = targetView(event, 'municipality', slug, facilities);
    return [
      formatInstant(event.at) ?? event.at,
      actor.name ?? t(actor.nameKey),
      event.actor.email ?? '',
      actor.roleKeys.map((role) => t(role)).join(', '),
      key ? t(key) : t('activity.unknownAction', { code: event.action }),
      [t(target.kindKey), target.text].filter(Boolean).join(': '),
      t('activity.outcome.' + event.outcome),
      event.requestId ?? '',
    ];
  });
  return [header, ...rows];
}
