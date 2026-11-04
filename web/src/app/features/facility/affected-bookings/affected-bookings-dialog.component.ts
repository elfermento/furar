// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { map } from 'rxjs';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { RowListDirective } from '../../../shared/ui/row/row-list.directive';
import { AffectedBookingComponent } from './affected-booking.component';
import type { DecisionValue } from './affected-bookings-form';
import { decisionsArray, problemOf, toDecision } from './affected-bookings-form';
import type { AffectedBooking, BookingAction, BookingDecision } from './affected-bookings.store';

/**
 * Que fai «Confirmar»: reenvía a petición coas decisións. Resolve `null` se xa está feito ou a lista
 * nova se as afectadas cambiaron entre os dous pasos (outro 409); rexeita con calquera outro erro.
 */
export type SubmitDecisions = (decisions: BookingDecision[]) => Promise<AffectedBooking[] | null>;

export interface AffectedBookingsDialogData {
  readonly affected: readonly AffectedBooking[];
  /** Que cambio as afecta («Bloqueo do 3 nov, 8:00–12:00 · Mantemento»); se non, un texto xeral. */
  readonly summary?: string;
  readonly submit: SubmitDecisions;
}

/** Como pecha: `done` (gardado), `undefined` (Volver, Esc, Pechar) ou co erro do reenvío. */
export type AffectedBookingsDialogResult = 'done' | { readonly failed: unknown } | undefined;

/**
 * Modal de reservas afectadas (D1-19; especificación, bloque 2; plan §1.2.6): unha lista de
 * `fieldset` (un por reserva) con «Seleccionar todas» e «Cancelar/Manter as marcadas». «Confirmar»
 * pide decisión en todas e polo menos unha canle en cada cancelada (P18); se falta algo, aviso e
 * foco no primeiro que falta. Mentres se reenvía, a capa queda ocupada e non se pode pechar.
 */
@Component({
  selector: 'furar-affected-bookings-dialog',
  imports: [
    AffectedBookingComponent,
    ButtonDirective,
    CalloutComponent,
    DialogComponent,
    RowListDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './affected-bookings-dialog.component.html',
})
export class AffectedBookingsDialogComponent {
  private readonly data = inject<AffectedBookingsDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<AffectedBookingsDialogResult>>(DialogRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly transloco = inject(TranslocoService);

  protected readonly bookings = signal<readonly AffectedBooking[]>(this.data.affected);
  protected readonly form = decisionsArray(this.data.affected);
  protected readonly values = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  /** A lista cambiou entre os dous pasos: aviso para revisala. */
  protected readonly changed = signal(false);

  protected readonly summary = computed(
    () => this.data.summary || this.transloco.translate('facility.affectedBookings.intro'),
  );
  protected readonly marked = computed(() => this.values().filter((v) => v.marked).length);
  protected readonly allMarked = computed(() => this.values().length > 0 && this.marked() === this.values().length);
  protected readonly counts = computed(() => {
    const values = this.values();
    return {
      pending: values.filter((v) => v.action === null).length,
      cancel: values.filter((v) => v.action === 'CANCEL').length,
      keep: values.filter((v) => v.action === 'KEEP').length,
    };
  });
  protected readonly incomplete = computed(() => this.values().filter((v) => problemOf(v) !== null).length);

  protected markAll(marked: boolean): void {
    for (const row of this.form.controls) row.controls.marked.setValue(marked, { emitEvent: false });
    this.form.updateValueAndValidity();
  }

  /** «Cancelar/Manter as marcadas»: só toca as marcadas e desmárcaas despois. */
  protected applyToMarked(action: BookingAction): void {
    for (const row of this.form.controls) {
      if (row.controls.marked.value) {
        row.controls.action.setValue(action);
        row.controls.marked.setValue(false);
      }
    }
  }

  protected async confirm(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    if (this.form.invalid) {
      this.focusFirstProblem();
      return;
    }
    const bookings = this.bookings();
    const decisions = this.values().map((value, i) => toDecision(bookings[i], value));
    this.busy.set(true);
    try {
      const next = await this.data.submit(decisions);
      this.busy.set(false);
      if (next === null) {
        this.ref.close('done');
      } else {
        this.replace(next);
      }
    } catch (error) {
      this.busy.set(false);
      this.ref.close({ failed: error });
    }
  }

  /** Lista nova tras outro 409: consérvase o decidido para as que seguen e vólvese ao título. */
  private replace(next: readonly AffectedBooking[]): void {
    const previous = new Map<string, DecisionValue>(
      this.bookings().map((booking, i) => [booking.bookingId, this.values()[i]]),
    );
    const fresh = decisionsArray(next, previous);
    this.form.clear({ emitEvent: false });
    for (const row of fresh.controls) this.form.push(row, { emitEvent: false });
    this.bookings.set(next);
    this.form.updateValueAndValidity();
    this.submitted.set(false);
    this.changed.set(true);
    queueMicrotask(() => {
      const title = this.host.nativeElement.querySelector<HTMLElement>('h2');
      title?.setAttribute('tabindex', '-1');
      title?.focus();
    });
  }

  /** Foco no primeiro que falta: o cancelar/manter da reserva ou a súa primeira canle. */
  private focusFirstProblem(): void {
    const values = this.values();
    const index = values.findIndex((v) => problemOf(v) !== null);
    const booking = this.bookings()[index];
    if (!booking) return;
    const row = this.host.nativeElement.querySelector<HTMLElement>(`fieldset[data-booking="${booking.bookingId}"]`);
    const target =
      problemOf(values[index]) === 'action'
        ? row?.querySelector<HTMLElement>('[role="radio"][tabindex="0"]')
        : row?.querySelector<HTMLElement>('input[data-channel]:not(:disabled)');
    target?.focus();
  }
}
