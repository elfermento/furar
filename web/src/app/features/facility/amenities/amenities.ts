// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FormControl, FormGroup } from '@angular/forms';
import { registerSymbols } from '../../../shared/ui/icon/icon.component';
import type { IconName } from '../../../shared/ui/icon/icons';
import { AMENITY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import type { Facility } from '../detail/facility-detail.store';

/** Servizo ou medida de accesibilidade do catálogo fixo de Furar (especificación, bloque 1). */
export type Amenity = Facility['amenities'][number];
export type AmenityGroup = 'services' | 'accessibility';

/**
 * Grupo de cada elemento do catálogo, na orde en que se ven. Un `Record` sobre o tipo do
 * contrato: se a API engade un valor, isto deixa de compilar ata darlle grupo e texto.
 */
const GROUP_OF: Record<Amenity, AmenityGroup> = {
  CHANGING_ROOMS: 'services',
  SHOWERS: 'services',
  COVERED_STANDS: 'services',
  CAFE: 'services',
  PARKING: 'services',
  LIGHTING: 'services',
  STEP_FREE_ACCESS: 'accessibility',
  ACCESSIBLE_TOILET: 'accessibility',
  ACCESSIBLE_CHANGING_ROOM: 'accessibility',
  HEARING_LOOP: 'accessibility',
};

/** Icona de cada elemento na vista (maqueta `02-ficha-instalacion`; Material Symbols, P27). */
const ICON_OF: Record<Amenity, IconName> = {
  CHANGING_ROOMS: 'checkroom',
  SHOWERS: 'shower',
  COVERED_STANDS: 'stadium',
  CAFE: 'local-cafe',
  PARKING: 'local-parking',
  LIGHTING: 'lightbulb',
  STEP_FREE_ACCESS: 'accessible-forward',
  ACCESSIBLE_TOILET: 'wc',
  ACCESSIBLE_CHANGING_ROOM: 'accessible',
  HEARING_LOOP: 'hearing',
};

// As iconas do catálogo non van na carga inicial (D27): chegan con quen usa este ficheiro.
registerSymbols(AMENITY_SYMBOLS);

export function amenityIcon(item: Amenity): IconName {
  return ICON_OF[item];
}

/** Todo o catálogo, na orde da vista e do diálogo. */
export const AMENITIES = Object.keys(GROUP_OF) as readonly Amenity[];

export const AMENITY_GROUPS: readonly { readonly key: AmenityGroup; readonly items: readonly Amenity[] }[] = (
  ['services', 'accessibility'] as const
).map((key) => ({ key, items: AMENITIES.filter((item) => GROUP_OF[item] === key) }));

export type AmenitiesForm = FormGroup<Record<Amenity, FormControl<boolean>>>;
export type AmenitiesValue = Record<Amenity, boolean>;

/** Unha casilla por elemento do catálogo, marcada se a instalación xa o ofrece. */
export function amenitiesForm(selected: readonly Amenity[]): AmenitiesForm {
  const controls = Object.fromEntries(
    AMENITIES.map((item) => [item, new FormControl(selected.includes(item), { nonNullable: true })]),
  ) as Record<Amenity, FormControl<boolean>>;
  return new FormGroup(controls);
}

/** O que ofrece a instalación, na orde do catálogo (a API non garante orde). */
export function offeredAmenities(facility: Facility | null): Amenity[] {
  const selected = new Set(facility?.amenities ?? []);
  return AMENITIES.filter((item) => selected.has(item));
}

/** O corpo do `PUT`: a lista enteira dos marcados, na orde do catálogo. */
export function amenitiesBody(value: AmenitiesValue): Amenity[] {
  return AMENITIES.filter((item) => value[item]);
}
