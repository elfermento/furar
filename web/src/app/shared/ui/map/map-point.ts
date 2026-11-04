// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Punto do mapa en graos WGS 84, coa precisión que garda a API (`latitude numeric(8,6)`,
 * `longitude numeric(9,6)`: seis decimais, uns 10 cm).
 */
export interface MapPoint {
  readonly latitude: number;
  readonly longitude: number;
}

export type MapAxis = keyof MapPoint;

/** Unha marca dun mapa con varias (listado): o que representa, onde e o seu nome. */
export interface MapMarker {
  readonly id: string;
  readonly point: MapPoint;
  readonly label: string;
}

export const AXIS_LIMIT: Record<MapAxis, number> = { latitude: 90, longitude: 180 };

const DECIMALS = 6;
const NUMBER = /^-?\d+(?:\.\d+)?$/;

export function isMapPoint(value: unknown): value is MapPoint {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const { latitude, longitude } = value as Record<string, unknown>;
  return inRange(latitude, 'latitude') && inRange(longitude, 'longitude');
}

function inRange(value: unknown, axis: MapAxis): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= AXIS_LIMIT[axis];
}

/** O punto dun enderezo da API, se o ten (sen mapa no despregue pode faltar). */
export function pointOf(
  address: { readonly latitude?: number | null; readonly longitude?: number | null } | null | undefined,
): MapPoint | null {
  const point = { latitude: address?.latitude, longitude: address?.longitude };
  return isMapPoint(point) ? point : null;
}

/** Redondea a seis decimais, como a base. */
export function roundPoint(latitude: number, longitude: number): MapPoint {
  const round = (n: number) => Number(n.toFixed(DECIMALS));
  return { latitude: round(latitude), longitude: round(longitude) };
}

const formatters = new Map<string, Intl.NumberFormat>();

/**
 * Chrome e Chromium non traen datos `gl` en Intl e caerían no punto decimal do inglés; o galego
 * escribe os números coma o castelán (coma decimal), así que nese caso úsase `es`.
 */
function numberLocale(lang: string): string {
  try {
    return Intl.NumberFormat.supportedLocalesOf([lang]).length || lang !== 'gl' ? lang : 'es';
  } catch {
    return lang;
  }
}

/** «43,491400» en gl e es: co separador decimal do idioma e sen agrupar os miles. */
export function formatCoordinate(value: number, lang: string): string {
  let format = formatters.get(lang);
  if (!format) {
    format = new Intl.NumberFormat(numberLocale(lang), {
      minimumFractionDigits: DECIMALS,
      maximumFractionDigits: DECIMALS,
      useGrouping: false,
    });
    formatters.set(lang, format);
  }
  return format.format(value);
}

/**
 * Le unha coordenada escrita a man: coma ou punto decimal, signo «-» ou «−» e espazos ao redor.
 * Devolve `null` se non é un número ou se sae do rango do eixo.
 */
export function parseCoordinate(text: string, axis: MapAxis): number | null {
  const normal = text.trim().replace(',', '.').replace('−', '-');
  if (!NUMBER.test(normal)) {
    return null;
  }
  const value = Number(normal);
  return inRange(value, axis) ? Number(value.toFixed(DECIMALS)) : null;
}

/** Desprazamento en píxeles dunha frecha (Maiús: cinco veces máis). */
export function arrowOffset(event: KeyboardEvent): readonly [number, number] | null {
  const step = event.shiftKey ? 50 : 10;
  switch (event.key) {
    case 'ArrowUp':
      return [0, -step];
    case 'ArrowDown':
      return [0, step];
    case 'ArrowLeft':
      return [-step, 0];
    case 'ArrowRight':
      return [step, 0];
    default:
      return null;
  }
}
