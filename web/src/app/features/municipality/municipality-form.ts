// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { ThemeResponse } from './municipality.store';
import type { Language } from '../../core/i18n/languages';
import { checkTheme } from '../../core/theme/contrast';
import type { ThemeForm } from './theme-editor.component';

const HEX = /^#[0-9a-fA-F]{6}$/;
export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/** Idiomas: polo menos un, e o de por defecto entre eles (municipality.languages-invalid). */
export function languagesValidator(control: AbstractControl): ValidationErrors | null {
  const { gl, es, defaultLanguage } = (control as LanguagesForm).getRawValue();
  const chosen = [gl ? 'gl' : null, es ? 'es' : null].filter(Boolean);
  return chosen.length === 0 || !chosen.includes(defaultLanguage) ? { 'municipality.languages-invalid': {} } : null;
}

/** Contraste AA como na API (municipality.theme-low-contrast). */
export function contrastValidator(control: AbstractControl): ValidationErrors | null {
  const failing = checkTheme((control as ThemeForm).getRawValue()).find((check) => !check.ok);
  return failing ? { 'theme.low-contrast': {} } : null;
}

export type LanguagesForm = FormGroup<{
  gl: FormControl<boolean>;
  es: FormControl<boolean>;
  defaultLanguage: FormControl<Language>;
}>;

export function languagesForm(languages: readonly Language[], defaultLanguage: Language): LanguagesForm {
  return new FormGroup(
    {
      gl: new FormControl(languages.includes('gl'), { nonNullable: true }),
      es: new FormControl(languages.includes('es'), { nonNullable: true }),
      defaultLanguage: new FormControl<Language>(defaultLanguage, { nonNullable: true }),
    },
    { validators: languagesValidator },
  );
}

export function languagesOf(form: LanguagesForm): Language[] {
  const { gl, es } = form.getRawValue();
  return [...(gl ? (['gl'] as const) : []), ...(es ? (['es'] as const) : [])];
}

export function themeForm(theme: ThemeResponse): ThemeForm {
  const color = (value: string) =>
    new FormControl(value, { nonNullable: true, validators: [Validators.required, Validators.pattern(HEX)] });
  return new FormGroup(
    {
      primary: color(theme.primary),
      onPrimary: color(theme.onPrimary),
      accent: color(theme.accent),
      background: color(theme.background),
      text: color(theme.text),
    },
    { validators: contrastValidator },
  );
}

export function nameControl(value = ''): FormControl<string> {
  return new FormControl(value, { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] });
}
