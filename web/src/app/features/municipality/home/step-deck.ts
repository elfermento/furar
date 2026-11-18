// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { reducedMotion } from '../../../shared/ui/motion/reduced-motion';

/**
 * Onde a baralla vai unha debaixo doutra sempre (o mesmo corte ca home.css): pantalla ancha pero
 * baixa, onde as tarxetas do escritorio non caben enteiras debaixo da barra superior.
 */
export const STATIC_QUERY = '(min-width: 900px) and (max-height: 719px)';

/**
 * Pantalla estreita (o corte da barra superior e da inferior, home.css): a mesma baralla, coa
 * altura da tarxeta máis alta; vai fixa só se cabe (`deckFits`).
 */
export const NARROW_QUERY = '(max-width: 899px)';

/** Se a baralla pode ir fixa e animada: non é unha pantalla ancha e baixa e non se pediu menos movemento. */
export function pinnedDeck(view: Window): boolean {
  return !reducedMotion(view) && !(view.matchMedia?.(STATIC_QUERY).matches ?? false);
}

/**
 * Se a baralla estreita cabe fixa: o título (coa súa marxe) e a tarxeta máis alta, no alto
 * visible entre a barra superior e a inferior (`room`). Se non (móbil pequeno, texto moi
 * grande, apaisado), unha debaixo doutra: cada paso enteiro.
 */
export function deckFits(room: number, title: number, cards: readonly number[]): boolean {
  return title + Math.max(0, ...cards) <= room;
}

const clamp = (value: number) => Math.max(0, Math.min(1, value));

/** Canto dura o paso dunha tarxeta á seguinte (o mesmo ca `--f-step-move` en home.css). */
export const STEP_MOVE_MS = 600;
/** O tempo mínimo que un paso queda á vista (co seu paso de entrada) antes de ir ao seguinte. */
export const STEP_DWELL_MS = 1100;
/** O mesmo, se o scroll xa vai dous ou máis pasos por diante (scroll rápido): algo máis áxil. */
export const STEP_DWELL_FAST_MS = 750;

/** O paso que corresponde ao percorrido da sección (0–1): un terzo cada un. */
export function targetStep(progress: number, count: number): number {
  return Math.min(count - 1, Math.floor(clamp(progress) * count));
}

/**
 * Canto agardar (ms) antes de mover a baralla un paso cara a `target`, se o paso actual leva
 * `since` ms á vista; `Infinity`, se xa está nel. Os pasos van dun en dun e cada un queda polo
 * menos o seu tempo, por rápido que sexa o scroll.
 */
export function stepDelay(current: number, target: number, since: number): number {
  if (current === target) return Infinity;
  const dwell = Math.abs(target - current) > 1 ? STEP_DWELL_FAST_MS : STEP_DWELL_MS;
  return Math.max(0, dwell - since);
}

/** O seguinte paso cara a `target`. */
export function stepToward(current: number, target: number): number {
  return current + Math.sign(target - current);
}

/** Progreso (0–1) dunha sección máis alta ca a pantalla: 0 co seu alto arriba de todo, 1 co seu pé abaixo. */
export function sectionProgress(top: number, height: number, viewport: number): number {
  const travel = height - viewport;
  return travel <= 0 ? 0 : clamp(-top / travel);
}
