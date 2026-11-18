// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl } from '@angular/forms';
import { FormArray, FormGroup } from '@angular/forms';
import type { ControlError } from '../custom-fields/custom-field-controls';
import { focusErrorOrAlert } from './focus-error/focus-error';

/** Validadores de Angular → mesmos códigos e textos que os erros da API. */
function toControlError(name: string, value: unknown): ControlError {
  const raw = typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
  switch (name) {
    case 'required':
      return { key: 'errors.validation.required', params: {} };
    case 'maxlength':
      return { key: 'errors.customfield.too-long', params: { max: raw['requiredLength'] } };
    case 'minlength':
      return { key: 'errors.customfield.too-short', params: { min: raw['requiredLength'] } };
    case 'pattern':
      return { key: 'errors.validation.pattern', params: {} };
    case 'min':
    case 'max':
      return { key: 'errors.validation.range', params: {} };
    default:
      // Erros propios ou da API: o nome xa é o código (p. ex. municipality.slug-taken).
      return { key: `errors.${name}`, params: raw };
  }
}

/** Erro que amosar nun campo: só se se tocou ou xa se intentou enviar. */
export function fieldError(control: AbstractControl | null | undefined, submitted: boolean): ControlError | null {
  if (!control?.errors || (!control.touched && !submitted)) {
    return null;
  }
  const [name, value] = Object.entries(control.errors)[0] ?? [];
  return name ? toControlError(name, value) : null;
}

/** aria-describedby para un campo con id `id` (axuda en id-help, erro en id-error). */
export function describedBy(id: string, hasHelp: boolean, error: unknown): string | null {
  const ids = [hasHelp ? `${id}-help` : '', error ? `${id}-error` : ''].filter(Boolean);
  return ids.length ? ids.join(' ') : null;
}

/** Número de campos (controis folla) con erro e erros do propio grupo, para o resumo. */
export function countInvalid(control: AbstractControl): number {
  const own = control.errors ? 1 : 0;
  if (control instanceof FormGroup || control instanceof FormArray) {
    return (
      own +
      Object.values(control.controls).reduce((sum: number, child: AbstractControl) => sum + countInvalid(child), 0)
    );
  }
  return control.enabled ? own : 0;
}

/**
 * Ao fallar un gardado: foco no primeiro campo inválido do contedor (na orde de lectura, á vista
 * coa súa mensaxe) ou, se o erro non é dun campo, no aviso de erro (`focusErrorOrAlert`).
 */
export function focusFirstInvalid(container: HTMLElement): void {
  focusErrorOrAlert(container);
}
