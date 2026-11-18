// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CanDeactivateFn } from '@angular/router';

/** O que pregunta antes de saír do asistente con cambios sen gardar (saír sen gardar ou gardar e saír). */
export interface LeavesWithQuestion {
  canLeave(): Promise<boolean>;
}

/** Saír do asistente «Nova instalación» con cambios sen gardar pregunta. */
export const wizardLeaveGuard: CanDeactivateFn<LeavesWithQuestion> = (page) => page.canLeave();
