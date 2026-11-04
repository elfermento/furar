// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from './local-date';
import { addDays, addMonths } from './local-date';

/** Un período ISO 8601 de calendario (`P1Y`, `P18M`, `P90D`), sen horas. */
export interface IsoPeriod {
  readonly years: number;
  readonly months: number;
  readonly days: number;
}

/** Prefixo das claves dos textos: `common.period.yearsOne`, `common.period.monthsOther`… */
const KEYS = 'common.period.';

const PATTERN = /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$/;

/** Le `P3Y`, `P1Y6M`, `P2W`…; `null` se non é un período de calendario. */
export function parseIsoPeriod(text: string | null | undefined): IsoPeriod | null {
  const match = typeof text === 'string' ? PATTERN.exec(text.trim()) : null;
  if (!match || text?.trim() === 'P') return null;
  const [, y, m, w, d] = match;
  return { years: Number(y ?? 0), months: Number(m ?? 0), days: Number(w ?? 0) * 7 + Number(d ?? 0) };
}

/**
 * O texto dun período coa clave de `common.period` e a cantidade: anos se son anos xustos, meses
 * se son meses e días se só son días; `null` se mestura días con meses ou anos (quen o usa pon o
 * seu texto de reserva).
 */
export function periodText(period: IsoPeriod | null): { key: string; count: number } | null {
  if (!period) return null;
  const { years, months, days } = period;
  const plural = (unit: string, count: number) => ({
    key: `${KEYS}${unit}${count === 1 ? 'One' : 'Other'}`,
    count,
  });
  if (days === 0 && months === 0 && years > 0) return plural('years', years);
  if (days === 0 && years * 12 + months > 0) return plural('months', years * 12 + months);
  if (years === 0 && months === 0 && days > 0) return plural('days', days);
  return null;
}

/** O día que hai `period` antes de `day`, coma `Period` en Java (anos e meses, despois días). */
export function minusPeriod(day: IsoDate, period: IsoPeriod): IsoDate {
  return addDays(addMonths(day, -(period.years * 12 + period.months)), -period.days);
}
