// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CanDeactivateFn } from '@angular/router';

/** O que pregunta antes de saír do asistente (seguir, descartar ou gardar o borrador). */
export interface LeavesWithQuestion {
  canLeave(): Promise<boolean>;
}

/** Saír do asistente «Nova instalación» a medias pregunta (maqueta 53). */
export const wizardLeaveGuard: CanDeactivateFn<LeavesWithQuestion> = (page) => page.canLeave();
