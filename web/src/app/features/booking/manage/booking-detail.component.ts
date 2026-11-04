// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, Injector, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import { bookingFieldRows } from '../booking-fields';
import { bookingChip } from '../mine/my-bookings';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import type { CancelBookingData } from './cancel-booking-dialog.component';
import { CancelBookingDialogComponent } from './cancel-booking-dialog.component';
import type { ManagedBookingDetailResponse, PaymentStatus } from './manage-bookings';
import { paymentActions } from './manage-bookings';
import type { FacilitySummaryResponse } from './manage-bookings.store';
import { ManageBookingsStore } from './manage-bookings.store';

/** Texto e icona de cada cobro que se pode marcar (o que adianta e o que desfai, P13). */
const PAYMENT_ACTION: Record<PaymentStatus, { key: string; icon: 'payments' | 'undo' }> = {
  PAID: { key: 'markPaid', icon: 'payments' },
  REFUNDED: { key: 'markRefunded', icon: 'undo' },
  PENDING: { key: 'undoPaid', icon: 'undo' },
  TO_REFUND: { key: 'undoRefunded', icon: 'undo' },
  NONE: { key: 'markPaid', icon: 'payments' },
};

/**
 * Detalle dunha reserva do concello (maqueta 26), o mesmo deseño ca a tarxeta da reserva (maquetas
 * 11 e 13): a instalación coa súa miniatura e enderezo, os valores (`furar-value-list`: persoa,
 * uso, parte, día, hora e código), o correo de quen reserva, o total coa etiqueta do pago e as
 * accións: «Marcar como pagada» ou «Marcar como devolta» e o seu desfacer (P13), e «Cancelar
 * reserva» (só a xestión; pide as canles, P12). Cada acción vai coa versión que se ve; ben,
 * anúnciase (`role="status"`); se a reserva cambiou noutro sitio (412), aviso con «Recargar»;
 * calquera outro erro (unha transición que xa non vale, 422) en aviso. Úsao a páxina de reservas
 * (tarxeta ao lado ou diálogo no móbil) e o calendario (D2-16), co seu `ManageBookingsStore`.
 */
