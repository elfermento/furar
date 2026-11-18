// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { namedParticipants } from '../../../shared/dependents/participants';
import { searchingCountsText } from '../../../shared/open-matches/open-match-format';
import { ActionItemComponent, ActionListDirective } from '../../../shared/ui/action-list/action-list.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { IconName } from '../../../shared/ui/icon/icons';
import { QrComponent } from '../../../shared/ui/qr/qr.component';
import { StarsComponent } from '../../../shared/ui/stars/stars.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import type { BookingWaitResponse } from '../waits/waits.store';
import { bookingWhen } from './booking-card.component';
import * as mine from './my-bookings';

/** Unha acción máis no detalle, doutra función (vai antes da destrutiva). */
export interface MyBookingDetailAction {
  /** Clave estable: volve no resultado (`{ kind: 'extra', id }`) e vai en `data-detail-action`. */
  readonly id: string;
  readonly icon: IconName;
  /** Rótulo xa traducido. */
  readonly label: string;
}

/** O que abre o detalle: a reserva ou a espera, o concello e o teléfono do servizo (fóra de prazo). */
export interface MyBookingDetailData {
  readonly booking?: MyBookingResponse | null;
  readonly wait?: BookingWaitResponse | null;
  readonly slug: string;
  readonly phone: string | null;
  readonly extraActions?: readonly MyBookingDetailAction[];
  /** Reserva dunha entidade (ADR-0039, punto 10): sen «Cambiar»; só se cancela. */
  readonly organization?: boolean;
}

/** A acción que escolleu; a páxina faina co detalle xa pechado. Pechar sen escoller: `undefined`. */
export type MyBookingDetailResult =
  { readonly kind: 'cancel' | 'review' | 'report' | 'leave' } | { readonly kind: 'extra'; readonly id: string };

interface Row {
  readonly id: string;
  readonly label: string;
  readonly value: string;
}

/**
 * Detalle dunha reserva de «As miñas reservas»: `furar-dialog` (folla no móbil) co
 * nome e o día e a hora na cabeceira. Nas próximas, o QR co código arriba; despois os datos (para
 * quen, uso, parte e, á dereita, o importe e debaixo o estado do pago e a asistencia; a valoración
 * se a ten). Fóra de prazo, o aviso co teléfono. «Valorar» como botón principal se se pode, e as
 * accións en lista, unha por fila: Cambiar, Reservar de novo, Avisar dun problema, as doutras
 * funcións (`extraActions`) e, ao final, a destrutiva (Cancelar ou Deixar de esperar). Pecha con
 * «Pechar», Esc ou picar fóra, sen mover a páxina.
 */
