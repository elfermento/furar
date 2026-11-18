// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl } from '@angular/forms';
import type { ControlError } from '../../shared/custom-fields/custom-field-controls';
import { fieldError } from '../../shared/ui/field-errors';
import type { EventForm } from './event-form';

/** Os erros que se ven de cada campo (despois de tocalo ou de intentar gardar). */
export function formErrors(form: EventForm, shown: boolean) {
  const c = form.controls;
  const at = (control: AbstractControl): ControlError | null => fieldError(control, shown);
  return {
    date: at(c.date),
    minute: at(c.minute),
    capacity: at(c.capacity),
    price: at(c.price),
    deadline: at(c.deadline),
    birthYearFrom: at(c.birthYearFrom),
    birthYearTo: at(c.birthYearTo),
    schedule: {
      weekdays: at(c.weekdays) ?? undefined,
      hours: at(c.startMinute) ?? undefined,
      dates: at(c.firstDate) ?? undefined,
    },
    place: { facilityId: at(c.facilityId), useId: at(c.useId), partId: at(c.partId), place: at(c.place) },
  };
}
