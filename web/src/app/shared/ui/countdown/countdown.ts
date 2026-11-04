// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { WritableSignal } from '@angular/core';
import { computed, effect, signal } from '@angular/core';

/** Cada canto avanza o reloxo mentres hai algunha conta atrás. */
export const COUNTDOWN_TICK_MS = 1000;

/**
 * Reloxo (ms desde a época) para as contas atrás: avanza cada segundo só mentres algún prazo de
 * `deadlines` está no futuro; cando non queda ningún, o temporizador párase, e volve arrancar se
 * chega un prazo novo. Créase nun contexto de inxección (un campo dun compoñente): ao destruílo,
 * o temporizador vai con el, sen intervalos orfos. Pódese poñer á hora a man (`set(Date.now())`).
 */
export function countdownClock(deadlines: () => readonly number[]): WritableSignal<number> {
  const now = signal(Date.now());
  const pending = computed(() => {
    const at = now();
    return deadlines().some((deadline) => deadline > at);
  });
  effect((onCleanup) => {
    if (!pending()) return;
    const id = setInterval(() => now.set(Date.now()), COUNTDOWN_TICK_MS);
    onCleanup(() => clearInterval(id));
  });
  return now;
}

/** O que queda ata un prazo, en `m:ss` («1:45», «0:05»); os segundos cara arriba e nunca negativo. */
export function formatCountdown(remainingMs: number): string {
  const total = Math.max(0, Math.ceil(remainingMs / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}
