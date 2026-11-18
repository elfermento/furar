// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { StatusCardLine } from '../../../shared/ui/status-card/status-card.component';
import { StatusCardComponent } from '../../../shared/ui/status-card/status-card.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { agoText } from '../manage/manage-bookings';
import { BookingCancel } from '../mine/booking-cancel';
import { BookingCardComponent } from '../mine/booking-card.component';
import type { MyBookingDetailData, MyBookingDetailResult } from '../mine/my-booking-detail.component';
import { MyBookingDetailComponent } from '../mine/my-booking-detail.component';
import type { BookingScope } from '../mine/my-bookings';
import type { MyBookingResponse, MyOrganizationResponse, MySeriesRequestResponse } from './my-organizations.store';
import { OrganizationBookingsStore } from './my-organizations.store';
import type { MySeasonRequestResult } from './my-season-request-detail.component';
import { MySeasonRequestDetailComponent } from './my-season-request-detail.component';
import type { RequestSeasonData } from './request-season-dialog.component';
import { RequestSeasonDialogComponent } from './request-season-dialog.component';
import { scheduleParts } from './seasons';

/** Unha tarxeta de solicitude: a solicitude e o que pinta. */
interface RequestCard {
  readonly request: MySeriesRequestResponse;
  readonly lines: readonly StatusCardLine[];
  readonly ago: string;
}

/**
 * A pestana dunha entidade en «As miñas reservas» (ADR-0039, punto 10): Próximas e
 * Historial coma «Eu», con «Solicitar tempada» á dereita. En «Próximas», primeiro as solicitudes
 * pendentes ou non aprobadas (tarxeta coa instalación, «· parte · uso», os días, as horas e as
 * datas, o chip e cando se enviou) e despois as sesións e as reservas da entidade, coa mesma
 * tarxeta ca as da persoa («· Tempada» nas sesións). O detalle é o de sempre sen «Cambiar»;
 * cancelar unha sesión é sen custo ata que empeza. Para reservar como a entidade, a páxina de
 * reservar de calquera instalación («Reservar como»).
 */
@Component({
  selector: 'furar-organization-bookings',
  imports: [
    BookingCardComponent,
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    StatusCardComponent,
    StatusStateComponent,
    TabDirective,
    TabsComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [OrganizationBookingsStore, BookingCancel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './organization-bookings.component.html',
})
export class OrganizationBookingsComponent {
  protected readonly store = inject(OrganizationBookingsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly cancelling = inject(BookingCancel);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  readonly slug = input.required<string>();
  readonly organization = input.required<MyOrganizationResponse>();

  protected readonly scope = signal<BookingScope>('UPCOMING');
  protected readonly list = computed(() => this.store.lists[this.scope()]());
  protected readonly problem = signal<ApiProblem | null>(null);
  /** As pendentes e as non aprobadas (as aprobadas xa son sesións). */
  protected readonly requestCards = computed<RequestCard[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    return this.store
      .requests()
      .filter((request) => request.status !== 'APPROVED')
      .map((request) => ({
        request,
        lines: [
          {
            id: 'place',
            soft: true,
            parts: [request.partName, request.useName].filter((part): part is string => !!part),
          },
          { id: 'when', icon: 'event-repeat', parts: scheduleParts(request, lang, this.t) },
        ],
        ago: this.t('booking.seasons.request.sent', { ago: agoText(request.createdAt, lang) }),
      }));
  });

  constructor() {
    effect(() => {
      const scope = this.scope();
      const slug = this.slug();
      const { id } = this.organization();
      untracked(() => void this.store.load(slug, id, scope));
    });
    effect(() => {
      const slug = this.slug();
      const { id } = this.organization();
      untracked(() => void this.store.loadRequests(slug, id));
    });
  }

  protected retry(): void {
    void this.store.load(this.slug(), this.organization().id, this.scope(), true);
  }

  protected more(): void {
    void this.store.loadMore(this.slug(), this.organization().id, this.scope());
  }

  /** «Solicitar tempada»: o formulario; enviada, a súa tarxeta arriba e un aviso. */
  protected async ask(): Promise<void> {
    const organization = this.organization();
    const data: RequestSeasonData = {
      slug: this.slug(),
      organizationName: organization.name,
      send: (body) => this.store.request(this.slug(), organization.id, body),
    };
    const ref = this.dialogs.open<MySeriesRequestResponse, RequestSeasonData, RequestSeasonDialogComponent>(
      RequestSeasonDialogComponent,
      { data, injector: this.injector },
    );
    if (await firstValueFrom(ref.closed, { defaultValue: undefined })) {
      this.scope.set('UPCOMING');
      this.toasts.show('booking.seasons.ask.sent');
    }
  }

  protected async openRequest(request: MySeriesRequestResponse): Promise<void> {
    const ref = this.dialogs.open<MySeasonRequestResult, MySeriesRequestResponse, MySeasonRequestDetailComponent>(
      MySeasonRequestDetailComponent,
      { data: request, sheet: true, injector: this.injector },
    );
    if ((await firstValueFrom(ref.closed, { defaultValue: undefined })) !== 'withdraw') return;
    this.problem.set(null);
    try {
      await this.store.withdraw(this.slug(), this.organization().id, request);
      this.toasts.show('booking.seasons.request.withdrawn');
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  /** O detalle de sempre (sen «Cambiar») e, se escolle cancelar, cancelar. */
  protected async open(booking: MyBookingResponse): Promise<void> {
    const data: MyBookingDetailData = { booking, slug: this.slug(), phone: null, organization: true };
    const ref = this.dialogs.open<MyBookingDetailResult, MyBookingDetailData, MyBookingDetailComponent>(
      MyBookingDetailComponent,
      { data, sheet: true, injector: this.injector },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (result?.kind === 'cancel') await this.cancel(booking);
  }

  /** Pregunta co diálogo de «Eu» (`BookingCancel`; nunha sesión, «Cancelar a sesión?») e cancela. */
  private async cancel(booking: MyBookingResponse): Promise<void> {
    this.problem.set(null);
    try {
      await this.cancelling.confirm(this.slug(), booking, () =>
        this.store.cancel(this.slug(), this.organization().id, booking),
      );
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
