// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { bookingChip } from '../mine/my-bookings';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import { groupByStart } from './bookings-calendar';

/**
 * O día no móbil (maqueta 27): no canto da grella, unha lista por hora de inicio. Cada reserva é
 * un botón (persoa, instalación e uso, etiqueta do estado) que abre a tarxeta da reserva.
 */
@Component({
  selector: 'furar-calendar-day-list',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-card f-bcal__list' },
  template: `
    @for (group of groups(); track group.time) {
      <section class="f-bcal__list-group">
        <h2>{{ group.time }}</h2>
        <ul class="f-manage__rows">
          @for (b of group.items; track b.id) {
            <li>
              <button
                type="button"
                class="f-mrow"
                data-cal-block
                [attr.data-booking]="b.id"
                [attr.aria-pressed]="b.id === selectedId()"
                (click)="picked.emit(b)"
              >
                <span class="f-mrow__main">
                  <b class="f-mrow__name">{{ b.holderName ?? ('booking.manage.anonymized' | transloco) }}</b
                  >{{ ' ' }}
                  <span class="f-mrow__meta">{{ meta(b) }}</span>
                </span>
                {{ ' ' }}
                @if (chip(b); as c) {
                  <span class="f-mrow__end">
                    <furar-tag [tone]="c.tone" [look]="c.look">{{
                      'booking.mine.status.' + c.key | transloco
                    }}</furar-tag>
                  </span>
                }
              </button>
            </li>
          }
        </ul>
      </section>
    } @empty {
      <p class="f-manage__empty">{{ 'booking.calendar.empty' | transloco }}</p>
    }
  `,
})
export class CalendarDayListComponent {
  readonly bookings = input.required<readonly ManagedBookingResponse[]>();
  readonly selectedId = input<string | null>(null);
  readonly picked = output<ManagedBookingResponse>();

  protected readonly groups = computed(() => groupByStart(this.bookings()));

  protected meta(b: ManagedBookingResponse): string {
    return [b.facilityName, b.useName, b.partName].filter(Boolean).join(' · ');
  }

  protected chip(b: ManagedBookingResponse) {
    return bookingChip(b);
  }
}
