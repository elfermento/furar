// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { DestroyRef, Injectable, Injector, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { BookingCancelStore } from '../../shared/booking-cancel/booking-cancel.store';
import type { CancelBookingData } from '../../shared/booking-cancel/cancel-booking-dialog.component';
import { CancelBookingDialogComponent } from '../../shared/booking-cancel/cancel-booking-dialog.component';
import type { ScheduleConflict } from '../../shared/schedule/schedule';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { EventSessionsPreviewRequest, EventSessionsPreviewResponse } from './events.store';
import { EventsStore } from './events.store';

/** Espera antes de pedir a vista previa mentres se escolle (ms). */
export const PREVIEW_DELAY = 300;

/**
 * A vista previa das sesións dunha actividade nova (ADR-0041, punto 4): pídese soa en canto o
 * horario está completo e cada vez que cambia; «Cancelar a reserva» dun día ocupado abre o diálogo
 * de sempre (un interruptor por canle) e volve pedila. Unha por diálogo.
 */
@Injectable()
export class ActivityPreview {
  private readonly store = inject(EventsStore);
  private readonly bookings = inject(BookingCancelStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly injector = inject(Injector);

  readonly preview = signal<EventSessionsPreviewResponse | null>(null);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly problem = signal<ApiProblem | null>(null);

  private slug = '';
  private body: () => EventSessionsPreviewRequest | null = () => null;
  private request = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request++);
  }

  /** Empeza a seguir o horario (no contexto de inxección de quen o usa). */
  watch(slug: string, body: Signal<EventSessionsPreviewRequest | null>): void {
    this.slug = slug;
    this.body = body;
    effect(
      (onCleanup) => {
        if (!body()) {
          untracked(() => {
            this.request++;
            this.preview.set(null);
            this.state.set('idle');
          });
          return;
        }
        const timer = setTimeout(() => void this.refresh(), PREVIEW_DELAY);
        onCleanup(() => clearTimeout(timer));
      },
      { injector: this.injector },
    );
  }

  /** Pide de novo a vista previa. */
  async refresh(): Promise<void> {
    const body = this.body();
    if (!body) return;
    const request = ++this.request;
    this.state.set('loading');
    try {
      const preview = await this.store.preview(this.slug, body);
      if (request !== this.request) return;
      this.preview.set(preview);
      this.state.set('ready');
    } catch (error) {
      if (request !== this.request) return;
      this.problem.set(asProblem(error));
      this.state.set('error');
    }
  }

  /** «Cancelar a reserva» dun día ocupado: o diálogo de sempre e outra vista previa. */
  async cancelConflict(conflict: ScheduleConflict): Promise<void> {
    if (!conflict.bookingId) return;
    this.problem.set(null);
    try {
      const booking = await this.bookings.booking(this.slug, conflict.bookingId);
      const ref = this.dialogs.open<boolean | { failed: unknown }, CancelBookingData, CancelBookingDialogComponent>(
        CancelBookingDialogComponent,
        { data: { booking }, injector: this.injector, autoFocus: '[data-cancel-keep]' },
      );
      const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
      if (result === true) {
        this.toasts.show('booking.manage.detail.done.cancelled');
        await this.refresh();
      } else if (typeof result === 'object') {
        this.problem.set(asProblem(result.failed));
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
