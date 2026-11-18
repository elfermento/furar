// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
// Só o tipo de local-date.ts: a liña do último acceso vai na carga inicial (D27) e non debe arrastralo.
import type { IsoDate } from './local-date';

/**
 * Instantes da API (UTC) no reloxo de parede de Europe/Madrid, a zona das instalacións, sexa cal
 * sexa a do navegador. As partes saen de `Intl` en `en-GB` (todos os navegadores o traen) e o texto
 * faise a man: Chrome e Chromium non teñen datos `gl` e, con `dateStyle` ou `month: 'long'`, caen
 * nun formato raíz («2026 M10 29 00:28»). Os nomes de meses e días van en `local-date.ts`.
 */
export const MADRID_ZONE = 'Europe/Madrid';

const parts = new Intl.DateTimeFormat('en-GB', {
  timeZone: MADRID_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** Ano, mes, día, hora, minuto e segundo en Madrid, con dúas cifras (o ano, catro). */
export interface MadridWall {
  readonly year: string;
  readonly month: string;
  readonly day: string;
  readonly hour: string;
  readonly minute: string;
  readonly second: string;
}

export function madridWall(instant: Date | number | string): MadridWall {
  const part = Object.fromEntries(parts.formatToParts(new Date(instant)).map((p) => [p.type, p.value]));
  return {
    year: part['year'] ?? '',
    month: part['month'] ?? '',
    day: part['day'] ?? '',
    hour: part['hour'] ?? '',
    minute: part['minute'] ?? '',
    second: part['second'] ?? '',
  };
}

/** Data ISO e hora («18:05») dun instante en Madrid. */
export function inMadrid(instant: Date | number | string): { date: IsoDate; time: string } {
  const wall = madridWall(instant);
  return { date: `${wall.year}-${wall.month}-${wall.day}`, time: `${wall.hour}:${wall.minute}` };
}

/** Hoxe en Madrid (ISO): os días do calendario son os de alí, como na API. */
export function madridToday(now: Date = new Date()): IsoDate {
  return inMadrid(now).date;
}

/**
 * «29/10/2026, 00:28» (ou só «29/10/2026»): igual en galego e en castelán. `null` se non hai data
 * ou non vale.
 */
export function formatInstant(instant: Date | string | null | undefined, withTime = true): string | null {
  if (instant === null || instant === undefined || instant === '') return null;
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return null;
  const wall = madridWall(date);
  const day = `${wall.day}/${wall.month}/${wall.year}`;
  return withTime ? `${day}, ${wall.hour}:${wall.minute}` : day;
}
