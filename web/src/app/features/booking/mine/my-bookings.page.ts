// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { MyEventsComponent } from '../../../shared/events/my-events.component';
import { MyOpenMatchesComponent } from '../../../shared/open-matches/my-open-matches.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { focusPageHeadingIfLost } from '../../../shared/ui/focus-page-heading';
import { registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { openIncidentReport } from '../../../shared/incident-report/open-incident-report';
import { bookingSlot } from '../../../shared/incident-report/report-problem-button.component';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import { BookingCardComponent } from './booking-card.component';
import type { MyBookingDetailData, MyBookingDetailResult } from './my-booking-detail.component';
import { MyBookingDetailComponent } from './my-booking-detail.component';
import type { ReviewDialogData } from './review-dialog.component';
import { ReviewDialogComponent } from './review-dialog.component';
import { RemindersSettingComponent } from './reminders-setting.component';
import type { BookingScope } from './my-bookings';
import { MyBookingsStore } from './my-bookings.store';
import { BookingCancel } from './booking-cancel';
import type { BookingWaitResponse } from '../waits/waits.store';
import { WaitsStore } from '../waits/waits.store';
import { HolderTabsComponent } from '../seasons/holder-tabs.component';
import type { MatchActionId } from '../open-match/booking-match';
import { matchDetailActions } from '../open-match/booking-match';
import { BookingMatchActions } from '../open-match/booking-match-actions';

/** Unha tarxeta de «Próximas»: unha reserva ou unha espera, coa data pola que se ordenan. */
interface CardEntry {
  readonly id: string;
  readonly start: string;
  readonly booking?: MyBookingResponse;
  readonly wait?: BookingWaitResponse;
}

// As iconas desta páxina que non van na carga inicial (Cambiar, o chevron, «Sen custo»).
registerSymbols(LAZY_SYMBOLS);

/**
 * «As miñas reservas» (`/:concello/reservas`): Próximas e Historial en
 * `furar-tabs`, unha tarxeta por reserva (`furar-booking-card`, enteira pulsable) e «Ver máis» se
 * hai máis páxinas. A tarxeta abre o detalle (`furar-my-booking-detail`, folla no móbil) co QR, os
 * datos e as accións; a escollida faise co detalle xa pechado. Cancelar pregunta en
 * `furar-confirm` estilo A (a reserva coa súa icona e unha liña: «Sen custo» co prazo, ou «Xa está
 * pagada» coa nota de devolución do concello, que vén da API); ben, a tarxeta cambia sen recargar
 * e anúnciase; se a reserva cambiou noutro sitio (412), aviso con «Recargar». En «Próximas» saen
 * tamén as esperas da persoa (ADR-0033), na mesma tarxeta co chip «En espera». Ao final, o interruptor
 * «Recordatorios por correo» (ADR-0035). Tamén os eventos aos que se apuntou (ADR-0038), coas súas
 * tarxetas (`furar-my-events`). Precisa sesión (`personGuard`).
 */
@Component({
  selector: 'furar-my-bookings-page',
  imports: [
    BookingCardComponent,
    ButtonDirective,
    CalloutComponent,
    HolderTabsComponent,
    MyEventsComponent,
    MyOpenMatchesComponent,
    RemindersSettingComponent,
    RouterLink,
    StatusStateComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
  ],
  providers: [MyBookingsStore, WaitsStore, BookingMatchActions, BookingCancel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-bookings.page.html',
})
export class MyBookingsPage implements OnInit {
  protected readonly store = inject(MyBookingsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly scope = signal<BookingScope>('UPCOMING');
  /** Cantos eventos próximos ten (ADR-0038): con algún, «Próximas» non está baleira. */
  protected readonly eventCount = signal(0);
  /** Cantas partidas ás que se uniu ou pediu unirse (ADR-0040): tamén enchen «Próximas». */
  protected readonly matchCount = signal(0);
  protected readonly list = computed(() => this.store.lists[this.scope()]());
  protected readonly hasMore = computed(() => this.list().items.length < this.list().total);
  private readonly waits = inject(WaitsStore);
  /**
   * As tarxetas da pestana. En «Próximas», as reservas e as esperas (ADR-0033) mesturadas por
   * data; no historial, só reservas.
   */
  protected readonly entries = computed<readonly CardEntry[]>(() => {
    const bookings = this.list().items.map<CardEntry>((booking) => ({ id: booking.id, start: booking.start, booking }));
    if (this.scope() !== 'UPCOMING') return bookings;
    const waits: CardEntry[] = this.waits.items().map((wait) => ({ id: wait.id, start: wait.start, wait }));
    return [...bookings, ...waits].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime());
  });
  protected readonly phone = computed(() => this.store.contact()?.phone ?? null);

  private readonly toasts = inject(ToastService);
  /** 412: a reserva cambiou noutro sitio; aviso con «Recargar». */
  protected readonly stale = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  constructor() {
    // Cada pestana pídese a primeira vez que se abre (e de novo se quedou para recargar).
    effect(() => {
      const scope = this.scope();
      const slug = this.slug();
      if (slug) untracked(() => void this.store.load(slug, scope));
    });
  }

  ngOnInit(): void {
    const slug = this.slug();
    if (!slug) return;
    this.store.loadMunicipalityTexts(slug);
    void this.waits.load(slug);
  }

  /**
   * Deixar de esperar: sen preguntar (pódese volver esperar); a tarxeta desaparece, anúnciase
   * (WCAG 4.1.3) e o foco, que estaba nela, vai ao título.
   */
  protected async leave(wait: BookingWaitResponse): Promise<void> {
    this.problem.set(null);
    try {
      await this.waits.leave(this.slug(), wait.id);
      this.toasts.show('booking.mine.wait.removed');
      focusPageHeadingIfLost(this.injector);
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  protected retry(): void {
    void this.store.load(this.slug(), this.scope(), true);
  }

  protected more(): void {
    void this.store.loadMore(this.slug(), this.scope());
  }

  protected reload(): void {
    this.stale.set(false);
    this.problem.set(null);
    void this.store.load(this.slug(), 'UPCOMING', true);
  }

  private readonly injector = inject(Injector);

  /** «Valorar» (ADR-0037): a capa (folla no móbil) e, se se enviou, as grazas. */
  protected async review(booking: MyBookingResponse): Promise<void> {
    const data: ReviewDialogData = {
      booking,
      day: formatDay(inMadrid(booking.start).date, this.language.lang(), 'long'),
      slug: this.slug(),
    };
    const ref = this.dialogs.open<boolean, ReviewDialogData, ReviewDialogComponent>(ReviewDialogComponent, {
      data,
      sheet: true,
      injector: this.injector,
    });
    if (await firstValueFrom(ref.closed, { defaultValue: undefined })) {
      this.toasts.show('reviews.thanks');
    }
  }

  /** O detalle da tarxeta e, se escolleu unha acción, esa acción. */
  protected async open(entry: CardEntry): Promise<void> {
    const t = (key: string) => this.transloco.translate(key);
    const data: MyBookingDetailData = {
      booking: entry.booking ?? null,
      wait: entry.wait ?? null,
      slug: this.slug(),
      phone: this.phone(),
      extraActions: entry.booking
        ? matchDetailActions(entry.booking, this.municipality.value()?.openMatchPolicy, t)
        : [],
    };
    const ref = this.dialogs.open<MyBookingDetailResult, MyBookingDetailData, MyBookingDetailComponent>(
      MyBookingDetailComponent,
      { data, sheet: true, injector: this.injector },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    const { booking, wait } = entry;
    if (result?.kind === 'leave' && wait) return this.leave(wait);
    if (!booking) return;
    if (result?.kind === 'extra') return this.matchAction(booking, result.id as MatchActionId);
    if (result?.kind === 'cancel') return this.cancel(booking);
    if (result?.kind === 'review') return this.review(booking);
    if (result?.kind === 'report') this.report(booking);
  }

  private readonly matches = inject(BookingMatchActions);

  /** As accións da partida desde o detalle da reserva (ADR-0040): a tarxeta cambia sen recargar. */
  private async matchAction(booking: MyBookingResponse, id: MatchActionId): Promise<void> {
    this.problem.set(null);
    try {
      const updated = await this.matches.run(this.slug(), booking, id, this.injector);
      if (updated) this.store.setOpenMatch(booking.id, updated);
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  /** «Avisar dun problema» (ADR-0036): o aviso coa instalación e a reserva xa postas. */
  private report(booking: MyBookingResponse): void {
    void openIncidentReport(this.dialogs, {
      slug: this.slug(),
      mode: 'resident',
      facilityId: booking.facilityId,
      bookingId: booking.id,
      subheading: `${booking.facilityName} · ${bookingSlot(booking)}`,
    });
  }

  private readonly cancelling = inject(BookingCancel);

  /** Cancelar (`BookingCancel`): ben, a tarxeta cambia sen recargar; se cambiou noutro sitio (412), «Recargar». */
  protected async cancel(booking: MyBookingResponse): Promise<void> {
    this.stale.set(false);
    this.problem.set(null);
    try {
      await this.cancelling.confirm(this.slug(), booking);
    } catch (error) {
      const problem = asProblem(error);
      if (problem.status === 412) {
        this.stale.set(true);
      } else {
        this.problem.set(problem);
      }
    }
  }
}
