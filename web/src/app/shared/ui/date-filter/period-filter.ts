// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../calendar/local-date';
import { addDays, addMonths, startOfMonth, startOfWeek } from '../calendar/local-date';

/** Período dun panel de cifras (`?periodo=`), en galego coma as rutas (ADR-0003). */
export type PeriodKey = 'semana' | 'mes' | 'trimestre' | 'ano' | 'datas';

/** Os atallos, na orde do panel: a semana, o mes, o trimestre e o ano naturais en curso. */
export const PERIOD_PRESETS = ['semana', 'mes', 'trimestre', 'ano'] as const;
export type PeriodPreset = (typeof PERIOD_PRESETS)[number];

/** O período escollido: un atallo (sen datas) ou `datas` co rango. */
export interface PeriodValue {
  readonly period: PeriodKey;
  /** Só con `datas`. */
  readonly from: IsoDate | null;
  readonly to: IsoDate | null;
}

/**
 * O rango natural que contén `today` (Europe/Madrid), o mesmo ca a API: semana ISO de luns a
 * domingo, mes, trimestre e ano naturais.
 */
export function presetRange(preset: PeriodPreset, today: IsoDate): { from: IsoDate; to: IsoDate } {
  switch (preset) {
    case 'semana': {
      const from = startOfWeek(today);
      return { from, to: addDays(from, 6) };
    }
    case 'mes': {
      const from = startOfMonth(today);
      return { from, to: addDays(addMonths(from, 1), -1) };
    }
    case 'trimestre': {
      const month = Math.floor((Number(today.slice(5, 7)) - 1) / 3) * 3 + 1;
      const from = `${today.slice(0, 4)}-${String(month).padStart(2, '0')}-01` as IsoDate;
      return { from, to: addDays(addMonths(from, 3), -1) };
    }
    case 'ano': {
      const from = `${today.slice(0, 4)}-01-01` as IsoDate;
      return { from, to: `${today.slice(0, 4)}-12-31` as IsoDate };
    }
  }
}

/** As datas que se ven no calendario para un período (as do atallo ou as do rango). */
export function periodRange(value: PeriodValue, today: IsoDate): { from: IsoDate | null; to: IsoDate | null } {
  return value.period === 'datas' ? { from: value.from, to: value.to } : presetRange(value.period, today);
}

/** O período dun rango: o atallo que coincide con el ou `datas`. */
export function periodOf(from: IsoDate, to: IsoDate, today: IsoDate): PeriodValue {
  const preset = PERIOD_PRESETS.find((key) => {
    const range = presetRange(key, today);
    return range.from === from && range.to === to;
  });
  return preset ? { period: preset, from: null, to: null } : { period: 'datas', from, to };
}

export function samePeriod(a: PeriodValue, b: PeriodValue): boolean {
  return a.period === b.period && a.from === b.from && a.to === b.to;
}
