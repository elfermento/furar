// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Injector,
  input,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
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
import { ReserveStore } from './reserve.store';
import { UseAvailabilityStore } from './use-availability.store';

/**
 * Páxina de reservar (`/:concello/instalacions/:id/reservar`, D2-12, maquetas 11 e 13): «←» redondo
 * cara á ficha e «Reservar»; unha tarxeta centrada coa miniatura, o nome e o enderezo e os campos
 * Que · Data · Hora (con duración) · Parte, cada un en `furar-field-popover`, que pasan sós ao
 * seguinte ao escoller. Debaixo, o total e «Continuar» (sen sesión: entra e volve aquí co mesmo
 * escollido) ou «Confirmar reserva». Se alguén colle a hora antes, aviso suave arriba coas tres
 * horas libres máis próximas e a Hora baleira. Confirmada, `?reserva=` mostra «Reserva confirmada».
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
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [ReserveStore, ConfirmedBookingStore, UseAvailabilityStore, ReserveCustomFields],
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
  private readonly staff = inject(StaffAccessService);
  private readonly injector = inject(Injector);
  private readonly fieldsForm = viewChild(CustomFieldsFormComponent);
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

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    return this.store.load(this.id());
  }

  /** Confirmar; se un campo propio non vale, o foco vai ao primeiro con erro (3.3.1). */
  protected async submit(): Promise<void> {
    await this.store.submit();
    if (this.store.fields.showErrors()) {
      afterNextRender(() => this.fieldsForm()?.focusFirstInvalid(), { injector: this.injector });
    }
  }

  protected pickAlternative(time: string): void {
    this.store.selectTime(time);
  }
}
