// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import type { CanMatchFn } from '@angular/router';
import { MunicipalityDirectory } from './municipality-directory';

/**
 * A raíz é a portada común só cando a instalación ten varios concellos (ADR-0030). Con un ou
 * ningún, ou se a lista non se pode ler, segue a ruta seguinte (a súa portada, a de exemplo ou o
 * erro; ADR-0028). Le a lista da API cada vez que se entra na raíz; a ruta seguinte usa a gardada.
 */
export const severalMunicipalitiesMatch: CanMatchFn = () =>
  inject(MunicipalityDirectory)
    .fetch()
    .then(
      (municipalities) => municipalities.length > 1,
      () => false,
    );
