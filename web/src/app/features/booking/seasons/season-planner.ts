// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, Injector, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { CancelBookingData } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import { CancelBookingDialogComponent } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import type { DraftErrors, SeasonDraft } from './seasons';
import { EMPTY_DRAFT, conflictDates, draftErrors, scheduleOf } from './seasons';
import type { ScheduleConflict } from '../../../shared/schedule/schedule';
import type { SeriesPreviewResponse, SeriesResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** Espera antes de pedir a vista previa mentres se escolle (ms). */
export const PREVIEW_DELAY = 300;

/**
 * Crear unha tempada na xestión (ADR-0039, punto 8): o borrador, os seus erros, a vista
 * previa (pedida soa en canto o borrador está completo e cada vez que cambia) e crear co `skip` que
 * viu o xestor. Se mentres tanto cambiou algún día (409 `booking.series-conflicts-changed`), a vista
 * previa nova vén no erro e pasa a ser a que se ve. Cancelar a reserva ou a sesión dun choque abre o
 * diálogo de sempre (un interruptor por canle) e volve pedir a vista previa. Un por páxina.
 */
@Injectable()
export class SeasonPlanner {
  private readonly store = inject(SeasonsStore);
  private readonly bookings = inject(ManageBookingsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly injector = inject(Injector);
  private readonly municipality = inject(CurrentMunicipality);

  readonly today = madridToday();
  readonly draft = signal<SeasonDraft>(EMPTY_DRAFT);
  /** O uso escollido vai por partes (dío o formulario). */
  readonly partRequired = signal(false);
  /** Intentou crear: os erros vense. */
  readonly submitted = signal(false);
  readonly preview = signal<SeriesPreviewResponse | null>(null);
  readonly previewState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly problem = signal<ApiProblem | null>(null);
  readonly saving = signal(false);

  private readonly slug = computed(() => this.municipality.slug() ?? '');
  readonly errors = computed<DraftErrors>(() =>
    draftErrors(this.draft(), { organization: true, part: this.partRequired(), today: this.today }),
  );
  readonly visibleErrors = computed<DraftErrors>(() => (this.submitted() ? this.errors() : {}));
  /** O horario xa completo e sen erros: o que se manda á vista previa. */
  readonly schedule = computed(() => (Object.keys(this.errors()).length ? null : scheduleOf(this.draft())), {
    equal: (a, b) => JSON.stringify(a) === JSON.stringify(b),
  });
  /** As sesións que se crearían (para «Crear N sesións»). */
  readonly sessions = computed(() => (this.previewState() === 'ready' ? (this.preview()?.sessions ?? 0) : null));

  private request = 0;

  constructor() {
    effect((onCleanup) => {
      const schedule = this.schedule();
      if (!schedule) {
        untracked(() => {
          this.request++;
          this.preview.set(null);
          this.previewState.set('idle');
        });
        return;
      }
      const timer = setTimeout(() => void this.refresh(), PREVIEW_DELAY);
      onCleanup(() => clearTimeout(timer));
    });
    inject(DestroyRef).onDestroy(() => this.request++);
  }

  /** Pide de novo a vista previa do borrador. */
  async refresh(): Promise<void> {
    const schedule = this.schedule();
    if (!schedule) return;
    const request = ++this.request;
    this.previewState.set('loading');
    try {
      const preview = await this.store.preview(this.slug(), schedule);
      if (request !== this.request) return;
      this.preview.set(preview);
      this.previewState.set('ready');
    } catch (error) {
      if (request !== this.request) return;
      this.problem.set(asProblem(error));
      this.previewState.set('error');
    }
  }

  /**
   * Crea (ou, con `requestId`, aproba creando) co que se ve. Con erros, `null` e vense; se os
   * choques cambiaron, a vista previa nova e o aviso.
   */
  async create(requestId: string | null = null): Promise<SeriesResponse | null> {
    this.submitted.set(true);
    this.problem.set(null);
    const schedule = this.schedule();
    const preview = this.preview();
    if (!schedule || !preview || this.saving() || this.previewState() !== 'ready') return null;
    this.saving.set(true);
    try {
      return await this.store.create(this.slug(), { ...schedule, skip: conflictDates(preview) }, requestId);
    } catch (error) {
      const problem = asProblem(error);
      const fresh = problem.extensions['preview'] as SeriesPreviewResponse | undefined;
      if (problem.code === 'booking.series-conflicts-changed' && fresh) {
        this.request++;
        this.preview.set(fresh);
        this.previewState.set('ready');
      }
      this.problem.set(problem);
      return null;
    } finally {
      this.saving.set(false);
    }
  }

  /** «Cancelar a reserva» ou «Cancelar a sesión» dun choque: o diálogo de sempre e outra vista previa. */
  async cancelConflict(conflict: ScheduleConflict): Promise<void> {
    if (!conflict.bookingId) return;
    this.problem.set(null);
    try {
      const booking = await this.store.booking(this.slug(), conflict.bookingId);
      const data: CancelBookingData = {
        booking,
        cancel: (channels) => this.bookings.cancel(this.slug(), booking, channels),
      };
      const ref = this.dialogs.open<boolean | { failed: unknown }, CancelBookingData, CancelBookingDialogComponent>(
        CancelBookingDialogComponent,
        { data, injector: this.injector, autoFocus: '[data-cancel-keep]' },
      );
      const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
      if (result === true) {
        this.toasts.show(
          conflict.kind === 'SESSION' ? 'booking.seasons.sessionCancelled' : 'booking.manage.detail.done.cancelled',
        );
        await this.refresh();
      } else if (typeof result === 'object') {
        this.problem.set(asProblem(result.failed));
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
