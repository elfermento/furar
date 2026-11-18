// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import { formatDateLong } from '../../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../../shared/ui/calendar/madrid-time';

/** O día dunha valoración en Galicia: «10 de novembro»; doutro ano, «10 de novembro de 2025». */
export function reviewDate(instant: string, lang: string, today: IsoDate): string {
  const day = inMadrid(instant).date;
  const text = formatDateLong(day, lang);
  const year = day.slice(0, 4);
  return year === today.slice(0, 4) && text.endsWith(` de ${year}`) ? text.slice(0, -` de ${year}`.length) : text;
}
