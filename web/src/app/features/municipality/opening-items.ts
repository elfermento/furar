// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ChecklistItem } from '../../shared/ui/checklist/checklist.component';

/**
 * Parámetro do URL que abre a alta no listado de instalacións (`?nova=1`): o asistente «Nova
 * instalación».
 */
const NEW_FACILITY_PARAM = 'nova';

/**
 * Os pasos de «Para abrir as reservas» (`null` co todo feito), con ligazóns absolutas: valen en
 * «Xeral» e na portada (a versión compacta, maqueta 56).
 */
export function openingChecklistItems(
  slug: string,
  done: { readonly legal: boolean; readonly facility: boolean },
  t: (key: string) => string,
): ChecklistItem[] | null {
  if (done.legal && done.facility) return null;
  return [
    {
      key: 'legal',
      label: t('section.opening.legal'),
      next: t('section.opening.legalNext'),
      done: done.legal,
      action: { label: t('section.opening.complete'), link: ['/', slug, 'xestion', 'concello', 'legal'] },
    },
    {
      key: 'facility',
      label: t('section.opening.facility'),
      next: t('section.opening.facilityNext'),
      done: done.facility,
      action: {
        label: t('section.opening.create'),
        link: ['/', slug, 'xestion', 'instalacions'],
        queryParams: { [NEW_FACILITY_PARAM]: 1 },
      },
    },
  ];
}
