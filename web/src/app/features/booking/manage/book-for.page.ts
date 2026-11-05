// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { focusFirstInvalid } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { formatCents } from '../../../shared/ui/money/money';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmedBookingStore } from '../reserve/confirmed-booking.store';
import { ReserveCustomFields } from '../reserve/reserve-custom-fields';
import { ReserveFieldsComponent } from '../reserve/reserve-fields.component';
import { ReserveStore } from '../reserve/reserve.store';
import { UseAvailabilityStore } from '../reserve/use-availability.store';
import type { ManagedBookingDetailResponse } from './manage-bookings';
import type { SavedSlot } from './book-for';
import { bookingWhen, doneToast, keptSlot, slotParams, takenAlternatives } from './book-for';
import { BookForWhoComponent } from './book-for-who.component';
import { BookForStore, holderBody, holderForm } from './book-for.store';

registerSymbols(LAZY_SYMBOLS);

let nextId = 0;

/**
 * Reservar no nome doutra persoa e cambiar unha reserva desde a xestión (maqueta 58):
 * `/:concello/xestion/reservas/nova` e `/:concello/xestion/reservas/:id/cambiar`. O mesmo
 * formulario ca a veciñanza na web pública (`furar-reserve-fields` co seu `ReserveStore`: Que ·
 * Data · Hora · Parte, cos ocos da API e o escollido no URL), máis a instalación (só as publicadas e
 * abertas), «¿Para quen?» (nome, correo para a confirmación e teléfono, e os campos propios da
 * reserva) e, se o uso ten prezo, se xa pagou. Cambiar abre o mesmo formulario relleno (a
 * instalación non cambia) e, se a persoa ten correo, «Avisar por correo», marcado. Nunha reserva
 * feita coa conta da persoa, os seus datos son os da conta e só se ven. Ben, volve a «Reservas» co
 * detalle aberto e un aviso; se alguén colleu a hora, o aviso suave coas horas libres próximas.
 */