@Component({
  selector: 'furar-managed-booking-detail',
  imports: [
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-mdetail', '[attr.data-booking-detail]': 'store.detail()?.id ?? null' },
  template: `
    @let t = 'booking.manage.detail.';
    @if (closable()) {
      <button
        type="button"
        class="f-mdetail__close"
        data-detail-close
        [attr.aria-label]="t + 'close' | transloco"
        (click)="closed.emit()"
      >
        <furar-icon name="close" />
      </button>
    }
    @let current = store.detail();
    @if (current?.detail; as d) {
      <div class="f-reserve__facility" data-detail-facility>
        @if (facility()?.cover; as cover) {
          <img [src]="cover.thumb" [width]="cover.width" [height]="cover.height" alt="" />
        }
        <div>
          <b>{{ d.facilityName }}</b>
          @if (d.facilityAddress) {
            {{ ' ' }}<span>{{ d.facilityAddress }}</span>
          }
        </div>
      </div>
      <furar-value-list [items]="rows()" />
      @if (d.holderEmail; as email) {
        <p class="f-mdetail__contact">
          <a [href]="'mailto:' + email" data-detail-email>
            <furar-icon name="email" />
            <span>{{ email }}</span>
          </a>
        </p>
      }
      @if (amount() || chip()) {
        <p class="f-mdetail__total" data-detail-total>
          <span>{{ t + 'total' | transloco }}</span
          >{{ ' ' }}
          <span class="f-mdetail__money">
            @if (amount(); as value) {
              <b>{{ value }}</b
              >{{ ' ' }}
            }
            @if (chip(); as c) {
              <furar-tag [tone]="c.tone" [look]="c.look" [attr.data-chip]="c.key">{{
                'booking.mine.status.' + c.key | transloco
              }}</furar-tag>
            }
          </span>
        </p>
      }
      <div role="status" class="f-mdetail__live" data-detail-status>
        @if (stale()) {
          <furar-callout tone="warn" data-detail-stale>
            <p>{{ t + 'stale' | transloco }}</p>
            <button type="button" furarButton size="sm" class="mt-unit" data-detail-reload (click)="reload()">
              {{ t + 'reload' | transloco }}
            </button>
          </furar-callout>
        }
      </div>
      @if (problem(); as p) {
        <furar-callout tone="danger" live="alert" data-detail-error>{{ p.translated }}</furar-callout>
      }
      @if (actions().length || canCancel()) {
        <div class="f-mdetail__acts">
          @for (action of actions(); track action.target) {
            <button
              type="button"
              [furarButton]="action.undo ? 'quiet' : 'primary'"
              [attr.data-payment]="action.target"
              [busy]="busy() === action.target"
              (click)="setPayment(d, action.target)"
            >
              <furar-icon [name]="paymentIcon(action.target)" />
              {{ t + paymentKey(action.target) | transloco }}
            </button>
          }
          @if (canCancel()) {
            <button type="button" furarButton="danger" data-detail-cancel (click)="cancel(d)">
              <furar-icon name="event-busy" />
              {{ t + 'cancel' | transloco }}
            </button>
          }
        </div>
      }
    } @else if (current?.state === 'error') {
      <furar-status-state mode="error" [heading]="t + 'loadError' | transloco">
        <button type="button" furarButton (click)="reload()">{{ t + 'retry' | transloco }}</button>
      </furar-status-state>
    } @else {
      <furar-status-state mode="skeleton" shape="card" [rows]="1" [description]="t + 'loading' | transloco" />
    }
  `,
})
export class ManagedBookingDetailComponent {
  /** A instalación da reserva (miniatura); sen ela, só o nome e o enderezo. */
  readonly facility = input<FacilitySummaryResponse | null>(null);
  /** Con botón «Pechar» (a tarxeta ao lado; no diálogo xa o pon `furar-dialog`). */
  readonly closable = input(false);
  readonly closed = output();

  protected readonly store = inject(ManageBookingsStore);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  private readonly toasts = inject(ToastService);
  protected readonly stale = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** O cobro que se está a marcar (ou `null`). */
  protected readonly busy = signal<PaymentStatus | null>(null);

  private readonly slug = computed(() => this.municipality.slug() ?? '');
  private readonly detail = computed(() => this.store.detail()?.detail ?? null);

  protected readonly rows = computed<ValueListItem[]>(() => {
    this.langChanges();
    const d = this.detail();
    if (!d) return [];
    const t = (key: string) => this.transloco.translate(`booking.manage.detail.${key}`);
    const start = inMadrid(d.start);
    const end = inMadrid(d.end);
    const rows: ValueListItem[] = [
      {
        id: 'person',
        label: t('person'),
        value: d.holderName ?? this.transloco.translate('booking.manage.anonymized'),
      },
      { id: 'use', label: t('use'), value: d.useName },
    ];
    if (d.partName) rows.push({ id: 'part', label: t('part'), value: d.partName });
    rows.push(
      { id: 'day', label: t('day'), value: formatDay(start.date, this.language.lang(), 'long') },
      { id: 'time', label: t('time'), value: `${start.time}–${end.time}` },
      { id: 'code', label: t('code'), value: d.code },
    );
    // Os campos propios da reserva (maqueta 57): o persoal ve todos os que teñen valor.
    const translate = (key: string) => this.transloco.translate(key);
    rows.push(...bookingFieldRows(this.store.fields(), d.customFields, this.language.lang(), translate));
    return rows;
  });
  protected readonly amount = computed(() => {
    const price = this.detail()?.price;
    return price?.kind === 'PAID' && typeof price.amountCents === 'number' ? formatCents(price.amountCents) : null;
  });
  protected readonly chip = computed(() => {
    const d = this.detail();
    return d ? bookingChip(d) : null;
  });
  protected readonly actions = computed(() => {
    const d = this.detail();
    return d ? paymentActions(d) : [];
  });
  /** «Cancelar reserva»: só a xestión (STAFF non cancela) e se aínda está confirmada. */
  protected readonly canCancel = computed(() => this.staff.canManage() && (this.detail()?.cancellable ?? false));

  protected paymentKey(target: PaymentStatus): string {
    return PAYMENT_ACTION[target].key;
  }

  protected paymentIcon(target: PaymentStatus): 'payments' | 'undo' {
    return PAYMENT_ACTION[target].icon;
  }

  protected async setPayment(detail: ManagedBookingDetailResponse, target: PaymentStatus): Promise<void> {
    this.reset();
    this.busy.set(target);
    try {
      await this.store.setPayment(this.slug(), detail, target);
      this.toasts.show(`booking.manage.detail.done.${PAYMENT_ACTION[target].key}`);
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy.set(null);
    }
  }

  /** Pregunta as canles (P12) e cancela coa capa ocupada; os erros quedan nesta tarxeta. */
  protected async cancel(detail: ManagedBookingDetailResponse): Promise<void> {
    this.reset();
    const data: CancelBookingData = { booking: detail };
    const ref = this.dialogs.open<boolean | { failed: unknown }, CancelBookingData, CancelBookingDialogComponent>(
      CancelBookingDialogComponent,
      { data, injector: this.injector, autoFocus: '[data-cancel-keep]' },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (result === true) {
      this.toasts.show('booking.manage.detail.done.cancelled');
    } else if (typeof result === 'object') {
      this.fail(result.failed);
    }
  }

  protected reload(): void {
    this.reset();
    const id = this.store.detail()?.id;
    if (id) void this.store.open(this.slug(), id);
  }

  private reset(): void {
    this.stale.set(false);
    this.problem.set(null);
  }

  private fail(error: unknown): void {
    const problem = asProblem(error);
    if (problem.status === 412) this.stale.set(true);
    else this.problem.set(problem);
  }
}
