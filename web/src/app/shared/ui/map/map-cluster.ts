// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Unha marca xa colocada na pantalla (píxeles do mapa no zoom actual). */
export interface ScreenMarker {
  readonly id: string;
  readonly x: number;
  readonly y: number;
}

/** Un grupo de marcas: as que o forman e o seu centro (a media), en píxeles. */
export interface MarkerCluster {
  readonly ids: readonly string[];
  readonly x: number;
  readonly y: number;
}

/** Distancia (px) por debaixo da que dúas marcas se agrupan: a chincheta mide 44 px. */
export const CLUSTER_RADIUS = 44;

/**
 * Agrupa as marcas que quedarían unha enriba doutra (máis preto ca `radius` píxeles do primeiro
 * punto do grupo), na orde na que chegan, así o resultado non depende de nada máis. Un grupo dunha
 * soa marca é a marca. Sen librería: as marcas dunha portada son poucas centenas como moito.
 */
export function clusterMarkers(markers: readonly ScreenMarker[], radius = CLUSTER_RADIUS): MarkerCluster[] {
  const groups: { seed: ScreenMarker; members: ScreenMarker[] }[] = [];
  for (const marker of markers) {
    const group = groups.find((g) => Math.hypot(g.seed.x - marker.x, g.seed.y - marker.y) < radius);
    if (group) group.members.push(marker);
    else groups.push({ seed: marker, members: [marker] });
  }
  return groups.map(({ members }) => ({
    ids: members.map((m) => m.id),
    x: members.reduce((sum, m) => sum + m.x, 0) / members.length,
    y: members.reduce((sum, m) => sum + m.y, 0) / members.length,
  }));
}

/**
 * Onde vai cada marca dun grupo que se abre no sitio (as que non se separan ao achegar: o mesmo
 * punto ou xa no zoom máximo): en círculo arredor do centro, a unha chincheta de distancia.
 */
export function spreadOffsets(count: number, gap = CLUSTER_RADIUS): [number, number][] {
  if (count <= 1) return [[0, 0]];
  const radius = Math.max(gap, (gap * count) / (2 * Math.PI));
  return Array.from({ length: count }, (_, i) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / count;
    return [Math.round(radius * Math.cos(angle)), Math.round(radius * Math.sin(angle))];
  });
}
