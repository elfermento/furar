// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Injector } from '@angular/core';
import { runInInjectionContext } from '@angular/core';
import type { MunicipalitySummaryResponse } from '../../api/models/municipality-summary-response';
import type { PickMenuOption, PickSheetRequest } from '../../shared/ui/pick-menu/pick-menu.component';
import { NEW_MUNICIPALITY_URL } from '../auth/roles';
import type { StaffMembership } from '../auth/staff-memberships.service';
import type { UserMenuSwitcher } from '../../shared/ui/user-menu/user-menu.component';
import { BOOKING_STYLESHEET } from './booking-styles';
import { lazyStylesheet } from './lazy-stylesheet';

/** A barra cárgao á parte cando hai sesión: os servizos e a API dos concellos non van na carga inicial. */
export { StaffMembershipsService } from '../auth/staff-memberships.service';
export { MunicipalityDirectory } from '../municipality/municipality-directory';

/**
 * «Concello» no menú da persoa: só con dous ou máis concellos como persoal. O actual
 * (o do URL) leva ✓; fóra dun concello, ningún. O rol vai á dereita de cada un.
 */
export function municipalitySwitcher(
  list: readonly StaffMembership[],
  slug: string | null,
  t: (key: string) => string,
): UserMenuSwitcher | null {
  if (list.length < 2) return null;
  return {
    label: t('session.switchMunicipality'),
    current: list.find((m) => m.slug === slug)?.name ?? '',
    options: list.map((m) => ({
      id: m.slug,
      label: m.name,
      detail: t('session.role.' + m.role),
      current: m.slug === slug,
    })),
  };
}

/**
 * «Concello ▾» na barra (persoal de dous ou máis concellos, sen ningún escollido): cada concello
 * co rol á dereita. Cun só concello (ou ningún), nada.
 */
export function municipalityPicks(
  list: readonly StaffMembership[],
  t: (key: string) => string,
): readonly PickMenuOption[] {
  return list.length < 2 ? [] : list.map((m) => ({ id: m.slug, label: m.name, detail: t('session.role.' + m.role) }));
}

/**
 * A lista de «Concello ▾» no móbil, nunha folla desde abaixo: primeiro a folla de estilos das capas
 * (non vai na carga inicial) e despois a folla e o servizo das capas, tamén á parte (D27).
 * Devolve o concello escollido.
 */
export async function pickInSheet(injector: Injector, request: PickSheetRequest): Promise<string | undefined> {
  await runInInjectionContext(injector, lazyStylesheet(BOOKING_STYLESHEET, 'furar-booking-css'));
  const [{ openPickSheet }, { DialogService }] = await Promise.all([
    import('../../shared/ui/pick-menu/pick-sheet.component'),
    import('../../shared/ui/dialog/dialog.service'),
  ]);
  return openPickSheet(injector.get(DialogService), request);
}

/**
 * «Concellos ›» no menú da plataforma: todos os concellos, por nome, co actual (o do
 * URL) con ✓, e ao final «＋ Novo concello». Sen ningún, nada: «Novo concello» vai entón no primeiro
 * nivel (`userMenuItems`). Os concellos onde a plataforma é tamén persoal (tras «Engadirme como
 * xestor») levan o rol á dereita, coma no cambio do persoal.
 */
export function platformSwitcher(
  list: readonly Pick<MunicipalitySummaryResponse, 'slug' | 'name'>[],
  slug: string | null,
  t: (key: string) => string,
  lang: string,
  memberships: readonly StaffMembership[] = [],
): UserMenuSwitcher | null {
  if (list.length === 0) return null;
  const collator = new Intl.Collator(lang);
  const roles = new Map(memberships.map((m) => [m.slug, m.role]));
  return {
    label: t('session.municipalities'),
    current: list.find((m) => m.slug === slug)?.name ?? '',
    options: [...list]
      .sort((a, b) => collator.compare(a.name, b.name))
      .map((m) => {
        const role = roles.get(m.slug);
        return {
          id: m.slug,
          label: m.name,
          ...(role ? { detail: t('session.role.' + role) } : {}),
          current: m.slug === slug,
        };
      }),
    extra: { id: 'new-municipality', label: t('session.newMunicipality'), icon: 'plus', link: [NEW_MUNICIPALITY_URL] },
  };
}
