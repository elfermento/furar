// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IconName } from '../ui/icon/icons';
import type { AccountDeletionBlockerResponse } from './account-deletion.store';

/** Traduce unha clave con parámetros. */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** Unha fila do diálogo: o que hai que cancelar e, se se pode facer na web, onde. */
export interface BlockerRow {
  readonly key: string;
  readonly icon: IconName;
  readonly text: string;
  /** Onde cancelalo; `null` se non se fai na web (pedir que a quiten do persoal). */
  readonly link: readonly string[] | null;
}

const ICONS: Readonly<Record<AccountDeletionBlockerResponse['kind'], IconName>> = {
  BOOKING: 'calendar-month',
  OPEN_MATCH: 'group',
  EVENT: 'local-activity',
  STAFF: 'tune',
  PLATFORM_ADMIN: 'shield',
};

/**
 * As filas do diálogo «Antes de eliminar a túa conta» (ADR-0043), na orde da API: as reservas e as
 * partidas levan a «As miñas reservas» dese concello; a inscrición, á ficha do evento; o persoal e a
 * plataforma non teñen ligazón (alguén ten que quitala).
 */
export function blockerRows(blockers: readonly AccountDeletionBlockerResponse[], t: Translate): BlockerRow[] {
  return blockers.map((b, index) => {
    const municipality = b.municipalityName ?? b.municipality ?? '';
    const one = b.count === 1;
    const key = `${b.kind}-${b.municipality ?? ''}-${b.eventId ?? index}`;
    const icon = ICONS[b.kind];
    switch (b.kind) {
      case 'BOOKING':
        return {
          key,
          icon,
          text: t(one ? 'accountDeletion.bookingOne' : 'accountDeletion.bookingOther', {
            count: b.count,
            municipality,
          }),
          link: b.municipality ? ['/', b.municipality, 'reservas'] : null,
        };
      case 'OPEN_MATCH':
        return {
          key,
          icon,
          text: t(one ? 'accountDeletion.openMatchOne' : 'accountDeletion.openMatchOther', {
            count: b.count,
            municipality,
          }),
          link: b.municipality ? ['/', b.municipality, 'reservas'] : null,
        };
      case 'EVENT':
        return {
          key,
          icon,
          text: t('accountDeletion.event', { name: b.eventName ?? '', municipality }),
          link: b.municipality && b.eventId ? ['/', b.municipality, 'eventos', b.eventId] : null,
        };
      case 'STAFF':
        return { key, icon, text: t('accountDeletion.staff', { municipality }), link: null };
      case 'PLATFORM_ADMIN':
        return { key, icon, text: t('accountDeletion.platformAdmin'), link: null };
    }
  });
}
