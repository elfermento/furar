// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { pointOf } from '../../../shared/ui/map/map-point';
import type { StepState } from '../../../shared/ui/stepper/stepper.component';
import type { SportResponse } from '../data-card/data-form';
import { fold } from '../data-card/sports-field.component';
import { priceText } from '../uses/use-price';
import type { RentalMode, Use } from '../uses/uses.store';

/**
 * Asistente «Nova instalación»: os pasos, onde segue un borrador, os atallos dos usos
 * e o horario por defecto. Sen Angular: o estado vive en `WizardStore`.
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

export type { ScheduleMode } from '../schedule/season-editor';
export { DEFAULT_HOURS, modeOf, sameWeek } from '../schedule/season-editor';

/**
 * Inicio da primeira tempada do asistente: o 1 de xaneiro do ano de hoxe, sen data de fin,
 * para que unha instalación nova non deixe de reservarse o 1 de xaneiro seguinte.
 */
export function wholeYear(today: IsoDate): { startDate: IsoDate } {
  return { startDate: `${today.slice(0, 4)}-01-01` as IsoDate };
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

/**
 * O que acompaña a «quen reserva» no repaso do asistente: os ocos libres á vista (só sen reserva
 * por internet) e o control de entrada (ADR-0032), como claves de texto.
 */
export function bookingExtras(value: {
  onlineBooking: boolean;
  showFreeSlots: boolean;
  entryControl: boolean;
}): string[] {
  return [
    ...(!value.onlineBooking && value.showFreeSlots ? ['facility.data.showFreeSlots.label'] : []),
    ...(value.entryControl ? ['facility.data.entryControl'] : []),
  ];
}
