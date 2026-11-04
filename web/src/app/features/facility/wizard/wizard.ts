// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { pointOf } from '../../../shared/ui/map/map-point';
import type { StepState } from '../../../shared/ui/stepper/stepper.component';
import type { SportResponse } from '../data-card/data-form';
import { fold } from '../data-card/sports-field.component';
import type { DayHours, Interval } from '../schedule/schedule.store';
import { DAYS } from '../schedule/schedule.store';
import { priceText } from '../uses/use-price';
import type { RentalMode, Use } from '../uses/uses.store';

/**
 * Asistente «Nova instalación» (maqueta 53): os pasos, onde segue un borrador, os atallos dos usos
 * e o horario «Todo o ano». Sen Angular: o estado vive en `WizardStore`.
 */
export const STEP_KEYS = ['data', 'location', 'contact', 'photos', 'uses', 'schedule', 'rules', 'review'] as const;
export type StepKey = (typeof STEP_KEYS)[number];
export const STEP = Object.fromEntries(STEP_KEYS.map((key, i) => [key, i])) as Record<StepKey, number>;
export const REVIEW = STEP.review;
export const STEP_COUNT = STEP_KEYS.length;

/** Os que se poden «Saltar»: todo o seu contido é opcional. */
export const OPTIONAL_STEPS: ReadonlySet<StepKey> = new Set<StepKey>(['contact', 'photos', 'rules']);

/** Clave do nome dun paso: o 7 chámase «Normas e pago» só se o pago vai por instalación. */
export function stepLabelKey(key: StepKey, scope: 'ALL' | 'FACILITY'): string {
  return `facility.wizard.steps.${key === 'rules' && scope === 'FACILITY' ? 'rulesAndPayment' : key}`;
}

/** O que se sabe dun borrador desde o listado ou a ficha. */
export interface DraftProgress {
  readonly address?: { readonly latitude?: number | null; readonly longitude?: number | null } | null;
  readonly missing: readonly string[];
}

/**
 * Paso onde segue un borrador (e o «Paso N de 8» do listado): o primeiro obrigatorio sen
 * completar; se non falta nada, «Revisar». Co mapa no despregue, o enderezo leva a chincheta.
 */
export function resumeStep(draft: DraftProgress, needsPoint: boolean): number {
  if (draft.missing.includes('SPORTS')) return STEP.data;
  if (!draft.address || (needsPoint && !pointOf(draft.address))) return STEP.location;
  if (draft.missing.includes('USES')) return STEP.uses;
  if (draft.missing.includes('SCHEDULE')) return STEP.schedule;
  return REVIEW;
}

/** Estado dun paso no indicador: o actual; despois, se se visitou, feito ou con algo mal. */
export function stepState(index: number, current: number, max: number, ok: boolean): StepState {
  if (index === current) return 'current';
  if (index > max || index === REVIEW) return 'pending';
  return ok ? 'done' : 'todo';
}

// --- Horario ---

export type ScheduleMode = 'SAME' | 'DAYS';
/** O horario que se propón: de 8:00 a 23:00 todos os días. */
export const DEFAULT_HOURS: Interval = { startMinute: 8 * 60, endMinute: 23 * 60 };
const WEEKDAYS = DAYS.filter((day) => day !== 'HOLIDAYS');

/**
 * «Todo o ano»: desde o 1 de xaneiro do ano de hoxe e sen data de fin (maqueta 54), para que unha
 * instalación nova non deixe de reservarse o 1 de xaneiro seguinte.
 */
export function wholeYear(today: IsoDate): { startDate: IsoDate } {
  return { startDate: `${today.slice(0, 4)}-01-01` as IsoDate };
}

/** O mesmo tramo de luns a domingo; os festivos, pechados. */
export function sameWeek(hours: Interval): DayHours[] {
  return DAYS.map((day) => ({ day, intervals: day === 'HOLIDAYS' ? [] : [{ ...hours }] }));
}

