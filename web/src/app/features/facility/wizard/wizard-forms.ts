// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import type { Subscription } from 'rxjs';
import { merge } from 'rxjs';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { buildCustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
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
import type { Season } from '../schedule/schedule.store';
import { DEFAULT_HOURS, sameWeek, wholeYear } from './wizard';
import { SeasonEditor, overlapOf } from '../schedule/season-editor';

export { NAME_MAX };
/** Erro do paso 1 sen deportes (só da web: a API garda a instalación sen eles). */
export const SPORTS_REQUIRED = 'facility.sports-required';

export type PaymentScope = 'ALL' | 'FACILITY';

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
  /** As tempadas do paso «Horario», na orde na que se ven. */
  readonly seasons = signal<readonly SeasonEditor[]>([]);
  /** Tempadas gardadas que se quitaron: bórranse ao gardar o paso. */
  readonly removedSeasons = signal<ReadonlySet<string>>(new Set());
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
  private nextSeason = 0;
  private readonly seasonEvents = new Map<string, Subscription>();
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
    merge(
      this.name.events,
      this.data.events,
      this.description.events,
      this.location.events,
      this.amenities.events,
      this.houseRules.events,
      this.payment.events,
    ).subscribe(() => this.touch());
    this.setSeasons([this.blankSeason(madridToday())]);
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

  /** Enche os formularios coa instalación gardada e as súas tempadas (quedan sen cambios). */
  fill(facility: Facility, seasons: readonly Season[], today: IsoDate = madridToday()): void {
    this.name.reset(facility.name);
    const data = dataValue(facility, Object.keys(this.data.controls.customFields.controls));
    if (data) this.data.reset(data);
    this.description.reset(descriptionValue(facility) ?? { gl: '', es: '' });
    const location = locationValue(facility);
    if (location) this.location.reset(location);
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
    this.removedSeasons.set(new Set());
    this.setSeasons(
      seasons.length ? seasons.map((season) => new SeasonEditor(season.id, season)) : [this.blankSeason(today)],
    );
    this.touch();
  }

  /** O horario está ben: cada tempada co nome, as datas e as horas, e sen pisarse. */
  scheduleOk(): boolean {
    const list = this.seasons();
    return list.length > 0 && list.every((season) => season.ok() && !overlapOf(list, season));
  }

  /** A tempada anterior coa que pisa as datas desta, se a hai. */
  overlapOf(season: SeasonEditor): SeasonEditor | null {
    return overlapOf(this.seasons(), season);
  }

  /** «Engadir tempada»: sen nome nin datas, co horario da última. */
  addSeason(): SeasonEditor {
    const season = new SeasonEditor(
      `new-${this.nextSeason++}`,
      { name: '', startDate: '', endDate: null, week: sameWeek(DEFAULT_HOURS) },
      { added: true, hasEnd: true },
    );
    const last = this.seasons().at(-1);
    if (last) season.copyHours(last);
    this.setSeasons([...this.seasons(), season]);
    this.touch();
    return season;
  }

  /** Quita unha tempada: unha nova desaparece; unha gardada bórrase ao gardar. */
  removeSeason(season: SeasonEditor): void {
    if (season.id) this.removedSeasons.update((ids) => new Set(ids).add(season.id as string));
    this.setSeasons(this.seasons().filter((other) => other !== season));
    this.touch();
  }

  /** Xa se borrou na API. */
  forgetRemoved(id: string): void {
    this.removedSeasons.update((ids) => new Set([...ids].filter((other) => other !== id)));
  }

  /** «Empezar co horario de» outra tempada. */
  copySeasonHours(season: SeasonEditor, from: SeasonEditor): void {
    season.copyHours(from);
    this.touch();
  }

  /** Marca os erros de todas as tempadas (ao «Seguinte»). */
  touchSeasons(): void {
    for (const season of this.seasons()) season.touchAll();
  }

  /** O horario xa se gardou tal como está. */
  markSchedulePristine(): void {
    for (const season of this.seasons()) season.markPristine();
  }

  get scheduleDirty(): boolean {
    return this.removedSeasons().size > 0 || this.seasons().some((season) => season.dirty);
  }

  /** Hai algo sen gardar: o mesmo que mira `WizardSaver` para gardar cada paso. */
  dirty(): boolean {
    return (
      [this.name, this.data, this.description, this.location, this.amenities, this.houseRules, this.payment].some(
        (form) => form.dirty,
      ) ||
      this.photosDirty() ||
      this.scheduleDirty
    );
  }

  /** A primeira tempada dun horario baleiro: sen nome, desde o 1 de xaneiro e sen fin. */
  private blankSeason(today: IsoDate): SeasonEditor {
    return new SeasonEditor(`new-${this.nextSeason++}`, {
      name: '',
      startDate: wholeYear(today).startDate,
      endDate: null,
      week: sameWeek(DEFAULT_HOURS),
    });
  }

  /** Cambia a lista: as comprobacións seguen os formularios das que quedan. */
  private setSeasons(list: readonly SeasonEditor[]): void {
    for (const [key, subscription] of this.seasonEvents) {
      if (!list.some((season) => season.key === key)) {
        subscription.unsubscribe();
        this.seasonEvents.delete(key);
      }
    }
    for (const season of list) {
      if (!this.seasonEvents.has(season.key)) {
        this.seasonEvents.set(
          season.key,
          season.all.events.subscribe(() => this.touch()),
        );
      }
    }
    this.seasons.set(list);
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
