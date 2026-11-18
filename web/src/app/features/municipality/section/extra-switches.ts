// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FormControl } from '@angular/forms';
import type { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { MunicipalityStore } from '../municipality.store';

type Municipality = NonNullable<ReturnType<CurrentMunicipality['value']>>;
type Store = Pick<MunicipalityStore, 'updateOpenData' | 'updateInstallHint'>;

/** Os interruptores sen máis campos de «Configuración adicional» (Concello › Xeral). */
export interface SwitchControls {
  openData: FormControl<boolean>;
  installHint: FormControl<boolean>;
}

export function switchControls(municipality: Municipality): SwitchControls {
  return {
    openData: new FormControl(municipality.openData, { nonNullable: true }),
    installHint: new FormControl(municipality.installHint, { nonNullable: true }),
  };
}

/** Cada un co seu `PUT`, un detrás doutro, coa versión que deixou o anterior; só os que cambiaron. */
export async function saveSwitches(store: Store, current: CurrentMunicipality, form: SwitchControls): Promise<void> {
  await saveOpenData(store, current, form.openData.value);
  await saveInstallHint(store, current, form.installHint.value);
}

/**
 * «Datos abertos» en Concello › Xeral (ADR-0034, punto 25): o seu propio `PUT` dentro do «Gardar»
 * da pestana, coa versión do concello que deixou o paso anterior.
 */
export async function saveOpenData(
  store: Pick<MunicipalityStore, 'updateOpenData'>,
  current: CurrentMunicipality,
  enabled: boolean,
): Promise<void> {
  const municipality = current.value();
  if (!municipality || municipality.openData === enabled) return;
  const saved = await store.updateOpenData(municipality.slug, municipality.version, enabled);
  current.set({ ...municipality, version: saved.version, openData: saved.body });
}

/** «Aviso para instalar no móbil» na portada: coma «Datos abertos», o seu `PUT` co `If-Match` do concello. */
export async function saveInstallHint(
  store: Pick<MunicipalityStore, 'updateInstallHint'>,
  current: CurrentMunicipality,
  enabled: boolean,
): Promise<void> {
  const municipality = current.value();
  if (!municipality || municipality.installHint === enabled) return;
  const saved = await store.updateInstallHint(municipality.slug, municipality.version, enabled);
  current.set({ ...municipality, version: saved.version, installHint: saved.body });
}

/** «Vista previa» só co interruptor aceso e gardado: apagado, a páxina non existe (ADR-0034). */
export function openDataPreview(municipality: ReturnType<CurrentMunicipality['value']>): string | null {
  return municipality?.openData ? `/${municipality.slug}/datos-abertos` : null;
}
