// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Incident } from './incidents';

type Overrides = Omit<Partial<Incident>, 'reporter'> & { reporter?: Partial<Incident['reporter']> };

/** Unha incidencia de proba (sen datos reais): un aviso da veciñanza, novo, cunha foto. */
export function incident(overrides: Overrides = {}): Incident {
  const { reporter, ...rest } = overrides;
  return {
    id: 'i1',
    facilityId: 'f1',
    facilityName: 'Pista de pádel 2',
    type: 'BREAKDOWN',
    status: 'NEW',
    origin: 'RESIDENT',
    description: 'A rede está rota polo medio.',
    reporter: { name: 'Uxía Proba', email: 'uxia@example.org', anonymized: false, ...reporter },
    photos: [{ id: 'p1', width: 1200, height: 800 }],
    photosRemoved: false,
    createdAt: '2026-11-11T09:42:00Z',
    version: 0,
    ...(reporter?.anonymized ? { reporter: { anonymized: true } } : {}),
    ...rest,
  } as Incident;
}
