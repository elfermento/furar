// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import { merge, switchMap } from 'rxjs';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { buildCustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import type { CustomFieldDefinition } from '../../../shared/custom-fields/custom-field.types';
import type { AmenitiesForm } from '../amenities/amenities';
import { amenitiesForm, offeredAmenities } from '../amenities/amenities';
import type { DataForm } from '../data-card/data-form';
import { NAME_MAX, dataForm, dataValue, nameControl } from '../data-card/data-form';
import type { DescriptionForm } from '../description/description-form';
import {
  HOUSE_RULES_INVALID,
  HOUSE_RULES_MAX,
  descriptionForm,
  descriptionValue,
  houseRulesValue,
  optionalBilingualForm,
} from '../description/description-form';
import { PAYMENT_NOTE_MAX } from '../description/payment-notes-section.component';
import type { PaymentNotesResponse } from '../description/payment-notes.store';
import type { Facility } from '../detail/facility-detail.store';
import type { LocationForm } from '../location/location-form';
import { locationForm, locationValue } from '../location/location-form';
import type { DraftImage } from '../photos/photos';
import { fromImage } from '../photos/photos';
import type { DrawingResponse } from '../photos/photos.store';
import { PhotosStore } from '../photos/photos.store';
import type { DayHours, Season } from '../schedule/schedule.store';
import { DAYS } from '../schedule/schedule.store';
import type { WeekForm } from '../schedule/week-form';
import { dayIntervals, weekForm } from '../schedule/week-form';
import type { ScheduleMode } from './wizard';
import { DEFAULT_HOURS, modeOf, sameWeek } from './wizard';

export { NAME_MAX };
/** Erro do paso 1 sen deportes (só da web: a API garda a instalación sen eles). */
export const SPORTS_REQUIRED = 'facility.sports-required';

export type PaymentScope = 'ALL' | 'FACILITY';
type SameHoursForm = FormGroup<{ startMinute: FormControl<number>; endMinute: FormControl<number> }>;

function endAfterStart(group: AbstractControl): ValidationErrors | null {
  const { startMinute, endMinute } = (group as SameHoursForm).getRawValue();
  return endMinute <= startMinute ? { 'schedule.end-before-start': { index: 0 } } : null;
}

const weekOf = (week: readonly DayHours[]): WeekForm =>
  weekForm({ id: '', name: '', startDate: '', endDate: '', version: 0, week });

/**
 * Formularios do asistente «Nova instalación», un por sección da ficha (os mesmos validadores),
 * máis as fotos e o horario: o que se escribe en cada paso. Un por páxina.
 */
@Injectable()
export class WizardForms {
  private readonly deployment = inject(DeploymentSettings);
  private readonly municipality = inject(CurrentMunicipality);

  readonly name = nameControl();
  readonly data: DataForm = dataForm();
  readonly description: DescriptionForm = descriptionForm();
  readonly location: LocationForm = locationForm();
  readonly amenities: AmenitiesForm = amenitiesForm([]);
  readonly houseRules: DescriptionForm = optionalBilingualForm(HOUSE_RULES_MAX, HOUSE_RULES_INVALID);
  readonly payment = new FormGroup({
    payment: optionalBilingualForm(PAYMENT_NOTE_MAX, 'validation.required'),
    refund: optionalBilingualForm(PAYMENT_NOTE_MAX, 'validation.required'),
  });
  readonly scheduleMode = new FormControl<ScheduleMode>('SAME', { nonNullable: true });
  readonly sameHours: SameHoursForm = new FormGroup(
    {
      startMinute: new FormControl(DEFAULT_HOURS.startMinute, { nonNullable: true }),
      endMinute: new FormControl(DEFAULT_HOURS.endMinute, { nonNullable: true }),
    },
    { validators: endAfterStart },
  );
  readonly week = signal<WeekForm>(weekOf(sameWeek(DEFAULT_HOURS)));
  /** O horario cambiou (modo ou semana nova) aínda que os formularios non estean sucios. */
  readonly scheduleTouched = signal(false);
  /** Fotos da instalación (só fotos: o debuxo vai á parte e só se ve sen fotos). */
  readonly images = signal<readonly DraftImage[]>([]);
  /** Debuxo escollido; nulo = o suxerido. */
  readonly drawing = signal<string | null>(null);
  readonly photosDirty = signal(false);
  /** Debuxos incluídos (pídense unha vez, ao velos). */
  readonly drawings = signal<readonly DrawingResponse[]>([]);
  /** Campos propios das instalacións (paso 3). */
  readonly definitions = signal<readonly CustomFieldDefinition[]>([]);
  /** Pago e devolución: iguais para todas (`ALL`) ou en cada instalación (`FACILITY`, paso 7). */
  readonly scope = signal<PaymentScope>('ALL');
  readonly notes = signal<PaymentNotesResponse | null>(null);
  /** Cambia con calquera formulario: as comprobacións dos pasos dependen del. */
  readonly changes = signal(0);
  /** O modo do horario antes do último cambio. */
  private mode: ScheduleMode = 'SAME';
  private readonly photosStore = inject(PhotosStore);
  private drawingsLoaded = false;

