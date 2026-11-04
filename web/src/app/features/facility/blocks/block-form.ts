// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { ApiProblem } from '../../../core/errors/api-problem';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { applyFieldErrors } from '../../../shared/ui/edit-section/section-errors';
import type { Block, BlockBody } from './blocks.store';

/** Os mesmos límites ca `Block` da API. */
export const REASON_MAX = 200;
export const MINUTE_STEP = 15;
export const DAY_END = 24 * 60;
/** Un bloqueo novo propón 9:00–14:00, coma a semana e a maqueta. */
export const NEW_BLOCK = { startMinute: 9 * 60, endMinute: 14 * 60 } as const;
/** Códigos dos erros que tamén pon o servidor, para que o texto sexa o mesmo. */
export const INVALID_PERIOD = 'facility.block-invalid-period';
export const OVERLAP = 'facility.block-overlap';

export type BlockForm = FormGroup<{
  date: FormControl<IsoDate | null>;
  startMinute: FormControl<number | null>;
  endMinute: FormControl<number | null>;
  reason: FormControl<string>;
}>;

/** Outro bloqueo da instalación, para ver se se pisan. */
export type BlockSlot = Pick<Block, 'id' | 'date' | 'startMinute' | 'endMinute'>;

/**
 * Tramo do bloqueo (erro do grupo): remata despois de empezar e non pisa outro bloqueo do mesmo
 * día (P7; a API tampouco o deixa, 409 `block-overlap`). As horas comparadas son as de reloxo de
 * parede; o servidor, que calcula o período real co cambio de hora, segue a ser quen decide.
 */
function periodRule(others: () => readonly BlockSlot[]): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const { date, startMinute: start, endMinute: end } = (control as BlockForm).getRawValue();
    if (start === null || end === null) return null;
    if (end <= start) return { [INVALID_PERIOD]: { step: MINUTE_STEP } };
    const clash = others().find((o) => o.date === date && o.startMinute < end && start < o.endMinute);
    return clash ? { [OVERLAP]: { blockId: clash.id } } : null;
  };
}

/**
 * Formulario dun bloqueo (D1-24; especificación, bloque 2, «Bloqueos»): data, desde, ata e motivo
 * opcional. Un só día (P13); todo o día é 0:00–24:00. `others` son os outros bloqueos.
 */
export function blockForm(block: Block | null, others: () => readonly BlockSlot[]): BlockForm {
  return new FormGroup(
    {
      date: new FormControl<IsoDate | null>(block?.date ?? null, Validators.required),
      startMinute: new FormControl<number | null>(block?.startMinute ?? NEW_BLOCK.startMinute, Validators.required),
      endMinute: new FormControl<number | null>(block?.endMinute ?? NEW_BLOCK.endMinute, Validators.required),
      reason: new FormControl(block?.reason ?? '', {
        nonNullable: true,
        validators: Validators.maxLength(REASON_MAX),
      }),
    },
    { validators: periodRule(others) },
  );
}

/** Corpo do `POST`/`PUT`: o motivo baleiro non se manda. */
export function blockBody(form: BlockForm): BlockBody {
  const { date, startMinute, endMinute, reason } = form.getRawValue();
  const text = reason.trim();
  return {
    date: date ?? '',
    startMinute: startMinute ?? 0,
    endMinute: endMinute ?? 0,
    ...(text ? { reason: text } : {}),
  };
}

/**
 * Pon no seu campo os erros da API; o solape (`409` sen campo) vai ao tramo. Devolve se quedou
 * todo no formulario (se non, a sección amosa o aviso).
 */
export function applyBlockErrors(form: BlockForm, problem: ApiProblem): boolean {
  const unmatched = applyFieldErrors(form, problem, () => null);
  if (problem.code === OVERLAP && problem.fieldErrors.length === 0) {
    form.setErrors({ ...form.errors, [OVERLAP]: problem.params });
    form.markAllAsTouched();
    return true;
  }
  return unmatched === 0 && problem.fieldErrors.length > 0;
}
