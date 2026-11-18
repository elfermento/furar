// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl } from '@angular/forms';
import { ApiProblem } from '../../../core/errors/api-problem';

/** Busca o control da ruta que manda a API: «name», «address.street», «parts[1].name». */
export function controlAtPath(form: AbstractControl, path: string): AbstractControl | null {
  if (!path) {
    return null;
  }
  return form.get(path.replace(/\[(\d+)\]/g, '.$1'));
}

/**
 * Pon no seu control cada erro de campo da API (o código como clave do erro, igual ca o resto da
 * web) e márcao tocado. Devolve cantos non atoparon control.
 */
export function applyFieldErrors(
  form: AbstractControl,
  problem: ApiProblem,
  controlFor: (field: string) => AbstractControl | null,
): number {
  let unmatched = 0;
  for (const error of problem.fieldErrors) {
    const control = controlFor(error.field) ?? controlAtPath(form, error.field);
    if (control) {
      control.setErrors({ ...control.errors, [error.code]: error.params });
      control.markAsTouched();
    } else {
      unmatched++;
    }
  }
  return unmatched;
}

/** Calquera erro como ApiProblem: o que non vén da API móstrase como «erro inesperado». */
export function asProblem(error: unknown): ApiProblem {
  return error instanceof ApiProblem ? error : new ApiProblem(0, 'common.unexpected', {}, [], undefined, '');
}

const FOCUSABLE = 'input:not([type="hidden"]), select, textarea, button, [tabindex]:not([tabindex="-1"])';

/**
 * Foco no primeiro campo con erro do contedor, na orde de lectura (`aria-invalid="true"` ou un
 * control `ng-invalid` tocado), á vista coa súa mensaxe. A peza común é `focus-error/focus-error.ts`.
 */
export { focusFirstError } from '../focus-error/focus-error';

/** Primeiro elemento enfocable dun contedor (o campo que se abre ao editar). */
export function firstFocusable(container: HTMLElement): HTMLElement | null {
  return container.querySelector<HTMLElement>(FOCUSABLE);
}
