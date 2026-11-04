// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { StaffRole } from '../auth/roles';
import { NEW_MUNICIPALITY_URL, managementHome } from '../auth/roles';
import type { UserMenuItem } from '../../shared/ui/user-menu/user-menu.component';
import { STAFF_PAGES } from './staff-pages';

/** O que decide as entradas do menú da persoa. */
export interface UserMenuContext {
  readonly slug: string | null;
  /** Rol de persoal no concello actual (da API). */
  readonly staffRole: StaffRole | null;
  /** Ve o menú da xestión no concello actual (Xestión, Recepción ou a plataforma). */
  readonly canManage: boolean;
  /** Conta de traballo (persoal deste ou doutro concello) ou da plataforma: non reserva. */
  readonly workAccount: boolean;
  readonly platformAdmin: boolean;
  /** Na xestión do concello (`/:concello/xestion/...`) e non na web pública. */
  readonly inManagement: boolean;
  /** Cantos concellos ve a plataforma; `null` se non é a plataforma ou aínda non o sabe. */
  readonly municipalities: number | null;
  readonly homeLink: readonly unknown[];
  readonly accountUrl: string;
  readonly securityUrl: string;
}

/**
 * As entradas do menú da persoa (`furar-user-menu`; maquetas 50 e 56). Primeiro, a entrada á
 * xestión, en negra: a plataforma sen ningún concello, «Novo concello» (con concellos, «Concellos ›»
 * vai no cambio, `platformSwitcher`); Xestión e Recepción fóra da xestión, «Xestión do concello»
 * (á entrada do seu rol). Despois, a súa conta en Keycloak, «As miñas reservas» (só a veciñanza), a
 * web pública (Xestión, desde a xestión, e a plataforma; Recepción non) e pechar sesión; o persoal
 * e a plataforma, tamén «Seguridade da conta» (o contrasinal). As reservas do concello de Recepción
 * van na barra, non aquí.
 */
export function userMenuItems(c: UserMenuContext, t: (key: string) => string): UserMenuItem[] {
  const items: UserMenuItem[] = [];
  if (c.platformAdmin && c.municipalities === 0) {
    items.push({
      id: 'new-municipality',
      label: t('session.newMunicipality'),
      icon: 'plus',
      link: [NEW_MUNICIPALITY_URL],
      strong: true,
    });
  }
  const manage =
    !c.platformAdmin && c.slug !== null && c.staffRole !== null && !c.inManagement
      ? managementHome(c.slug, c.staffRole)
      : null;
  if (manage) {
    items.push({ id: 'manage', label: t('session.manage'), icon: 'tune', link: [manage], strong: true });
  }
  items.push({
    id: 'account',
    label: t('session.account'),
    icon: 'user',
    href: c.accountUrl,
    separatorBefore: items.length > 0,
  });
  // As súas reservas: só a veciñanza. Nin as contas de traballo (ADR-0027) nin a plataforma (ADR-0028).
  if (c.slug && !c.workAccount && !c.platformAdmin) {
    items.push({
      id: 'bookings',
      label: t('session.myBookings'),
      icon: 'calendar-month',
      link: ['/', c.slug, 'reservas'],
    });
  }
  // A web pública: non desde ela (Xestión ten alí «Xestión do concello»).
  if (c.canManage && c.staffRole !== 'STAFF' && !manage) {
    items.push({ id: 'public', label: t('session.publicSite'), icon: 'globe', link: c.homeLink });
  }
  // O rexistro da plataforma (as súas accións en todos os concellos) non ten outro sitio no menú.
  if (c.platformAdmin && STAFF_PAGES.activity) {
    items.push({
      id: 'platform-activity',
      label: t('session.platformActivity'),
      icon: 'schedule',
      link: ['/', 'plataforma', 'rexistro'],
    });
  }
  // Seguridade da conta (o contrasinal) para o persoal e a plataforma.
  if (c.staffRole !== null || c.platformAdmin) {
    items.push({ id: 'security', label: t('session.security'), icon: 'shield', href: c.securityUrl });
  }
  items.push({ id: 'logout', label: t('session.logout'), icon: 'logout', separatorBefore: true });
  return items;
}
