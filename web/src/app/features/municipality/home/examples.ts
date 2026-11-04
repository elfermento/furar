// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { PublicFacilityResponse } from './home.store';

/** Unha instalación de exemplo: clave dos textos, debuxo (en `public/examples`) e deportes. */
interface ExampleDefinition {
  readonly key: string;
  readonly drawing: string;
  readonly sports: readonly string[];
}

/** Prefixos dos textos (nomes e deportes) en `home.examples`. */
const NAMES = 'home.examples.';
const SPORTS = 'home.examples.sports.';

/** As da maqueta 46, na súa orde: as tres primeiras van no collaxe e en «Así de fácil». */
const EXAMPLES: readonly ExampleDefinition[] = [
  { key: 'football', drawing: 'football_field', sports: ['football', 'football7'] },
  { key: 'padel', drawing: 'padel_court', sports: ['padel'] },
  { key: 'sportsHall', drawing: 'sports_hall', sports: ['basketball', 'handball', 'futsal'] },
  { key: 'athletics', drawing: 'athletics_track', sports: ['athletics'] },
];

/**
 * Instalacións de exemplo da portada (maqueta 46, ADR-0028): mentres o concello (ou a instalación)
 * non ten ningunha publicada, a portada amósaas coa etiqueta «Exemplo» e sen ligazón. Teñen a
 * forma das de verdade para que «Así de fácil» as pinte igual. Os textos saen da tradución do
 * idioma actual (`translate`); os debuxos son os de Furar (CC0), servidos pola web.
 */
export function exampleFacilities(translate: (key: string) => string): PublicFacilityResponse[] {
  return EXAMPLES.map((example) => {
    const name = translate(NAMES + example.key);
    const image = `examples/${example.drawing}.jpg`;
    return {
      id: `example-${example.key}`,
      name,
      cover: {
        id: `example-${example.drawing}`,
        kind: 'DRAWING',
        thumb: image,
        large: image,
        width: 1200,
        height: 800,
        alt: { gl: '', es: '' },
      },
      sports: example.sports.map((sport) => {
        const label = translate(SPORTS + sport);
        return { label: { gl: label, es: label } };
      }),
      address: { street: '', locality: '', postalCode: '', latitude: 0, longitude: 0 },
      temporarilyClosed: false,
    };
  });
}
