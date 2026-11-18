// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * O paquete `leaflet` só publica a build UMD en `main`; a ESM vai aparte, sen tipos propios.
 * Son os mesmos de `@types/leaflet`.
 */
declare module 'leaflet/dist/leaflet-src.esm.js' {
  export * from 'leaflet';
}
