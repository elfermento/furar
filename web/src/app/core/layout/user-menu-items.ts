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
  /** Conta de traballo (persoal deste ou doutro concello) ou da plataforma: non reserva. */
  readonly workAccount: boolean;
  readonly platformAdmin: boolean;
  /** Na xestión do concello (`/:concello/xestion/...`) e non na web pública. */
  readonly inManagement: boolean;
  /** Cantos concellos ve a plataforma; `null` se non é a plataforma ou aínda non o sabe. */
  readonly municipalities: number | null;
}

/** «A miña conta» (a pantalla da app): dentro do concello actual ou, sen el, na raíz. */
export function accountLink(slug: string | null): readonly string[] {
  return slug ? ['/', slug, 'conta'] : ['/', 'conta'];
}

/**
 * As entradas do menú da persoa (`furar-user-menu`). Primeiro, a entrada á xestión, en negra: a
 * plataforma sen ningún concello, «Novo concello» (con concellos, «Concellos ›» vai no cambio,
 * `platformSwitcher`); Xestión e Recepción fóra da xestión, «Xestión do concello» (á entrada do
 * seu rol). Despois, «A miña conta» (a pantalla da app, coa seguridade, os concellos, o último
 * acceso e «Eliminar a miña conta»), «As miñas reservas» e «Persoas ao meu cargo» (só a
 * veciñanza), os indicadores e o rexistro da plataforma e, tras unha liña, «Pechar sesión». A web
 * pública é o escudo da barra; as reservas do concello de Recepción van na barra, non aquí.
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
    link: accountLink(c.slug),
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
  // As persoas a cargo son da conta (ADR-0031): só a veciñanza, coma «As miñas reservas», e tamén
  // sen concello á vista. Abre o diálogo de xestión.
  if (!c.workAccount && !c.platformAdmin) {
    items.push({ id: 'dependents', label: t('session.dependents'), icon: 'user' });
  }
  // Os indicadores da plataforma: as cifras de todos os concellos, antes do rexistro.
  // Sen ningún concello aínda, non hai cifras que ver.
  if (c.platformAdmin && c.municipalities !== 0) {
    items.push({
      id: 'platform-indicators',
      label: t('session.platformIndicators'),
      icon: 'chart',
      link: ['/', 'plataforma', 'indicadores'],
    });
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
  items.push({ id: 'logout', label: t('session.logout'), icon: 'logout', separatorBefore: true });
  return items;
}