@Component({
  selector: 'furar-book-for-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    BookForWhoComponent,
    FieldComponent,
    IconComponent,
    ReserveFieldsComponent,
    RouterLink,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SelectComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [BookForStore, ReserveStore, ConfirmedBookingStore, UseAvailabilityStore, ReserveCustomFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './book-for.page.html',
})
export class BookForPage implements OnInit {
  /** Parámetro `:id` da ruta: a reserva que se cambia; sen el, reserva nova. */
  readonly id = input<string | undefined>(undefined);

  protected readonly store = inject(BookForStore);
  protected readonly reserve = inject(ReserveStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly ids = `furar-book-for-${nextId++}`;
  protected readonly lang = this.language.lang;
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly holder = holderForm();
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly facilityId = signal<string | null>(null);
  protected readonly paid = signal(false);
  protected readonly notify = signal(true);
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** Cando era a reserva que se cambia. */
  private readonly before = signal<Pick<ManagedBookingDetailResponse, 'start' | 'end'> | null>(null);

  protected readonly changing = computed(() => !!this.id());
  /** Reserva feita coa conta da persoa: os seus datos non se corrixen aquí. */
  protected readonly account = computed(() => this.store.original()?.holderAccount ?? false);
  /** «←» volve ás reservas (ao cambiar, co detalle aberto). */
  protected readonly back = computed(() => ({ reserva: this.id() ?? null }));

  protected readonly facilityOptions = computed<SelectOption<string>[]>(() => {
    const original = this.store.original();
    // Ao cambiar, a instalación é a da reserva (P2: o cambio vai na mesma instalación).
    if (original) return [{ value: original.facilityId, label: original.facilityName }];
    return this.store.facilities().map((f) => ({ value: f.id, label: f.name }));
  });
  /** Pago só nos usos de pago. */
  protected readonly priced = computed(() => this.reserve.use()?.price.kind === 'PAID');
  protected readonly totalText = computed(() => {
    this.langChanges();
    const total = this.reserve.total();
    if (total !== null) return formatCents(total);
    return this.reserve.use()?.price.kind === 'FREE' ? this.transloco.translate('booking.reserve.free') : null;
  });
  /** O nome que se está a escribir, para o botón «Reservar para Ana». */
  private readonly holderName = toSignal(this.holder.controls.name.valueChanges, { initialValue: '' });
  private readonly holderEmail = toSignal(this.holder.controls.email.valueChanges, { initialValue: '' });
  protected readonly goText = computed(() => {
    this.langChanges();
    if (this.changing()) return this.transloco.translate('booking.bookFor.save');
    const name = this.holderName().trim();
    return name
      ? this.transloco.translate('booking.bookFor.confirmFor', { name })
      : this.transloco.translate('booking.bookFor.confirm');
  });
  /** «Avisar a Ana por correo»: ao cambiar, se ten correo. */
  protected readonly canNotify = computed(
    () => this.changing() && !!(this.account() ? this.store.original()?.holderEmail : this.holderEmail().trim()),
  );
  protected readonly notifyName = computed(() => this.holderName().trim() || this.store.original()?.holderName || '');

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    const slug = this.slug();
    if (!slug) return;
    this.state.set('loading');
    try {
      const id = this.id();
      if (id) {
        const booking = await this.store.loadOriginal(slug, id);
        this.prefill(booking);
        // O oco da reserva no URL (o escollido do formulario vive alí).
        await this.router.navigate([], { relativeTo: this.route, queryParams: slotParams(booking), replaceUrl: true });
        await this.reserve.load(booking.facilityId);
        this.reserve.fields.fill(booking.customFields as Record<string, unknown>);
        this.facilityId.set(booking.facilityId);
      } else {
        await this.store.loadFacilities(slug);
        const wanted = this.route.snapshot.queryParamMap.get('instalacion');
        const first = this.store.facilities().find((f) => f.id === wanted) ?? this.store.facilities()[0];
        if (first) {
          this.facilityId.set(first.id);
          await this.reserve.load(first.id);
        }
      }
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  /** Outra instalación: o escollido empeza de novo. */
  protected async pickFacility(id: string): Promise<void> {
    if (id === this.facilityId()) return;
    this.facilityId.set(id);
    this.problem.set(null);
    await this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { instalacion: id },
      replaceUrl: true,
    });
    await this.reserve.load(id);
  }

  protected pickPayment(value: unknown): void {
    this.paid.set(value === true);
  }

  protected toggleNotify(event: Event): void {
    this.notify.set((event.target as HTMLInputElement).checked);
  }

  protected async submit(): Promise<void> {
    this.submitted.set(true);
    this.problem.set(null);
    this.reserve.taken.set(null);
    const time = this.reserve.chosen()?.time ?? '';
    const facilityId = this.facilityId();
    const holderOk = this.account() || this.holder.valid;
    const fieldsOk = this.reserve.fields.check();
    if (!holderOk || !fieldsOk) {
      this.holder.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    const slot = this.slot();
    if (!slot || !facilityId || this.submitting()) return;
    this.submitting.set(true);
    const customFields = this.reserve.fields.body();
    try {
      const slug = this.slug();
      let saved: ManagedBookingDetailResponse;
      if (this.changing()) {
        saved = await this.store.change(slug, {
          ...slot,
          ...(this.account() ? {} : { holder: holderBody(this.holder) }),
          ...(this.priced() ? { paid: this.paid() } : {}),
          notifyPerson: this.notify(),
          customFields: customFields.customFields ?? {},
        });
      } else {
        saved = await this.store.create(slug, {
          facilityId,
          ...slot,
          holder: holderBody(this.holder),
          ...(this.priced() ? { paid: this.paid() } : {}),
          language: this.lang() === 'es' ? 'es' : 'gl',
          ...customFields,
        });
      }
      const toast = doneToast(saved, this.before(), this.notify());
      this.toasts.show(toast.key, toast.params);
      await this.router.navigate(['/', slug, 'xestion', 'reservas'], { queryParams: { reserva: saved.id } });
    } catch (error) {
      this.fail(error, time);
    } finally {
      this.submitting.set(false);
    }
  }

  /** O oco que se garda: o escollido ou, ao cambiar sen escoller outra hora, o de antes. */
  private readonly slot = computed<SavedSlot | null>(() => {
    const chosen = this.reserve.chosen();
    const use = this.reserve.use();
    if (!chosen || !use) return keptSlot(this.store.original(), this.reserve.selection().time);
    const partId = this.reserve.partId();
    return { useId: use.id, start: chosen.slot.start, slots: this.reserve.slots(), ...(partId ? { partId } : {}) };
  });
  protected readonly canSave = computed(() => this.slot() !== null);
  /** «Antes: mércores, 4 de novembro · 18:00–19:00», debaixo dos campos ao cambiar. */
  protected readonly beforeText = computed(() => {
    this.langChanges();
    const before = this.before();
    return before
      ? this.transloco.translate('booking.bookFor.before', { when: bookingWhen(before, this.lang()) })
      : null;
  });

  private fail(error: unknown, time: string): void {
    const problem = asProblem(error);
    if (problem.status === 409 && problem.code === 'booking.slot-taken') {
      this.reserve.taken.set({ time, alternatives: takenAlternatives(problem) });
    } else if (!this.reserve.fields.apply(problem)) {
      this.problem.set(problem);
    }
  }

  private prefill(booking: ManagedBookingDetailResponse): void {
    this.before.set({ start: booking.start, end: booking.end });
    this.holder.setValue({
      name: booking.holderName ?? '',
      email: booking.holderEmail ?? '',
      phone: booking.holderPhone ?? '',
    });
    this.paid.set(booking.paymentStatus === 'PAID');
  }
}
