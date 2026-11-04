// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type {
  PaymentNotesRequest,
  PaymentNotesResponse,
  SportsContactRequest,
  SportsContactResponse,
} from './municipality.store';

/**
 * Contacto do servizo de deportes e textos de pago e devolución nos axustes do concello (D2-11;
 * plan do dominio 2, P8), coas mesmas regras ca `SportsContact` e `PaymentNotes` da API: todo
 * opcional; nome e enderezo ata 200 caracteres; teléfono con díxitos e `+ ( ) . / -` ata 30; correo
 * con formato; cada nota, nos dous idiomas ou en ningún (baleira = o texto de Furar), ata 500.
 */
export const CONTACT_LIMITS = { text: 200, phone: 30 } as const;
export const NOTE_MAX = 500;
const PHONE = /^[0-9+()./ -]+$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export type ContactForm = FormGroup<{
  name: FormControl<string>;
  address: FormControl<string>;
  phone: FormControl<string>;
  email: FormControl<string>;
}>;
export type NoteForm = FormGroup<{ gl: FormControl<string>; es: FormControl<string> }>;
export type NotesForm = FormGroup<{ payment: NoteForm; refund: NoteForm }>;

const trimmed = (value: unknown): string => (typeof value === 'string' ? value.trim() : '');

function size(max: number): ValidatorFn {
  return (control) => (trimmed(control.value).length > max ? { 'validation.size': { min: 0, max } } : null);
}

function pattern(regex: RegExp, max: number): ValidatorFn {
  return (control) => {
    const value = trimmed(control.value);
    return value && (value.length > max || !regex.test(value)) ? { 'validation.pattern': {} } : null;
  };
}

/** Nos dous idiomas ou en ningún, e ata `NOTE_MAX` cada un (os erros da API, en cada idioma). */
function noteText(other: 'gl' | 'es'): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = trimmed(control.value);
    if (!value) return trimmed(control.parent?.get(other)?.value) ? { 'validation.required': {} } : null;
    return value.length > NOTE_MAX ? { 'validation.size': { min: 1, max: NOTE_MAX } } : null;
  };
}

export function contactForm(contact: SportsContactResponse = {}): ContactForm {
  return new FormGroup({
    name: new FormControl(contact.name ?? '', { nonNullable: true, validators: size(CONTACT_LIMITS.text) }),
    address: new FormControl(contact.address ?? '', { nonNullable: true, validators: size(CONTACT_LIMITS.text) }),
    phone: new FormControl(contact.phone ?? '', {
      nonNullable: true,
      validators: pattern(PHONE, CONTACT_LIMITS.phone),
    }),
    email: new FormControl(contact.email ?? '', {
      nonNullable: true,
      validators: pattern(EMAIL, CONTACT_LIMITS.text),
    }),
  });
}

function noteForm(note: { gl: string; es: string } | null): NoteForm {
  const form: NoteForm = new FormGroup({
    gl: new FormControl(note?.gl ?? '', { nonNullable: true, validators: noteText('es') }),
    es: new FormControl(note?.es ?? '', { nonNullable: true, validators: noteText('gl') }),
  });
  form.controls.gl.valueChanges.subscribe(() => form.controls.es.updateValueAndValidity({ emitEvent: false }));
  form.controls.es.valueChanges.subscribe(() => form.controls.gl.updateValueAndValidity({ emitEvent: false }));
  return form;
}

/** As notas propias do concello; se usa as de Furar, os campos van baleiros. */
export function notesForm(notes: PaymentNotesResponse | null): NotesForm {
  return new FormGroup({
    payment: noteForm(notes && !notes.paymentDefault ? notes.payment : null),
    refund: noteForm(notes && !notes.refundDefault ? notes.refund : null),
  });
}

export function contactBody(form: ContactForm): SportsContactRequest {
  const value = form.getRawValue();
  return {
    name: value.name.trim(),
    address: value.address.trim(),
    phone: value.phone.trim(),
    email: value.email.trim(),
  };
}

export function notesBody(form: NotesForm): PaymentNotesRequest {
  const { payment, refund } = form.getRawValue();
  return {
    payment: { gl: payment.gl.trim(), es: payment.es.trim() },
    refund: { gl: refund.gl.trim(), es: refund.es.trim() },
  };
}