  constructor() {
    this.data.controls.sports.addValidators((control) =>
      Array.isArray(control.value) && control.value.length === 0 ? { [SPORTS_REQUIRED]: {} } : null,
    );
    this.data.controls.sports.updateValueAndValidity({ emitEvent: false });
    // Sen mapa no despregue non hai onde poñer a chincheta: o punto é opcional (publícase sen el).
    this.location.controls.point.setValidators((control) =>
      this.deployment.mapEnabled() && !control.value ? { required: true } : null,
    );
    this.location.controls.point.updateValueAndValidity({ emitEvent: false });
    this.location.controls.ineCode.setValue(this.municipality.value()?.legal.ineCode ?? '');
    merge(
      this.name.events,
      this.data.events,
      this.description.events,
      this.location.events,
      this.amenities.events,
      this.houseRules.events,
      this.payment.events,
      this.scheduleMode.events,
      this.sameHours.events,
      toObservable(this.week).pipe(switchMap((week) => week.events)),
    ).subscribe(() => this.touch());
    this.scheduleMode.valueChanges.subscribe((mode) => {
      if (mode !== this.mode) this.convertSchedule(mode);
      this.mode = mode;
    });
  }

  /** As comprobacións volven calcularse (tamén tras cambiar as fotos ou os usos). */
  touch(): void {
    this.changes.update((n) => n + 1);
  }

  /** Os campos propios, cos valores gardados (se os hai). */
  setDefinitions(definitions: readonly CustomFieldDefinition[], values: Record<string, unknown>): void {
    this.data.setControl('customFields', buildCustomFieldsForm(definitions, values));
    this.definitions.set(definitions);
  }

  /** Enche os formularios coa instalación gardada e a súa tempada (quedan sen cambios). */
  fill(facility: Facility, season: Season | null): void {
    this.name.reset(facility.name);
    const data = dataValue(facility, Object.keys(this.data.controls.customFields.controls));
    if (data) this.data.reset(data);
    this.description.reset(descriptionValue(facility) ?? { gl: '', es: '' });
    const location = locationValue(facility);
    if (location) this.location.reset(location);
    if (!this.location.controls.ineCode.value) {
      this.location.controls.ineCode.setValue(this.municipality.value()?.legal.ineCode ?? '');
    }
    const offered = new Set(offeredAmenities(facility));
    for (const [item, control] of Object.entries(this.amenities.controls)) {
      control.reset(offered.has(item as keyof AmenitiesForm['controls']));
    }
    this.houseRules.reset(houseRulesValue(facility) ?? { gl: '', es: '' });
    const notes = facility.paymentNotes;
    this.payment.reset({
      payment: { gl: notes?.payment?.gl ?? '', es: notes?.payment?.es ?? '' },
      refund: { gl: notes?.refund?.gl ?? '', es: notes?.refund?.es ?? '' },
    });
    this.images.set(facility.images.filter((image) => image.kind === 'PHOTO').map(fromImage));
    const only = facility.images.length === 1 ? facility.images[0] : null;
    this.drawing.set(only?.kind === 'DRAWING' && only.id !== facility.suggestedImage?.id ? only.id : null);
    if (season) {
      const { mode, same } = modeOf(season.week);
      this.sameHours.reset(same);
      this.week.set(weekForm(season));
      this.mode = mode;
      this.scheduleMode.reset(mode);
    }
    this.touch();
  }

  /** O horario está ben: o tramo único ou a semana, cun día aberto polo menos. */
  scheduleOk(): boolean {
    if (this.scheduleMode.value === 'SAME') return this.sameHours.valid;
    const week = this.week();
    return week.valid && week.controls.week.controls.some((day) => day.controls.open.value);
  }

  /** «Por días» sen ningún día aberto. */
  noOpenDay(): boolean {
    return this.scheduleMode.value === 'DAYS' && !this.week().controls.week.controls.some((d) => d.controls.open.value);
  }

  /** A semana que se garda: «Mesmo horario» de luns a domingo (festivos pechados) ou día a día. */
  weekBody(): DayHours[] {
    if (this.scheduleMode.value === 'SAME') return sameWeek(this.sameHours.getRawValue());
    const rows = this.week().controls.week.controls;
    return DAYS.map((day, i) => ({ day, intervals: rows[i] ? dayIntervals(rows[i]) : [] }));
  }

  /** O horario xa se gardou tal como está. */
  markSchedulePristine(): void {
    this.scheduleMode.markAsPristine();
    this.sameHours.markAsPristine();
    this.week().markAsPristine();
    this.scheduleTouched.set(false);
  }

  get scheduleDirty(): boolean {
    return this.scheduleMode.dirty || this.sameHours.dirty || this.week().dirty || this.scheduleTouched();
  }

  /** «Mesmo horario» ↔ «Por días»: a semana parte do tramo único e ao revés. */
  private convertSchedule(mode: ScheduleMode): void {
    if (mode === 'DAYS') {
      if (!this.week().controls.week.controls.some((day) => day.controls.open.value)) {
        this.week.set(weekOf(sameWeek(this.sameHours.getRawValue())));
      }
    } else {
      const open = this.week()
        .controls.week.controls.map((day) => dayIntervals(day))
        .find((intervals) => intervals.length);
      if (open) this.sameHours.setValue({ ...open[0] });
    }
    this.scheduleTouched.set(true);
    this.touch();
  }

  // --- Fotos ---

  /** Os debuxos incluídos, unha vez (ao velos no paso «Fotos» ou en «Revisar»). */
  loadDrawings(): void {
    const slug = this.municipality.slug();
    if (this.drawingsLoaded || !slug) return;
    this.drawingsLoaded = true;
    this.photosStore.drawings(slug).then(
      (list) => this.drawings.set(list),
      () => (this.drawingsLoaded = false),
    );
  }

  setPhotos(list: readonly DraftImage[]): void {
    this.images.set(list);
    this.photosDirty.set(true);
    this.touch();
  }

  /** Escolle un debuxo; o suxerido gárdase como «ningún» (segue o suxerido se cambia o nome). */
  chooseDrawing(code: string, suggested: string | null): void {
    this.drawing.set(code === suggested ? null : code);
    this.photosDirty.set(true);
    this.touch();
  }
}
