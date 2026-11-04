// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { computed, signal } from '@angular/core';
import type { MapSettings } from '../app/core/deployment/deployment-settings';
import { DeploymentSettings } from '../app/core/deployment/deployment-settings';

/** O mapa por defecto do despregue: Mapa base do IGN. */
export const IGN_MAP: MapSettings = {
  enabled: true,
  attribution: 'CC BY 4.0 scne.es',
  attributionUrl: 'https://www.scne.es',
};

/** Despregue sen mapa (`FURAR_MAP=off`). */
export const NO_MAP: MapSettings = { enabled: false, attribution: '' };

/** `DeploymentSettings` falso, sen API: co mapa do IGN salvo que se diga outro. */
export function provideDeploymentSettings(map: MapSettings | null = IGN_MAP) {
  const current = signal(map);
  return {
    provide: DeploymentSettings,
    useValue: { map: current.asReadonly(), mapEnabled: computed(() => current()?.enabled === true) },
  };
}
