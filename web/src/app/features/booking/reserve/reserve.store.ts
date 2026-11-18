// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Api } from '../../../api/api';
import { loadPaymentTexts } from '../../../core/municipality/payment-notes';
import { getPublicFacility } from '../../../api/fn/facilities/get-public-facility';
import type { AvailabilityResponse } from '../../../api/models/availability-response';
import type { AvailableSlotResponse } from '../../../api/models/available-slot-response';
import type { NoteResponse } from '../../../api/models/note-response';
import type { PublicFacilityDetailResponse } from '../../../api/models/public-facility-detail-response';
import type { PublicUseResponse } from '../../../api/models/public-use-response';
import { SessionService } from '../../../core/auth/session.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import type { OfferedSlot, ReserveSelection } from './reserve';
import { durationOptions, offeredSlots, readSelection, selectionParams, takenTimes, totalCents } from './reserve';
import { openedUse, useMatches } from './reserve';
import { ParticipantsSelection } from '../../../shared/dependents/participants';
import { ConfirmedBookingStore } from './confirmed-booking.store';
import { ReserveCustomFields } from './reserve-custom-fields';
import { ReserveParticipants } from './reserve-participants';
import { UseAvailabilityStore } from './use-availability.store';
import { ReserveAs } from './reserve-as';

export type {
  AvailabilityResponse,
  AvailableSlotResponse,
  NoteResponse,
  PublicFacilityDetailResponse,
  PublicUseResponse,
};

/** Aviso do 409: a hora que se perdeu e as (ata) tres horas libres máis próximas que manda a API. */
export interface TakenNotice {
  readonly time: string;
  readonly alternatives: readonly string[];
}

/**
 * Estado da páxina de reservar. O escollido vive no URL (`readSelection`): cada escolla
 * navega con `replaceUrl` e o resto deríbase del, así recargar, compartir e volver de Keycloak
 * deixan a persoa no mesmo paso. Pide a ficha pública e a nota de pago do concello (os textos de
 * pago nunca van no código), os ocos dun uso e un día (só se ofrecen os que manda a API) e, ao
 * confirmar, `POST /me/bookings`. Un 409 `booking.slot-taken` deixa a Hora baleira e garda as
 * alternativas para o aviso. Con sesión, tamén os campos propios da reserva (`ReserveCustomFields`).
 * Un por páxina.
 */