@Component({
  selector: 'furar-my-booking-detail',
  imports: [
    ActionItemComponent,
    ActionListDirective,
    ButtonDirective,
    DialogComponent,
    IconComponent,
    QrComponent,
    RouterLink,
    StarsComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.mine.';
    <furar-dialog size="sm" [heading]="name" [subheading]="when">
      <div class="f-mydetail" [attr.data-my-booking-detail]="id">
        @if (booking && !past) {
          <div class="f-mybooking-qr" data-detail-qr>
            <furar-qr [size]="booking.qr.size" [path]="booking.qr.path" [code]="booking.code" />
            <b class="f-mybooking-qr__code" data-qr-code>{{ booking.code }}</b>
            <p>{{ t + 'qr.hint' | transloco }}</p>
          </div>
        }
        <div class="f-value-list" data-variant="lined">
          <dl class="f-value-list__list">
            @for (row of rows(); track row.id) {
              <div class="f-value-list__row" [attr.data-value]="row.id">
                <dt>{{ row.label }}</dt>
                <dd>{{ row.value }}</dd>
              </div>
            }
            @if (hasState) {
              <div class="f-value-list__row" data-value="state">
                <dt>{{ t + (amount ? 'detail.amount' : 'detail.status') | transloco }}</dt>
                <dd class="f-mydetail__state">
                  @if (amount) {
                    <b data-detail-amount>{{ amount }}</b>
                  }
                  @if (wait) {
                    <furar-tag tone="muted" look="solid" data-chip="waiting">{{
                      t + 'wait.chip' | transloco
                    }}</furar-tag>
                  }
                  @if (chip; as c) {
                    <furar-tag [tone]="c.tone" [attr.data-chip]="c.key">{{
                      t + 'status.' + c.key | transloco
                    }}</furar-tag>
                  }
                  @if (attendance; as a) {
                    <furar-tag [tone]="a.tone" [attr.data-attendance]="a.key">{{
                      'booking.attendance.' + a.key | transloco
                    }}</furar-tag>
                  }
                </dd>
              </div>
            }
            @if (searching; as counts) {
              <div class="f-value-list__row" data-value="match">
                <dt>{{ 'openMatch.manage.line' | transloco }}</dt>
                <dd class="f-mydetail__match">
                  <furar-tag tone="info" data-chip="searching">{{ 'openMatch.chip' | transloco }}</furar-tag>
                  <span>{{ counts }}</span>
                </dd>
              </div>
            }
            @if (booking?.reviewStars; as stars) {
              <div class="f-value-list__row" data-value="review">
                <dt>{{ t + 'detail.review' | transloco }}</dt>
                <dd><furar-stars size="md" data-booking-reviewed [value]="stars" /></dd>
              </div>
            }
          </dl>
        </div>
        @if (late) {
          <p class="f-mybooking__late" data-booking-late>
            <furar-icon name="info" />
            <span>
              {{ t + 'late.text' | transloco }}
              @if (data.phone; as tel) {
                {{ t + 'late.call' | transloco }}
                <a [href]="'tel:' + telHref">{{ tel }}</a>
              }
            </span>
          </p>
        }
        @if (booking?.canReview) {
          <button type="button" furarButton="primary" block data-booking-review (click)="pick('review')">
            <furar-icon name="star" />
            {{ 'reviews.action' | transloco }}
          </button>
        }
        @if (hasActions) {
          <ul furarActionList [attr.aria-label]="t + 'detail.actions' | transloco">
            @if (booking; as b) {
              @if (changeable && !data.organization) {
                <li>
                  <furar-action-item icon="edit-calendar">
                    <a
                      data-booking-change
                      [routerLink]="reserveLink"
                      [queryParams]="{ cambiar: b.id }"
                      (click)="ref.close()"
                      >{{ t + 'actions.change' | transloco }}</a
                    >
                  </furar-action-item>
                </li>
              }
              @if (past) {
                <li>
                  <furar-action-item icon="event-repeat">
                    <a
                      data-booking-again
                      [routerLink]="reserveLink"
                      [queryParams]="againParams"
                      (click)="ref.close()"
                      >{{ t + 'actions.again' | transloco }}</a
                    >
                  </furar-action-item>
                </li>
              }
              @if (reportable) {
                <li>
                  <furar-action-item icon="warn">
                    <button type="button" data-report-open (click)="pick('report')">
                      {{ 'incident.report.open' | transloco }}
                    </button>
                  </furar-action-item>
                </li>
              }
            }
            @for (action of extraActions; track action.id) {
              <li>
                <furar-action-item [icon]="action.icon">
                  <button
                    type="button"
                    [attr.data-detail-action]="action.id"
                    (click)="ref.close({ kind: 'extra', id: action.id })"
                  >
                    {{ action.label }}
                  </button>
                </furar-action-item>
              </li>
            }
            @if (changeable) {
              <li>
                <furar-action-item icon="event-busy" tone="danger">
                  <button type="button" data-booking-cancel (click)="pick('cancel')">
                    {{ t + 'actions.cancel' | transloco }}
                  </button>
                </furar-action-item>
              </li>
            }
            @if (wait) {
              <li>
                <furar-action-item icon="event-busy" tone="danger">
                  <button type="button" data-wait-leave (click)="pick('leave')">
                    {{ t + 'wait.remove' | transloco }}
                  </button>
                </furar-action-item>
              </li>
            }
          </ul>
        }
      </div>
    </furar-dialog>
  `,
})
export class MyBookingDetailComponent {
  protected readonly data = inject<MyBookingDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MyBookingDetailResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = inject(LanguageService).lang();

  protected readonly booking = this.data.booking ?? null;
  protected readonly wait = this.booking ? null : (this.data.wait ?? null);
  private readonly shown = (this.booking ?? this.wait) as MyBookingResponse | BookingWaitResponse;
  protected readonly id = this.shown.id;
  protected readonly name = this.shown.facilityName;
  protected readonly when = bookingWhen(this.shown, this.lang);
  protected readonly past = !!this.booking && mine.isPast(this.booking);
  protected readonly changeable = !!this.booking && mine.canChange(this.booking);
  protected readonly late = !!this.booking && mine.pastDeadline(this.booking);
  /** Avisar dun problema (ADR-0036): pasada e sen cancelar. */
  protected readonly reportable = !!this.booking && this.booking.done && this.booking.status === 'CONFIRMED';
  protected readonly amount = mine.bookingAmount(this.booking);
  protected readonly chip = this.booking ? mine.bookingChip(this.booking) : null;
  protected readonly attendance = this.booking ? mine.attendanceChip(this.booking) : null;
  private readonly tr = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
  protected readonly searching = this.past ? null : searchingCountsText(this.booking?.openMatch, this.tr);
  protected readonly hasState = !!(this.amount || this.chip || this.attendance || this.wait);
  protected readonly telHref = (this.data.phone ?? '').replace(/[^\d+]/g, '');
  protected readonly reserveLink = mine.reserveLink(this.data.slug, this.booking);
  protected readonly againParams = mine.againParams(this.booking);
  protected readonly extraActions = this.data.extraActions ?? [];
  protected readonly hasActions = this.changeable || this.past || !!this.wait || this.extraActions.length > 0;

  /** Para quen, o uso (coas prazas) e a parte; o resto xa está na cabeceira ou na tarxeta. */
  protected readonly rows = computed<Row[]>(() => {
    const label = (key: string) => this.tr(`booking.manage.detail.${key}`);
    const rows: Row[] = [];
    const booking = this.booking;
    const who = booking ? namedParticipants(booking.participants) : [];
    if (who.length) rows.push({ id: 'who', label: label('who'), value: who.join(' · ') });
    const seats = booking && booking.seats > 1 ? this.tr('booking.who.seats', { count: booking.seats }) : null;
    rows.push({ id: 'use', label: label('use'), value: [this.shown.useName, seats].filter(Boolean).join(' · ') });
    if (booking?.partName) rows.push({ id: 'part', label: label('part'), value: booking.partName });
    return rows;
  });

  protected pick(kind: 'cancel' | 'review' | 'report' | 'leave'): void {
    this.ref.close({ kind });
  }
}
