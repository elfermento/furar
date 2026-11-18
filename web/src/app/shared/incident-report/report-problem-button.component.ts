// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../ui/button/button.directive';
import { inMadrid } from '../ui/calendar/madrid-time';
import { DialogService } from '../ui/dialog/dialog.service';
import { IconComponent } from '../ui/icon/icon.component';
import { openIncidentReport } from './open-incident-report';

/** O que precisa da reserva: a de «As miñas reservas». */
export interface ReportableBooking {
  readonly id: string;
  readonly facilityId: string;
  readonly facilityName: string;
  readonly start: string;
  readonly end: string;
  readonly done: boolean;
  readonly status: string;
}

/** «11/11, 9:30–11:00», en Europe/Madrid. */
export function bookingSlot(booking: Pick<ReportableBooking, 'start' | 'end'>): string {
  const start = inMadrid(booking.start);
  const [, month, day] = start.date.split('-');
  return `${day}/${month}, ${start.time}–${inMadrid(booking.end).time}`;
}

/**
 * «Avisar dun problema» nunha reserva do historial: só nas pasadas sen cancelar, co
 * estilo de «Cambiar» (`quiet`). Abre o aviso coa instalación e a reserva xa postas.
 */
@Component({
  selector: 'furar-report-problem-button',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (shown()) {
      <button
        type="button"
        furarButton="quiet"
        data-report-open
        [attr.aria-describedby]="describedBy()"
        (click)="open()"
      >
        <furar-icon name="warn" />
        {{ 'incident.report.open' | transloco }}
      </button>
    }
  `,
})
export class ReportProblemButtonComponent {
  readonly slug = input.required<string>();
  readonly booking = input.required<ReportableBooking>();
  /** O título da tarxeta, para os botóns repetidos. */
  readonly describedBy = input<string | null>(null);

  private readonly dialogs = inject(DialogService);
  protected readonly shown = computed(() => this.booking().done && this.booking().status === 'CONFIRMED');

  protected open(): void {
    const booking = this.booking();
    void openIncidentReport(this.dialogs, {
      slug: this.slug(),
      mode: 'resident',
      facilityId: booking.facilityId,
      bookingId: booking.id,
      subheading: `${booking.facilityName} · ${bookingSlot(booking)}`,
    });
  }
}
