// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { undoBookingAttendance } from '../../../api/fn/bookings/undo-booking-attendance';
import { validateBooking } from '../../../api/fn/bookings/validate-booking';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import type { BookingValidationResponse } from '../../../api/models/booking-validation-response';
import type { FacilitySummaryResponse } from '../../../api/models/facility-summary-response';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';

export type { BookingValidationResponse };

/** Como vai a lectura dun código. */
export type ValidateState = 'idle' | 'loading' | 'ready' | 'notFound' | 'error';

/** Instalacións que se piden para a miniatura da reserva lida. */
const FACILITIES_SIZE = 100;

/**
 * «Validar QR» (ADR-0032, punto 4): ler un código (`POST …/bookings/validations`), que
 * valida soa a reserva se toca, «Validar igualmente» cun motivo e «Desfacer». Só vale a última
 * lectura: se chega outro código antes da resposta, a anterior descártase. Un por páxina.
 */
@Injectable()
export class ValidateStore {
  private readonly api = inject(Api);
  private request = 0;

  readonly state = signal<ValidateState>('idle');
  /** O resultado da última lectura, cos datos da reserva. */
  readonly result = signal<BookingValidationResponse | null>(null);
  /** O erro dunha lectura que fallou (non o de «non existe»). */
  readonly problem = signal<ApiProblem | null>(null);
  /** As instalacións, para a miniatura: se non chegan, só o nome e o enderezo. */
  readonly facilities = signal<readonly FacilitySummaryResponse[]>([]);

  async loadFacilities(slug: string): Promise<void> {
    try {
      const page = await this.api.invoke(listFacilities, { slug, page: 0, size: FACILITIES_SIZE });
      this.facilities.set(page.items);
    } catch {
      this.facilities.set([]);
    }
  }

  /** Le o código: dentro da marxe, a reserva queda validada. */
  read(slug: string, code: string): Promise<void> {
    return this.send(slug, { code });
  }

  /**
   * «Validar igualmente», fóra da marxe, co seu motivo. Se falla, o resultado de antes queda á
   * vista e o erro lánzase para que a páxina o mostre.
   */
  async force(slug: string, code: string, reason: string): Promise<void> {
    const request = ++this.request;
    const result = await this.api.invoke(validateBooking, { slug, body: { code, force: true, reason } });
    if (request !== this.request) return;
    this.result.set(result);
    this.state.set('ready');
  }

  /** Desfai a validación da reserva lida, coa versión que se ve. */
  async undo(slug: string): Promise<void> {
    const booking = this.result()?.booking;
    if (!booking) return;
    await this.api.invoke(undoBookingAttendance, { slug, id: booking.id, 'If-Match': `"${booking.version}"` });
    this.clear();
  }

  /** Volve ao principio: sen código nin resultado. */
  clear(): void {
    this.request++;
    this.result.set(null);
    this.problem.set(null);
    this.state.set('idle');
  }

  private async send(slug: string, body: { code: string }): Promise<void> {
    const request = ++this.request;
    this.state.set('loading');
    // O resultado do código anterior non queda á vista mentres chega o deste.
    this.result.set(null);
    this.problem.set(null);
    try {
      const result = await this.api.invoke(validateBooking, { slug, body });
      if (request !== this.request) return;
      this.result.set(result);
      this.state.set('ready');
    } catch (error) {
      if (request !== this.request) return;
      this.result.set(null);
      const problem = asProblem(error);
      if (problem.status === 404) {
        this.state.set('notFound');
      } else {
        this.problem.set(problem);
        this.state.set('error');
      }
    }
  }
}
