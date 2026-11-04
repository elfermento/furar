// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../../api/api';
import { changeMyBooking } from '../../../api/fn/bookings/change-my-booking';
import { createMyBooking } from '../../../api/fn/bookings/create-my-booking';
import { getMyBooking } from '../../../api/fn/bookings/get-my-booking';
import { getMyBookingCalendar } from '../../../api/fn/bookings/get-my-booking-calendar';
import type { CreateMyBookingRequest } from '../../../api/models/create-my-booking-request';
import type { MyBookingResponse } from '../../../api/models/my-booking-response';
import { SessionService } from '../../../core/auth/session.service';
import { ApiProblem } from '../../../core/errors/api-problem';

export type { MyBookingResponse };

/**
 * As esperas antes de cada reintento automático de reservar (ms): tantos reintentos como esperas.
 * Nas probas, `[0, 0]`.
 */
export const BOOKING_RETRY_DELAYS = new InjectionToken<readonly number[]>('BOOKING_RETRY_DELAYS', {
  factory: () => [500, 1500],
});

/**
 * Respostas que non din se a reserva se creou: sen resposta (rede, 0) ou unha pasarela ou a API
 * que non responderon (502, 503, 504). Só estas se reintentan, e sempre coa mesma clave.
 */
const UNKNOWN_OUTCOME = new Set([0, 502, 503, 504]);

/**
 * As reservas da persoa na páxina de reservar. Gardar: `POST /me/bookings` ou, no modo cambio
 * (`?cambiar=`), `PUT /me/bookings/{id}/slot` coa versión da reserva que se cambia (`If-Match`).
 * A confirmada (`?reserva=`) é a que devolveu a API ou, se se recarga, `GET /me/bookings/{id}`
 * (sen sesión, primeiro a Keycloak e volta aquí). «Engadir ao calendario» descarga o seu `.ics`
 * co token. Un por páxina.
 *
 * Reservar é idempotente (ADR-0025, punto 19): cada intención de reservar (o mesmo oco, uso,
 * franxas e parte) leva unha `Idempotency-Key` (`crypto.randomUUID()`). Se a resposta non chega,
 * reinténtase só coa mesma clave e, se a persoa volve confirmar o mesmo, tamén; a API devolve a
 * reserva xa feita en vez de crear outra ou dicir que alguén collera a hora. Unha resposta clara
 * (feita ou un erro 4xx) pecha a intención: a seguinte confirmación leva clave nova.
 */
@Injectable()
export class ConfirmedBookingStore {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly document = inject(DOCUMENT);
  private readonly retryDelays = inject(BOOKING_RETRY_DELAYS);

  /** A intención de reservar aberta: o que se pide e a súa clave. */
  private intent: { request: string; key: string } | null = null;

  readonly booking = signal<MyBookingResponse | null>(null);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  /** A reserva que se cambia (modo cambio). */
  readonly original = signal<MyBookingResponse | null>(null);
  readonly originalState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');

  /** Crea a reserva ou, no modo cambio, cambia a orixinal ao oco novo. */
  async save(slug: string, body: CreateMyBookingRequest): Promise<MyBookingResponse> {
    const original = this.original();
    const slot = {
      useId: body.useId,
      start: body.start,
      slots: body.slots,
      ...(body.partId ? { partId: body.partId } : {}),
    };
    const booking = original
      ? await this.api.invoke(changeMyBooking, {
          slug,
          id: original.id,
          'If-Match': `"${original.version}"`,
          body: slot,
        })
      : await this.create(slug, body);
    this.set(booking);
    return booking;
  }

  /** `POST /me/bookings` coa clave da intención; reintenta se non se sabe se chegou. */
  private async create(slug: string, body: CreateMyBookingRequest): Promise<MyBookingResponse> {
    const request = JSON.stringify([
      body.facilityId,
      body.useId,
      body.start,
      body.slots,
      body.partId ?? null,
      body.customFields ?? null,
    ]);
    if (this.intent?.request !== request) {
      this.intent = { request, key: globalThis.crypto.randomUUID() };
    }
    const key = this.intent.key;
    for (let attempt = 0; ; attempt++) {
      try {
        const booking = await this.api.invoke(createMyBooking, { slug, 'Idempotency-Key': key, body });
        this.intent = null;
        return booking;
      } catch (error) {
        const status = error instanceof ApiProblem ? error.status : 0;
        // Unha resposta clara pecha a intención; se non, a clave queda para o seguinte intento.
        if (!UNKNOWN_OUTCOME.has(status)) this.intent = null;
        if (!UNKNOWN_OUTCOME.has(status) || attempt >= this.retryDelays.length) throw error;
        await new Promise((resolve) => setTimeout(resolve, this.retryDelays[attempt]));
      }
    }
  }

  /** A reserva que se vai cambiar (`?cambiar=`); sen sesión, primeiro a Keycloak. */
  async loadOriginal(slug: string, id: string): Promise<MyBookingResponse | null> {
    if (this.original()?.id === id) return this.original();
    if (!this.session.authenticated()) {
      await this.session.login(this.router.url);
      return null;
    }
    this.originalState.set('loading');
    try {
      const booking = await this.api.invoke(getMyBooking, { slug, id });
      this.original.set(booking);
      this.originalState.set('ready');
      return booking;
    } catch {
      this.originalState.set('error');
      return null;
    }
  }

  /** A reserva que xa temos (a resposta do `POST`). */
  set(booking: MyBookingResponse): void {
    this.booking.set(booking);
    this.state.set('ready');
  }

  async load(slug: string, id: string): Promise<void> {
    if (this.booking()?.id === id) return;
    if (!this.session.authenticated()) {
      await this.session.login(this.router.url);
      return;
    }
    this.state.set('loading');
    try {
      this.set(await this.api.invoke(getMyBooking, { slug, id }));
    } catch {
      this.state.set('error');
    }
  }

  /** O `.ics` da reserva, pedido co token e gardado como `reserva-FUR-XXXX.ics`. */
  async downloadCalendar(slug: string): Promise<void> {
    const booking = this.booking();
    const view = this.document.defaultView;
    if (!booking || !view) return;
    const blob = await this.api.invoke(getMyBookingCalendar, { slug, id: booking.id });
    const url = view.URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = `reserva-${booking.code}.ics`;
    link.click();
    view.setTimeout(() => view.URL.revokeObjectURL(url), 0);
  }
}
