// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

/**
 * A reserva que pide abrir o URL (`?reserva=<id>`, a ligazón do rexistro de actividade), ou `null`.
 * O parámetro sae do URL (sen nova entrada no historial) para que recargar non a volva abrir despois
 * de pechala. Só nun contexto de inxección (o construtor da páxina).
 */
export function linkedBooking(): string | null {
  const route = inject(ActivatedRoute);
  const id = route.snapshot.queryParamMap.get('reserva');
  if (id) void inject(Router).navigate([], { relativeTo: route, queryParams: {}, replaceUrl: true });
  return id;
}
