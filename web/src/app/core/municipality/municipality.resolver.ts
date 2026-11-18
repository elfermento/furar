// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import type { ResolveFn } from '@angular/router';
import { RedirectCommand, Router } from '@angular/router';
import type { MunicipalityResponse } from '../../api/models/municipality-response';
import { CurrentMunicipality } from './current-municipality';

/** Concello inexistente → «non existe»; API caída → «erro». O URL non cambia. */
export const municipalityResolver: ResolveFn<MunicipalityResponse> = async (route) => {
  const router = inject(Router);
  const current = inject(CurrentMunicipality);
  const slug = route.paramMap.get('municipality') ?? '';
  const errorPage = (status: number) =>
    new RedirectCommand(router.parseUrl(`/erro/${status}`), { skipLocationChange: true });
  try {
    return (await current.load(slug)) ?? errorPage(404);
  } catch {
    return errorPage(500);
  }
};

/** Fóra dun concello (portada, plataforma): tema por defecto. */
export const leaveMunicipalityResolver: ResolveFn<true> = () => {
  inject(CurrentMunicipality).clear();
  return true;
};
