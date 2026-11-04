// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Posición dunha tarxeta da baralla: `t` canto entrou (0 fóra pola dereita, 1 pousada) e `u` canto ten enriba. */
export interface StepPose {
  readonly t: number;
  readonly u: number;
}

const clamp = (value: number) => Math.max(0, Math.min(1, value));
/** A curva `--ease-smooth` aproximada (saída rápida e pouso suave). */
const ease = (t: number) => 1 - Math.pow(1 - t, 3);

/**
 * Respaldo en JS das animacións por scroll (para navegadores sen `animation-timeline`): o mesmo
 * cálculo ca os @keyframes de home.css. `progress` é canto se percorreu da sección fixa (0–1); a
 * primeira tarxeta xa está pousada e cada seguinte entra pola dereita e pousa enriba da anterior.
 */
export function deckPoses(progress: number, count: number): StepPose[] {
  const p = clamp(progress) * (count - 1) * 1.15;
  return Array.from({ length: count }, (_, i) => ({
    t: i === 0 ? 1 : ease(clamp(p - (i - 1))),
    u: clamp(p - i),
  }));
}

/** Progreso (0–1) dunha sección máis alta ca a pantalla: 0 co seu alto arriba de todo, 1 co seu pé abaixo. */
export function sectionProgress(top: number, height: number, viewport: number): number {
  const travel = height - viewport;
  return travel <= 0 ? 0 : clamp(-top / travel);
}