@Injectable()
export class ReserveStore {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(SessionService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly confirmed = inject(ConfirmedBookingStore);

  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });
  private readonly facilityId = signal<string | null>(null);
  private readonly slug = computed(() => this.municipality.slug() ?? '');

  /** O escollido, tal como está no URL. */
  readonly selection = computed(() => readSelection(this.params()));

  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly problem = signal<ApiProblem | null>(null);
  readonly facility = signal<PublicFacilityDetailResponse | null>(null);
  readonly paymentNote = signal<NoteResponse | null>(null);

  private readonly slotsStore = inject(UseAvailabilityStore);
  readonly availability = this.slotsStore.availability.asReadonly();
  readonly availabilityState = this.slotsStore.state.asReadonly();
  /** Os ocos á vista son doutro uso ou día: estanse a pedir os novos. */
  readonly slotsStale = this.slotsStore.stale.asReadonly();

  readonly taken = signal<TakenNotice | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<ApiProblem | null>(null);

  readonly authenticated = this.session.authenticated;
  readonly email = this.session.email;
  readonly fields = inject(ReserveCustomFields);
  /** Para quen é, nos usos por prazas (ADR-0031): cada persoa ocupa unha praza. */
  readonly who = inject(ParticipantsSelection);
  /** As persoas a cargo da conta: só na páxina da persoa (no mostrador son as do correo). */
  private readonly people = inject(ReserveParticipants, { optional: true });
  /** «Reservar como» unha entidade (ADR-0039): só na páxina da persoa. */
  private readonly as = inject(ReserveAs, { optional: true });

  /** O uso do URL ou, se non hai (ou xa non existe), o primeiro co deporte buscado ou o primeiro. */
  readonly use = computed<PublicUseResponse | null>(() => openedUse(this.facility()?.uses ?? [], this.selection()));
  /** O día do URL ou hoxe segundo a API. */
  readonly date = computed<IsoDate | null>(() => this.selection().date ?? this.availability()?.today ?? null);
  private readonly daySlots = computed<readonly AvailableSlotResponse[]>(() => {
    const availability = this.availability();
    return availability?.bookable && !this.slotsStale() ? availability.slots : [];
  });
  readonly durations = computed(() => {
    const use = this.use();
    return use ? durationOptions(use, this.daySlots()) : [1];
  });
  readonly slots = computed(() => Math.min(this.selection().slots, this.durations().at(-1) ?? 1));
  readonly offered = computed<OfferedSlot[]>(() => {
    const use = this.use();
    return use ? offeredSlots(this.daySlots(), this.slots(), use.slotMinutes) : [];
  });
  /** O oco escollido, só se a API o segue ofrecendo coa duración escollida. */
  readonly chosen = computed(() => this.offered().find((slot) => slot.time === this.selection().time) ?? null);
  /** A parte escollida, só se está libre durante toda a reserva; se non, «Tanto me ten». */
  readonly partId = computed(() => {
    const part = this.selection().partId;
    const free = this.chosen()?.freeParts;
    return part && this.use()?.rentalMode === 'PARTS' && (!free || free.includes(part)) ? part : null;
  });
  /** Uso por prazas: a reserva leva as súas persoas. */
  readonly bySeats = computed(() => this.use()?.rentalMode === 'SEATS');
  /** As prazas da reserva: unha por persoa nos usos por prazas; se non, unha. */
  readonly seats = computed(() => (this.bySeats() ? this.who.seats() : 1));
  /** Falta dicir para quen é. */
  readonly nobody = computed(() => this.bySeats() && this.who.seats() === 0);
  readonly total = computed(() => {
    const use = this.use();
    const cents = use ? totalCents(use, this.slots()) : null;
    return cents === null ? null : cents * Math.max(1, this.seats());
  });

  private readonly availabilityKey = computed(
    () => {
      const slug = this.slug();
      const facilityId = this.facilityId();
      const useId = this.use()?.id;
      // Coa reserva xa confirmada á vista non fan falta ocos.
      if (this.selection().booking) return null;
      return slug && facilityId && useId ? { slug, facilityId, useId, date: this.selection().date } : null;
    },
    {
      equal: (a, b) =>
        a === b ||
        (!!a && !!b && a.slug === b.slug && a.facilityId === b.facilityId && a.useId === b.useId && a.date === b.date),
    },
  );
  private readonly bookingId = computed(() => this.selection().booking);
  private readonly changeId = computed(() => this.selection().change);

  constructor() {
    // Os ocos dun uso e un día: só cando cambia o uso ou a data (non con cada escolla).
    effect(() => {
      const key = this.availabilityKey();
      if (key) untracked(() => void this.slotsStore.load(key.slug, key.facilityId, key.useId, key.date));
    });
    // A reserva confirmada (`?reserva=`): se non está en memoria (recarga), pídese.
    effect(() => {
      const id = this.bookingId();
      const slug = this.slug();
      if (id && slug) untracked(() => void this.confirmed.load(slug, id, this.selection().organizationId ?? null));
    });
    // Modo cambio (`?cambiar=`): a reserva orixinal; se o URL non trae uso, ábrese co seu.
    effect(() => {
      const id = this.changeId();
      const slug = this.slug();
      if (id && slug) untracked(() => void this.startChange(slug, id));
    });
    this.people?.watch(this.bySeats);
  }

  /** Carga a instalación (e a nota de pago) e, se o URL trae hora sen uso, busca o uso que a ten. */
  async load(facilityId: string): Promise<void> {
    const slug = this.slug();
    if (!slug) return;
    this.state.set('loading');
    this.problem.set(null);
    // A nota do concello ou, se van por instalación, a desta instalación.
    const notes = loadPaymentTexts(this.api, slug, facilityId).then((texts) => this.paymentNote.set(texts.payment));
    const fields = this.authenticated() ? this.fields.load(slug) : Promise.resolve();
    this.as?.load(slug);
    try {
      const facility = await this.api.invoke(getPublicFacility, { slug, id: facilityId });
      await this.resolveUse(slug, facility);
      this.facility.set(facility);
      await notes;
      await fields;
      this.facilityId.set(facilityId);
      this.state.set('ready');
    } catch (error) {
      this.problem.set(asProblem(error));
      this.state.set('error');
    }
  }

  /** Outro uso; con `time` e `partId`, unha hora libre noutro uso. */
  selectUse(useId: string, time: string | null = null, partId: string | null = null): void {
    this.taken.set(null);
    void this.navigate({ useId, time, slots: 1, partId });
  }

  selectDate(date: IsoDate): void {
    this.taken.set(null);
    void this.navigate({ date, time: null, partId: null });
  }

  /** Cambiar a duración mantén a hora só se segue cabendo (ou `keepTime`: unha hora ocupada). */
  selectDuration(slots: number, keepTime = false): void {
    const use = this.use();
    const time = this.selection().time;
    const fits = !!use && offeredSlots(this.daySlots(), slots, use.slotMinutes).some((slot) => slot.time === time);
    void this.navigate({ slots, time: fits || keepTime ? time : null });
  }

  selectTime(time: string): void {
    this.taken.set(null);
    this.submitError.set(null);
    const slot = this.offered().find((each) => each.time === time);
    const part = this.selection().partId;
    void this.navigate({ time, partId: part && slot?.freeParts?.includes(part) ? part : null });
  }

  selectPart(partId: string | null): void {
    void this.navigate({ partId });
  }

  /**
   * «Continuar» sen sesión: a Keycloak co URL de agora, que xa leva todo o escollido. Con sesión,
   * reserva (ou cambia, no modo cambio); ben, a confirmación (`?reserva=`); 409, o aviso coas
   * alternativas.
   */
  async submit(): Promise<void> {
    if (!this.session.authenticated()) {
      await this.session.login(this.router.url);
      return;
    }
    const chosen = this.chosen();
    const use = this.use();
    const facilityId = this.facilityId();
    if (!chosen || !use || !facilityId || this.submitting() || this.nobody()) return;
    // No modo cambio, nunca unha reserva nova mentres non está a orixinal.
    if (this.selection().change && !this.confirmed.original()) return;
    const changing = !!this.selection().change;
    if (!changing && !this.fields.check()) return;
    this.submitting.set(true);
    this.submitError.set(null);
    this.taken.set(null);
    const partId = this.partId();
    // Como unha entidade: nunca nos usos por prazas (o campo non sae neles).
    const organizationId = this.bySeats() || changing ? null : (this.as?.organizationId() ?? null);
    try {
      const body = {
        facilityId,
        useId: use.id,
        start: chosen.slot.start,
        slots: this.slots(),
        ...(partId ? { partId } : {}),
        ...(changing ? {} : this.fields.body()),
        ...(this.bySeats() ? this.people?.body(changing) : {}),
      };
      const booking = await this.confirmed.save(this.slug(), body, organizationId);
      await this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { reserva: booking.id, ...(organizationId ? { como: organizationId } : {}) },
        replaceUrl: true,
      });
    } catch (error) {
      const problem = asProblem(error);
      if (problem.status === 409 && problem.code === 'booking.slot-taken') {
        this.onTaken(chosen.time, problem);
      } else if (changing || !this.fields.apply(problem)) {
        this.submitError.set(problem);
      }
    } finally {
      this.submitting.set(false);
    }
  }

  private async startChange(slug: string, id: string): Promise<void> {
    const original = await this.confirmed.loadOriginal(slug, id);
    if (original?.rentalMode === 'SEATS') this.people?.startChange(original.participants);
    if (!original || this.selection().useId) return;
    const { date } = inMadrid(original.start);
    await this.navigate({ useId: original.useId, date, slots: original.slots, partId: original.partId ?? null });
  }

  private onTaken(time: string, problem: ApiProblem): void {
    this.taken.set({ time, alternatives: takenTimes(problem.extensions['alternatives']) });
    void this.navigate({ time: null, partId: null });
    const facilityId = this.facilityId();
    const use = this.use();
    if (facilityId && use) void this.slotsStore.load(this.slug(), facilityId, use.id, this.selection().date);
  }

  /**
   * O listado leva só `?data=&hora=` (e `?deporte=` se se buscou un): se hai varios usos, o primeiro
   * dos que valen (os do deporte, ou todos) que ten esa hora libre ese día pasa a ser o do URL. Se
   * ningún a ten, queda o primeiro deles e a Hora sae ocupada.
   */
  private async resolveUse(slug: string, facility: PublicFacilityDetailResponse): Promise<void> {
    const { useId, time, date, sports } = this.selection();
    if (useId || !time || facility.uses.length < 2) return;
    const candidates = facility.uses.filter((use) => useMatches(use, sports));
    const found = await this.slotsStore.useWithTime(slug, facility, date, time, candidates);
    if (found && found !== (candidates[0] ?? facility.uses[0]).id) await this.navigate({ useId: found });
  }

  private navigate(change: Partial<ReserveSelection>): Promise<boolean> {
    const queryParams = selectionParams({ ...this.selection(), ...change, booking: null });
    // `merge`: os parámetros doutros (a instalación da reserva por outra persoa) quedan.
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
