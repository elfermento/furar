// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { JoinRequestBody } from './join-request.store';

/** Os límites da API (`JoinRequest`, ADR-0042). */
export const JOIN_REQUEST_LIMITS = {
  municipality: 160,
  name: 120,
  role: 120,
  email: 254,
  phone: 30,
  message: 2000,
} as const;

/** Un correo completo, co dominio e o seu punto (coma na API). */
export const JOIN_EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Coma os teléfonos das persoas e entidades nas reservas. */
export const JOIN_PHONE_PATTERN = /^[0-9+() .-]+$/;

/** Obrigatorio: só espazos tampouco vale (a API recórtaos). */
function filled(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() ? null : { required: true };
}

const text = (validators: ValidatorFn[]) => new FormControl('', { nonNullable: true, validators });

export function joinRequestForm() {
  const max = JOIN_REQUEST_LIMITS;
  return new FormGroup({
    municipality: text([filled, Validators.maxLength(max.municipality)]),
    name: text([filled, Validators.maxLength(max.name)]),
    role: text([Validators.maxLength(max.role)]),
    email: text([filled, Validators.maxLength(max.email), Validators.pattern(JOIN_EMAIL_PATTERN)]),
    phone: text([
      filled,
      Validators.minLength(3),
      Validators.maxLength(max.phone),
      Validators.pattern(JOIN_PHONE_PATTERN),
    ]),
    message: text([Validators.maxLength(max.message)]),
    /** Campo trampa: escondido ás persoas; se trae algo, a API descarta a solicitude sen dicilo. */
    website: text([]),
  });
}

export type JoinRequestForm = ReturnType<typeof joinRequestForm>;

/** O corpo da petición: sen espazos ao redor e sen os opcionais baleiros. */
export function joinRequestBody(form: JoinRequestForm): JoinRequestBody {
  const v = form.getRawValue();
  const optional = (value: string) => (value.trim() ? value.trim() : undefined);
  return {
    municipality: v.municipality.trim(),
    name: v.name.trim(),
    role: optional(v.role),
    email: v.email.trim(),
    phone: v.phone.trim(),
    message: optional(v.message),
    website: v.website,
  };
}
