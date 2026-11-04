// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatShort, weekdayIndex } from '../../../shared/ui/calendar/local-date';
import { intervalsText, weekdayName } from '../schedule/week-format';
import { DAY_END } from './block-form';
import type { Block } from './blocks.store';

/** «Martes 03/11/2026»: día da semana por `Intl` e data co formato do idioma. */
export function blockDay(date: string, lang: string): string {
  return `${weekdayName(weekdayIndex(date), lang)} ${formatShort(date)}`;
}

/** Todo o día (0:00–24:00, P13): a fila di «Todo o día» no canto das horas. */
export function allDay(block: Pick<Block, 'startMinute' | 'endMinute'>): boolean {
  return block.startMinute === 0 && block.endMinute === DAY_END;
}

/** «8:00 – 12:00» co formato do idioma. */
export function blockHours(block: Pick<Block, 'startMinute' | 'endMinute'>, lang: string): string {
  return intervalsText([block], lang);
}

/** «03/11/2026, 8:00 – 12:00»: o que distingue un bloqueo doutro do mesmo día. */
export function blockWhen(block: Pick<Block, 'date' | 'startMinute' | 'endMinute'>, lang: string): string {
  return `${formatShort(block.date)}, ${blockHours(block, lang)}`;
}
