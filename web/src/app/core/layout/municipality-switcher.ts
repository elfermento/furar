// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { MunicipalitySummaryResponse } from '../../api/models/municipality-summary-response';
import { NEW_MUNICIPALITY_URL } from '../auth/roles';
import type { StaffMembership } from '../auth/staff-memberships.service';
import type { UserMenuSwitcher } from '../../shared/ui/user-menu/user-menu.component';

/** A barra cárgao á parte cando hai sesión: os servizos e a API dos concellos non van na carga inicial. */
export { StaffMembershipsService } from '../auth/staff-memberships.service';
export { MunicipalityDirectory } from '../municipality/municipality-directory';

/**
 * «Concello» no menú da persoa (maqueta 41): só con dous ou máis concellos como persoal. O actual
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
 * «Concellos ›» no menú da plataforma (maqueta 56): todos os concellos, por nome, co actual (o do
 * URL) con ✓, e ao final «＋ Novo concello». Sen ningún, nada: «Novo concello» vai entón no primeiro
 * nivel (`userMenuItems`).
 */
export function platformSwitcher(
  list: readonly Pick<MunicipalitySummaryResponse, 'slug' | 'name'>[],
  slug: string | null,
  t: (key: string) => string,
  lang: string,
): UserMenuSwitcher | null {
  if (list.length === 0) return null;
  const collator = new Intl.Collator(lang);
  return {
    label: t('session.municipalities'),
    current: list.find((m) => m.slug === slug)?.name ?? '',
    options: [...list]
      .sort((a, b) => collator.compare(a.name, b.name))
      .map((m) => ({ id: m.slug, label: m.name, current: m.slug === slug })),
    extra: { id: 'new-municipality', label: t('session.newMunicipality'), icon: 'plus', link: [NEW_MUNICIPALITY_URL] },
  };
}