/**
 * Como se ve unha semana gardada: «Mesmo horario todos os días» se de luns a domingo ten o mesmo
 * tramo único e os festivos pechados; se non, «Por días».
 */
export function modeOf(week: readonly DayHours[]): { mode: ScheduleMode; same: Interval } {
  const rows = new Map(week.map((row) => [row.day, row.intervals]));
  const first = rows.get('MONDAY')?.[0];
  const same =
    !!first &&
    (rows.get('HOLIDAYS') ?? []).length === 0 &&
    WEEKDAYS.every((day) => {
      const intervals = rows.get(day) ?? [];
      return (
        intervals.length === 1 &&
        intervals[0].startMinute === first.startMinute &&
        intervals[0].endMinute === first.endMinute
      );
    });
  const open = week.find((row) => row.intervals.length)?.intervals[0];
  return { mode: same ? 'SAME' : 'DAYS', same: first && same ? { ...first } : { ...(open ?? DEFAULT_HOURS) } };
}

// --- Usos ---

/** Un atallo de «Engadir uso»: claves do nome e das partes (`facility.wizard.uses.presets.*`). */
interface PresetDef {
  readonly id: string;
  readonly rentalMode: RentalMode;
  readonly parts?: readonly string[];
  readonly capacity?: number;
}

const half = (id: string, parts: readonly [string, string]): PresetDef => ({ id, rentalMode: 'PARTS', parts });
const whole = (id: string): PresetDef => ({ id, rentalMode: 'WHOLE' });
const seats = (id: string, capacity: number): PresetDef => ({ id, rentalMode: 'SEATS', capacity });
const LANES = ['lane1', 'lane2', 'lane3', 'lane4', 'lane5', 'lane6'];

const COURT = [whole('wholeCourt'), half('halfCourt', ['halfNorth', 'halfSouth'])];
/** Atallos por deporte do catálogo: os de pista (salvo bádminton) comparten os da pista. */
const PRESETS: Readonly<Record<string, readonly PresetDef[]>> = {
  basketball: COURT,
  handball: COURT,
  futsal: COURT,
  volleyball: COURT,
  roller_hockey: COURT,
  football: [whole('wholeField'), half('halfField', ['fieldNorth', 'fieldSouth'])],
  football_7: [whole('field7')],
  swimming: [{ id: 'lanes', rentalMode: 'PARTS', parts: LANES }, seats('freeSwim', 40)],
  water_polo: [whole('wholePool')],
  padel: [whole('padel')],
  tennis: [whole('tennis')],
  badminton: [whole('badminton')],
  table_tennis: [whole('table')],
  athletics: [seats('track', 30)],
  gymnastics: [whole('room'), seats('class', 20)],
  skating: [whole('rink')],
};

/** Un atallo xa traducido: o que enche o formulario do uso. */
export interface UsePreset {
  readonly name: string;
  readonly rentalMode: RentalMode;
  readonly parts: readonly string[];
  readonly capacity: number | null;
}

export type Translate = (key: string, params?: Record<string, unknown>) => string;

/**
 * Atallos para os deportes do paso 1, na súa orde e sen repetir nin chocar co nome dun uso que
 * xa hai (sen maiúsculas nin acentos).
 */
export function presetsFor(sports: readonly SportResponse[], uses: readonly Use[], t: Translate): UsePreset[] {
  const seen = new Set(uses.map((use) => fold(use.name.trim())));
  const out: UsePreset[] = [];
  for (const sport of sports) {
    for (const def of PRESETS[sport.code ?? ''] ?? []) {
      const name = t(`facility.wizard.uses.presets.${def.id}`);
      if (seen.has(fold(name))) continue;
      seen.add(fold(name));
      out.push({
        name,
        rentalMode: def.rentalMode,
        parts: (def.parts ?? []).map((part) => t(`facility.wizard.uses.parts.${part}`)),
        capacity: def.capacity ?? null,
      });
    }
  }
  return out;
}

export { priceText };
