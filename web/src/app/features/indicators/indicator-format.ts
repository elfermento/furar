// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import {
  GL_MONTHS,
  GL_SHORT_MONTHS,
  GL_SHORT_WEEKDAYS,
  GL_WEEKDAYS,
  galicianFallback,
  toUtc,
} from '../../shared/ui/calendar/local-date';
import { MADRID_ZONE, formatInstant } from '../../shared/ui/calendar/madrid-time';

/** Zona de todas as datas dos indicadores (ADR-0026: días, semanas e horas en hora de Galicia). */
const ZONE = MADRID_ZONE;

/** Signo menos tipográfico (U+2212): «−3 %», non o guión. */
const MINUS = '\u2212';

/**
 * Cifras, porcentaxes, euros e datas no idioma de quen le, con `Intl` e `timeZone: 'Europe/Madrid'`
 * (plan do dominio 3, §4). Os números en galego levan o formato de `es`; sen datos `gl` no
 * navegador, os nomes de meses e días saen das táboas de arriba. Puro: os specs
 * constrúeno co idioma que queiran.
 */
export class IndicatorFormats {
  private readonly fallback: boolean;
  private readonly numberLocale: string;
  private readonly integerFormat: Intl.NumberFormat;
  private readonly percentFormat: Intl.NumberFormat;
  private readonly eurosFormat: Intl.NumberFormat;
  private readonly exactFormat: Intl.NumberFormat;

  constructor(readonly lang: string) {
    this.fallback = galicianFallback(lang);
    // Os números en galego van co formato de `es` (punto de miles e coma decimal, coma en money.ts):
    // o `gl` de CLDR agrupa cun espazo e Chrome nin o ten, así sae igual en todos os navegadores.
    this.numberLocale = lang === 'gl' ? 'es' : lang;
    // Miles sempre agrupados («2.946», non «2946» coma o `es` de CLDR); a lib de TS aínda non ten
    // o valor 'always' (ES2023), que xa entenden os navegadores.
    const grouping = { useGrouping: 'always' as unknown as boolean };
    this.integerFormat = new Intl.NumberFormat(this.numberLocale, { maximumFractionDigits: 0, ...grouping });
    this.percentFormat = new Intl.NumberFormat(this.numberLocale, {
      style: 'percent',
      maximumFractionDigits: 0,
      ...grouping,
    });
    const currency = { style: 'currency', currency: 'EUR', ...grouping } as const;
    this.eurosFormat = new Intl.NumberFormat(this.numberLocale, { ...currency, maximumFractionDigits: 0 });
    this.exactFormat = new Intl.NumberFormat(this.numberLocale, { ...currency, minimumFractionDigits: 2 });
  }

  /** «1.234». */
  integer(value: number): string {
    return this.integerFormat.format(value);
  }

  /** «54 %» a partir dunha proporción (0,54). */
  percent(ratio: number): string {
    return this.percentFormat.format(ratio);
  }

  /** «54 %» a partir dun valor xa en porcentaxe (54), o que levan os eixes dos gráficos. */
  percentValue(value: number): string {
    return this.percentFormat.format(value / 100);
  }

  /** «618 h» (minutos → horas enteiras). */
  hours(minutes: number): string {
    return `${this.integer(minutes / 60)}\u00a0h`;
  }

  /** «2.946 €»: euros enteiros (cifras, eixes e frases). */
  euros(cents: number): string {
    return this.eurosFormat.format(Math.round(cents / 100));
  }

  /** «2.946,50 €»: co céntimo (táboas). */
  eurosExact(cents: number): string {
    return this.exactFormat.format(cents / 100);
  }

  /** Cambio co signo: «+8 %», «−3 %» ou «+5 p.p.»; o valor xa vai redondeado. */
  change(value: number, unit: 'percent' | 'points'): string {
    const sign = value > 0 ? '+' : value < 0 ? MINUS : '';
    const abs = this.integer(Math.abs(value));
    return unit === 'percent' ? `${sign}${abs}\u00a0%` : `${sign}${abs}\u00a0p.p.`;
  }

  /** «outubro» (en minúscula, para dentro dunha frase). */
  monthName(iso: IsoDate): string {
    if (this.fallback) return GL_MONTHS[Number(iso.slice(5, 7)) - 1] ?? '';
    return this.date(iso, { month: 'long' });
  }

  /** «Outubro»: o nome do mes como rótulo. */
  monthTitle(iso: IsoDate): string {
    return capitalize(this.monthName(iso), this.lang);
  }

  /** «Outubro de 2026». */
  monthYear(iso: IsoDate): string {
    if (this.fallback) return capitalize(`${this.monthName(iso)} de ${iso.slice(0, 4)}`, this.lang);
    return capitalize(this.date(iso, { month: 'long', year: 'numeric' }), this.lang);
  }

  /** «out.»: o mes curto dos eixes. */
  monthShort(iso: IsoDate): string {
    if (this.fallback) return GL_SHORT_MONTHS[Number(iso.slice(5, 7)) - 1] ?? '';
    return this.date(iso, { month: 'short' });
  }

  /** «26 out.»: día e mes curto (eixes e frases). */
  dayMonth(iso: IsoDate): string {
    if (this.fallback) return `${Number(iso.slice(8, 10))} de ${this.monthShort(iso)}`;
    return this.date(iso, { day: 'numeric', month: 'short' });
  }

  /** «26/10/2026». */
  shortDate(iso: IsoDate): string {
    return this.date(iso, { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  /** Día da semana ISO (1 = luns) curto: «mar.». */
  weekdayShort(isoWeekday: number): string {
    if (this.fallback) return GL_SHORT_WEEKDAYS[isoWeekday - 1] ?? '';
    return this.date(weekdayDate(isoWeekday), { weekday: 'short' });
  }

  /** Día da semana ISO enteiro: «martes». */
  weekdayLong(isoWeekday: number): string {
    if (this.fallback) return GL_WEEKDAYS[isoWeekday - 1] ?? '';
    return this.date(weekdayDate(isoWeekday), { weekday: 'long' });
  }

  /** «28/10/2026, 13:05» en hora de Galicia (a cabeceira impresa), como no resto da web. */
  dateTime(instant: Date): string {
    return formatInstant(instant) ?? '';
  }

  private date(iso: IsoDate, options: Intl.DateTimeFormatOptions): string {
    return new Intl.DateTimeFormat(this.fallback ? 'es' : this.lang, { ...options, timeZone: ZONE }).format(toUtc(iso));
  }
}

/** Un luns calquera e os días seguintes, para o nome do día da semana. */
function weekdayDate(isoWeekday: number): IsoDate {
  return `2026-01-${String(4 + isoWeekday).padStart(2, '0')}`;
}

export function capitalize(text: string, lang: string): string {
  return text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
}
