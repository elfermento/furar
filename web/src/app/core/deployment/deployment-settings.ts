// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { getDeploymentSettings } from '../../api/fn/deployment/get-deployment-settings';
import type { MapSettingsResponse } from '../../api/models/map-settings-response';

export type MapSettings = MapSettingsResponse;

/** Sen resposta da API: sen mapa (non se sabe que recoñecemento levaría). */
const NO_MAP: MapSettings = { enabled: false, attribution: '' };

/**
 * Datos do despregue que non dependen do concello (`GET /api/v1/deployment-settings`): se hai
 * mapa e o seu recoñecemento (ADR-0023, revisión do 2026-11-03). Pídense unha vez, a primeira
 * vez que algo os usa; nada disto vai en `environment.ts`.
 */
@Injectable({ providedIn: 'root' })
export class DeploymentSettings {
  private readonly api = inject(Api);
  private readonly current = signal<MapSettings | null>(null);

  /** O mapa; `null` mentres carga. */
  readonly map = this.current.asReadonly();
  /** Hai mapa (falso mentres carga e se a API non responde). */
  readonly mapEnabled = computed(() => this.current()?.enabled === true);

  constructor() {
    this.api.invoke(getDeploymentSettings).then(
      (settings) => this.current.set(settings.map),
      () => this.current.set(NO_MAP),
    );
  }
}
