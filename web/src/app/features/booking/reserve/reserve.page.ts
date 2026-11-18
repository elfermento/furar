// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Injector,
  input,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
import { ParticipantsSelection } from '../../../shared/dependents/participants';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { ConfirmedBookingStore } from './confirmed-booking.store';
import { ReserveConfirmedComponent } from './reserve-confirmed.component';
import { CustomFieldsFormComponent } from '../../../shared/custom-fields/custom-fields-form.component';
import { ReserveCustomFields } from './reserve-custom-fields';
import { ReserveFieldsComponent } from './reserve-fields.component';
import { ReserveParticipants } from './reserve-participants';
import { dayOfMonth } from '../validate/validate';
import { BookingBlockStore } from './booking-block.store';
import { ReserveAs } from './reserve-as';
import { ReserveHours } from './reserve-hours';
import { ReserveStore } from './reserve.store';
import { SimilarSlotsComponent } from './similar-slots.component';
import { SimilarSlotsStore } from './similar-slots.store';
import { WaitPanelComponent } from './wait-panel.component';
import { WaitsStore } from '../waits/waits.store';
import { UseAvailabilityStore } from './use-availability.store';

/**
 * Páxina de reservar (`/:concello/instalacions/:id/reservar`): «←» redondo
 * cara á ficha e «Reservar»; unha tarxeta centrada coa miniatura, o nome e o enderezo e os campos
 * Que · Data · Hora (con duración) · Parte (`furar-reserve-fields`), que pasan sós ao seguinte ao
 * escoller. Debaixo, o total e «Continuar» (sen sesión: entra e volve aquí co mesmo
 * escollido) ou «Confirmar reserva». Se alguén colle a hora antes, aviso suave arriba coas tres
 * horas libres máis próximas e a Hora baleira. Confirmada, `?reserva=` mostra «Reserva confirmada».
 * As horas ocupadas tamén saen, en gris: cunha escollida, «Avísame se se libera» e «Ocos parecidos»
 * no canto do total e «Continuar» (ADR-0033).
 * Todo o escollido vive no URL (`ReserveStore`). Unha conta de traballo (persoal activo nalgún
 * concello) non reserva como veciña: un aviso dío antes e «Confirmar reserva» queda desactivado
 * (ADR-0027, revisión do 2026-10-30); cambiar unha reserva que xa tiña si se pode.
 */
@Component({
  selector: 'furar-reserve-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    CustomFieldsFormComponent,
    IconComponent,
    ReserveConfirmedComponent,
    ReserveFieldsComponent,
    RouterLink,
    SimilarSlotsComponent,
    StatusStateComponent,
    TranslocoPipe,
    WaitPanelComponent,
  ],
  providers: [
    ReserveStore,
    BookingBlockStore,
    ConfirmedBookingStore,
    UseAvailabilityStore,
    ReserveCustomFields,
    ParticipantsSelection,
    ReserveParticipants,
    ReserveHours,
    ReserveAs,
    WaitsStore,
    SimilarSlotsStore,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reserve.page.html',
})
export class ReservePage implements OnInit {
  /** Parámetro `:id` da ruta. */
  readonly id = input.required<string>();

  protected readonly store = inject(ReserveStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  protected readonly confirmed = inject(ConfirmedBookingStore);
  protected readonly hours = inject(ReserveHours);
  private readonly blocks = inject(BookingBlockStore);
  private readonly staff = inject(StaffAccessService);
  private readonly injector = inject(Injector);
  private readonly fieldsForm = viewChild(CustomFieldsFormComponent);
  private readonly reserveFields = viewChild(ReserveFieldsComponent);
  protected readonly lang = this.language.lang;

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly thumb = computed(() => this.store.facility()?.images[0] ?? null);
  protected readonly address = computed(() => {
    const address = this.store.facility()?.address;
    if (!address) return '';
    const street = [address.street, address.streetNumber].filter(Boolean).join(', ');
    return [street, address.locality].filter(Boolean).join(' · ');
  });
  /** Modo cambio (`?cambiar=`): mesmo fluxo, «Confirmar o cambio». */
  protected readonly changing = computed(() => !!this.store.selection().change);
  protected readonly notFound = computed(() => this.store.problem()?.status === 404);
  /** Conta de traballo: non pode facer unha reserva nova (a API respondería `booking.staff-account`). */
  protected readonly workAccount = computed(() => !this.changing() && this.staff.workAccount());

  protected readonly totalText = computed(() => {
    this.langChanges();
    const total = this.store.total();
    if (total !== null) return formatCents(total);
    return this.store.use()?.price.kind === 'FREE' ? this.transloco.translate('booking.reserve.free') : null;
  });
  /** Bloqueada por faltas: ata cando, por que e a que teléfono chamar; sen «Continuar». */
  protected readonly blocked = computed(() => {
    this.langChanges();
    const block = this.changing() ? null : this.blocks.block();
    if (!block) return null;
    const one = block.faults === 1 ? 'One' : '';
    const key = block.phone ? `body${one}` : `bodyNoPhone${one}`;
    return {
      title: this.transloco.translate('booking.reserve.blocked.title', {
        until: dayOfMonth(block.until, this.language.lang()),
      }),
      body: this.transloco.translate(`booking.reserve.blocked.${key}`, {
        faults: block.faults,
        days: block.windowDays,
        phone: block.phone ?? '',
      }),
    };
  });
  private readonly paymentText = computed(() => {
    const note = localized(this.store.paymentNote(), this.language.lang());
    return this.store.use()?.price.kind === 'PAID' ? note : '';
  });
  /** Debaixo do botón: sen sesión, que se pedirá entrar; e, se é de pago, como se paga (nota do concello). */
  protected readonly fine = computed(() => {
    this.langChanges();
    const login = this.store.authenticated() ? '' : this.transloco.translate('booking.reserve.loginNote');
    return [login, this.paymentText()].filter(Boolean).join(' · ');
  });

  /** A instalación cargada: un oco parecido doutra instalación cambia o `:id` sen recrear a páxina. */
  private loadedId: string | null = null;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        if (this.loadedId !== null && this.loadedId !== id) void this.load();
      });
    });
  }

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    this.loadedId = this.id();
    void this.checkBlock();
    return this.store.load(this.id());
  }

  /**
   * Pregunta se a persoa da sesión está bloqueada por faltas. O bloqueo só impide reservas novas:
   * ao cambiar unha que xa ten non se pregunta.
   */
  private async checkBlock(): Promise<void> {
    const asking = this.store.authenticated() && !this.changing() && !this.store.selection().booking;
    if (this.slug() && asking) await this.blocks.check(this.slug());
  }

  /**
   * Confirmar. Sen hora (ou sen ninguén en «Para quen»), o botón non está desactivado: abre o
   * campo que falta. Se un campo propio non vale, o foco vai ao primeiro con erro (3.3.1).
   */
  protected async submit(): Promise<void> {
    if (this.reserveFields()?.openMissing()) return;
    await this.store.submit();
    // Quedou bloqueada mentres escollía: o aviso de sempre, coa data e o teléfono.
    if (this.store.submitError()?.code === 'booking.blocked-no-show') {
      await this.checkBlock();
      if (this.blocks.block()) this.store.submitError.set(null);
    }
    if (this.store.fields.showErrors()) {
      afterNextRender(() => this.fieldsForm()?.focusFirstInvalid(), { injector: this.injector });
    }
  }

  protected pickAlternative(time: string): void {
    this.store.selectTime(time);
  }
}
