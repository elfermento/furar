// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OpeningDay } from './public-facility.store';

export type { OpeningDay };
export type OpeningSlot = OpeningDay['slots'][number];

/** Agora: aberto ata unha hora, ou pechado e cando abre (índice do día na semana da API). */
export type OpenState =
  | { readonly open: true; readonly closesAt: number }
  | { readonly open: false; readonly next: { readonly day: number; readonly minute: number } | null };

/**
 * Se está aberta agora e ata cando (tramos seguidos contan como un: 9–14 e 14–20 pecha ás 20), ou
 * cando volve abrir dentro da semana. Os instantes veñen da API co cambio de hora xa aplicado.
 */
export function openState(days: readonly OpeningDay[], now: Date): OpenState {
  const time = now.getTime();
  const slots = days.flatMap((day, index) => day.slots.map((slot) => ({ slot, index })));
  const current = slots.findIndex(({ slot }) => Date.parse(slot.startsAt) <= time && time < Date.parse(slot.endsAt));
  if (current >= 0) {
    let last = current;
    while (
      last + 1 < slots.length &&
      slots[last + 1].index === slots[last].index &&
      slots[last + 1].slot.startMinute === slots[last].slot.endMinute
    ) {
      last++;
    }
    return { open: true, closesAt: slots[last].slot.endMinute };
  }
  const next = slots.find(({ slot }) => Date.parse(slot.startsAt) > time);
  return { open: false, next: next ? { day: next.index, minute: next.slot.startMinute } : null };
}

/** Uns días seguidos co mesmo horario (índices na semana da API, extremos incluídos). */
export interface DayGroup {
  readonly from: number;
  readonly to: number;
  readonly slots: readonly OpeningSlot[];
}

const sameHours = (a: readonly OpeningSlot[], b: readonly OpeningSlot[]): boolean =>
  a.length === b.length &&
  a.every((slot, i) => slot.startMinute === b[i].startMinute && slot.endMinute === b[i].endMinute);

/**
 * A semana para a tarxeta: hoxe só (vai en negriña) e o resto xuntando os días seguidos co mesmo
 * horario («Martes a venres»), para non repetir as mesmas horas.
 */
export function groupDays(days: readonly OpeningDay[]): DayGroup[] {
  const groups: DayGroup[] = [];
  days.forEach((day, index) => {
    const last = groups.at(-1);
    if (index > 1 && last && last.to === index - 1 && sameHours(last.slots, day.slots)) {
      groups[groups.length - 1] = { ...last, to: index };
    } else {
      groups.push({ from: index, to: index, slots: day.slots });
    }
  });
  return groups;
}
