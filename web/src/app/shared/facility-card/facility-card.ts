// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Language } from '../../core/i18n/languages';

/** Os ocos libres dun día dunha instalación (a forma de `FacilityFreeSlotsResponse`). */
export interface FreeSlots {
  readonly freeCount: number;
  /** As primeiras horas libres, en minutos desde a medianoite. */
  readonly firstStarts?: readonly { readonly startMinute: number }[];
}

/** «09:30» desde os minutos do día. */
export function slotTime(startMinute: number): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(Math.floor(startMinute / 60))}:${pad(startMinute % 60)}`;
}

/** «700 m» (en treitos de 50 m) ou «2,3 km» (cun decimal ata 10 km), co formato do idioma. */
export function formatDistance(km: number, lang: Language): string {
  if (km < 1) {
    const meters = Math.max(50, Math.round((km * 1000) / 50) * 50);
    return meters >= 1000 ? `${new Intl.NumberFormat(lang).format(1)} km` : `${meters} m`;
  }
  const digits = km < 10 ? 1 : 0;
  const format = new Intl.NumberFormat(lang, { minimumFractionDigits: digits, maximumFractionDigits: digits });
  return `${format.format(km)} km`;
}

/** O que fai distinta unha portada na collaxe: o debuxo (o seu tipo) ou cada foto. */
interface CollageCover {
  readonly kind: string;
  readonly id: string;
}

/**
 * As `size` portadas da collaxe do inicio, unha de cada tipo de instalación: primeiro os debuxos
 * (ou fotos) máis repetidos na lista, cada un coa primeira instalación que o ten; se non hai tantos
 * tipos, repítense na orde da lista. Sempre a mesma escolla para a mesma lista.
 */
export function collageCovers<C extends CollageCover>(items: readonly { readonly cover: C }[], size: number): C[] {
  const covers = items.map((item) => item.cover);
  const groups = new Map<string, C[]>();
  for (const cover of covers) {
    const key = `${cover.kind}:${cover.id}`;
    groups.set(key, [...(groups.get(key) ?? []), cover]);
  }
  // `sort` é estable: a empate, o tipo que aparece antes.
  const kinds = [...groups.values()].sort((a, b) => b.length - a.length).map((group) => group[0]);
  const picked = kinds.slice(0, size);
  for (const cover of covers) {
    if (picked.length >= size) break;
    if (!picked.includes(cover)) picked.push(cover);
  }
  return picked;
}
