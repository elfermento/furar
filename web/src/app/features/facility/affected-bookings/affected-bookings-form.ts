// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import type { AffectedBooking, BookingAction, BookingDecision, NotifyChannel } from './affected-bookings.store';

/** Orde fixa das canles: correo e aviso na web (a reserva non garda teléfono). */
export const CHANNELS: readonly NotifyChannel[] = ['EMAIL', 'WEB'];

export type ChannelsForm = FormGroup<Record<NotifyChannel, FormControl<boolean>>>;

/** Unha reserva: marcada para as accións en bloque, cancelar ou manter, e como avisar. */
export type DecisionForm = FormGroup<{
  marked: FormControl<boolean>;
  action: FormControl<BookingAction | null>;
  channels: ChannelsForm;
}>;

export type DecisionValue = ReturnType<DecisionForm['getRawValue']>;

/** Que lle falta a unha reserva para poder confirmar (P18). */
export type DecisionProblem = 'action' | 'channel';

/** As canles que ten sentido ofrecer: correo só se ten correo; a web, sempre. */
export function channelsFor(booking: AffectedBooking): NotifyChannel[] {
  return CHANNELS.filter((c) => (c === 'EMAIL' ? booking.hasEmail : true));
}

/** Canle que se marca ao escoller «Cancelar» (P18): correo; se non ten, a web. */
export function defaultChannel(booking: AffectedBooking): NotifyChannel {
  return booking.hasEmail ? 'EMAIL' : 'WEB';
}

export function problemOf(value: Pick<DecisionValue, 'action' | 'channels'>): DecisionProblem | null {
  if (value.action === null) return 'action';
  if (value.action === 'CANCEL' && !CHANNELS.some((c) => value.channels[c])) return 'channel';
  return null;
}

const decisionValidator = (control: AbstractControl): ValidationErrors | null => {
  const problem = problemOf((control as DecisionForm).getRawValue());
  return problem ? { [problem]: true } : null;
};

/**
 * Formulario dunha reserva. Por defecto nada vén marcado (P18); se xa se decidira antes (a lista
 * volveu cambiar entre os dous pasos), consérvase. Ao escoller «Cancelar» sen canle márcase a por
 * defecto; ao escoller «Manter» quítanse as canles («se se mantén, non se avisa»).
 */
export function decisionForm(booking: AffectedBooking, previous?: DecisionValue): DecisionForm {
  const offered = channelsFor(booking);
  const channel = (c: NotifyChannel) =>
    new FormControl(
      { value: !!previous?.channels[c] && offered.includes(c), disabled: !offered.includes(c) },
      {
        nonNullable: true,
      },
    );
  const form: DecisionForm = new FormGroup(
    {
      marked: new FormControl(previous?.marked ?? false, { nonNullable: true }),
      action: new FormControl<BookingAction | null>(previous?.action ?? null),
      channels: new FormGroup({ EMAIL: channel('EMAIL'), WEB: channel('WEB') }),
    },
    { validators: decisionValidator },
  );
  form.controls.action.valueChanges.subscribe((action) => {
    const channels = form.controls.channels;
    if (action === 'KEEP') {
      channels.setValue({ EMAIL: false, WEB: false });
    } else if (action === 'CANCEL' && !CHANNELS.some((c) => channels.controls[c].value)) {
      channels.controls[defaultChannel(booking)].setValue(true);
    }
  });
  return form;
}

/** Lista de formularios, conservando o decidido para as reservas que seguen na lista. */
export function decisionsArray(
  bookings: readonly AffectedBooking[],
  previous: ReadonlyMap<string, DecisionValue> = new Map(),
): FormArray<DecisionForm> {
  return new FormArray(bookings.map((booking) => decisionForm(booking, previous.get(booking.bookingId))));
}

/** O que se reenvía: `channels` baleiro ao manter (§1.2.6). */
export function toDecision(booking: AffectedBooking, value: DecisionValue): BookingDecision {
  const action = value.action ?? 'KEEP';
  return {
    bookingId: booking.bookingId,
    action,
    channels: action === 'CANCEL' ? CHANNELS.filter((c) => value.channels[c]) : [],
  };
}
