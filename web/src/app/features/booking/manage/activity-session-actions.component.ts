// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ActionItemComponent, ActionListDirective } from '../../../shared/ui/action-list/action-list.component';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { formatDay } from '../reserve/reserve';
import type { ManagedBookingDetailResponse } from './manage-bookings';
import { ManageBookingsStore } from './manage-bookings.store';

/** As filas do detalle dunha sesión dunha actividade: a actividade, o uso (e a parte), o día e a hora. */
export function activitySessionRows(
  d: ManagedBookingDetailResponse,
  lang: string,
  t: (key: string) => string,
): ValueListItem[] {
  const start = inMadrid(d.start);
  const end = inMadrid(d.end);
  return [
    { id: 'activity', label: t('activity'), value: d.holderName ?? '' },
    { id: 'use', label: t('use'), value: [d.useName, d.partName].filter(Boolean).join(' · ') },
    { id: 'day', label: t('day'), value: formatDay(start.date, lang, 'long') },
    { id: 'time', label: t('time'), value: `${start.time}–${end.time}` },
  ];
}

/**
 * As accións dunha sesión dunha actividade (ADR-0041) no detalle dunha reserva do calendario, no
 * canto das dunha reserva normal: «Pasar lista» (Xestión e Recepción), «Ver a actividade» e
 * «Cancelar a sesión» (só Xestión, mentres está por vir; avisa por correo ás persoas apuntadas).
 */
@Component({
  selector: 'furar-activity-session-actions',
  imports: [ActionItemComponent, ActionListDirective, CalloutComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let d = detail();
    @let a = d.activity;
    @if (problem(); as p) {
      <furar-callout tone="danger" live="alert" data-detail-error>{{ p.translated }}</furar-callout>
    }
    @if (a) {
      <ul furarActionList data-detail-activity [attr.aria-label]="'booking.mine.detail.actions' | transloco">
        @if (canAttend() && a.sessionId) {
          <li>
            <furar-action-item icon="group">
              <a
                data-detail-attend
                [routerLink]="['/', slug(), 'xestion', 'eventos', a.eventId, 'sesions', a.sessionId]"
              >
                {{ 'event.sessions.attend' | transloco }}
              </a>
            </furar-action-item>
          </li>
        }
        <li>
          <furar-action-item icon="local-activity">
            <a
              data-detail-activity-open
              [routerLink]="['/', slug(), 'xestion', 'eventos']"
              [queryParams]="{ ver: a.eventId }"
            >
              {{ 'booking.manage.detail.openActivity' | transloco }}
            </a>
          </furar-action-item>
        </li>
        @if (canCancel()) {
          <li>
            <furar-action-item icon="event-busy" tone="danger">
              <button type="button" data-detail-session-cancel (click)="cancel()">
                {{ 'event.sessions.cancel' | transloco }}
              </button>
            </furar-action-item>
          </li>
        }
      </ul>
    }
  `,
})
export class ActivitySessionActionsComponent {
  private readonly store = inject(ManageBookingsStore);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly detail = input.required<ManagedBookingDetailResponse>();

  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  /** `event.attendance`: Xestión e Recepción. */
  protected readonly canAttend = computed(() => this.staff.canViewBookings());
  /** `event.manage`: só Xestión, mentres a sesión está confirmada e por vir. */
  protected readonly canCancel = computed(
    () => this.staff.canManage() && !!this.detail().activity?.sessionId && this.detail().cancellable,
  );

  protected async cancel(): Promise<void> {
    const detail = this.detail();
    const eventId = detail.activity?.eventId;
    const sessionId = detail.activity?.sessionId;
    if (!eventId || !sessionId) return;
    this.problem.set(null);
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    try {
      const done = await this.dialogs.confirm({
        heading: t('event.sessions.cancelConfirm.title'),
        message: t('booking.manage.detail.sessionCancelText', {
          day: formatDay(inMadrid(detail.start).date, this.language.lang(), 'long'),
        }),
        confirmLabel: t('event.sessions.cancel'),
        tone: 'danger',
        action: () => this.store.cancelActivitySession(this.slug(), eventId, sessionId, detail.id),
      });
      if (done) this.toasts.show('event.sessions.cancelledToast');
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
