// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import type { CanMatchFn } from '@angular/router';
import { DeploymentSettings } from './deployment-settings';

/**
 * `/alta-concello` só existe se o despregue ten «¿Es un concello?» (`FURAR_JOIN_REQUEST_EMAIL`,
 * ADR-0042). Sen ela, a ruta non casa e o URL cae en `/:municipality`: «non atopado», coma
 * calquera outro enderezo que non existe.
 */
export const joinRequestsMatch: CanMatchFn = async () => {
  const settings = inject(DeploymentSettings);
  await settings.ready;
  return settings.joinRequests();
};
