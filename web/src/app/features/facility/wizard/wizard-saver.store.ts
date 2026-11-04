// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Api } from '../../../api/api';
import { createFacility } from '../../../api/fn/facilities/create-facility';
import { publishFacility } from '../../../api/fn/facilities/publish-facility';
import { renameFacility } from '../../../api/fn/facilities/rename-facility';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { customFieldValuesOf } from '../../../shared/custom-fields/custom-field-controls';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { amenitiesBody } from '../amenities/amenities';
import { AmenitiesStore } from '../amenities/amenities.store';
import { DataCardStore } from '../data-card/data-card.store';
import { detailsBody } from '../data-card/data-form';
import { descriptionBody } from '../description/description-form';
import { DescriptionStore } from '../description/description.store';
import { PaymentNotesStore } from '../description/payment-notes.store';
import type { Facility } from '../detail/facility-detail.store';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { locationBody } from '../location/location-form';
import { LocationStore } from '../location/location.store';
import { PhotosStore } from '../photos/photos.store';
import { ScheduleStore } from '../schedule/schedule.store';
import { UsesStore } from '../uses/uses.store';
import type { StepKey } from './wizard';
import { wholeYear } from './wizard';
import { WizardForms } from './wizard-forms';

/**
 * Gardado do asistente «Nova instalación»: cada paso, nas mesmas peticións ca a ficha e co
 * `If-Match` da súa cola (`FacilityDetailStore`); só o que cambiou. O primeiro gardado crea a
 * instalación (sen publicar). Un por páxina.
 */
@Injectable()
export class WizardSaver {
  private readonly api = inject(Api);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly forms = inject(WizardForms);
  private readonly detail = inject(FacilityDetailStore);
  private readonly uses = inject(UsesStore);
  private readonly schedule = inject(ScheduleStore);
  private readonly paymentNotes = inject(PaymentNotesStore);
  private readonly dataStore = inject(DataCardStore);
  private readonly descriptionStore = inject(DescriptionStore);
  private readonly locationStore = inject(LocationStore);
  private readonly amenitiesStore = inject(AmenitiesStore);
  private readonly photosStore = inject(PhotosStore);
  /** Subidas aínda sen gardar en ningunha instalación. */
  private readonly fresh = new Set<string>();

  /** Garda un paso. Con `partial` (borrador), os Datos só se os deportes están ben. */
  async save(key: StepKey, partial = false): Promise<void> {
    const f = this.forms;
    switch (key) {
      case 'data':
        await this.saveIdentity(partial);
        break;
      case 'location':
        if (f.location.dirty) {
          const body = locationBody(f.location.getRawValue());
          await this.detail.saveFacility((target) => this.locationStore.update(target, body));
          f.location.markAsPristine();
        }
        break;
      case 'contact':
        await this.saveDetails();
        if (f.amenities.dirty) {
          const body = amenitiesBody(f.amenities.getRawValue());
          await this.detail.saveFacility((target) => this.amenitiesStore.update(target, body));
          f.amenities.markAsPristine();
        }
        break;
      case 'photos':
        await this.savePhotos();
        break;
      case 'uses':
        await this.saveDetails();
        break;
      case 'schedule':
        await this.saveSchedule();
        break;
      case 'rules':
        await this.saveRules();
        break;
      case 'review':
        break;
    }
  }

  publish(): Promise<Facility> {
    return this.detail.saveFacility(({ slug, id, ifMatch }) =>
      this.api.invoke(publishFacility, { slug, id, 'If-Match': ifMatch }),
    );
  }

  /** Unha foto subida neste asistente: se non se garda, descártase. */
  uploaded(photoId: string): void {
    this.fresh.add(photoId);
  }

  /** Quitar unha subida sen gardar (sen agardar: se falla, a API borra a pendente ao día). */
  discardUpload(photoId: string): void {
    if (!this.fresh.delete(photoId)) return;
    void this.photosStore.discard(this.slug(), photoId).catch(() => undefined);
  }

  /** Nome (crea a instalación a primeira vez), descrición e Datos. */
  private async saveIdentity(partial: boolean): Promise<void> {
    const f = this.forms;
    const slug = this.slug();
    const name = f.name.value.trim();
    if (!this.detail.facility()) {
      const created = await this.api.invoke(createFacility, { slug, body: { name } });
      this.detail.adopt(slug, created);
      await Promise.all([this.uses.load(slug, created.id), this.schedule.load(slug, created.id)]);
      f.name.markAsPristine();
    } else if (f.name.dirty) {
      await this.detail.saveFacility(({ id, ifMatch }) =>
        this.api.invoke(renameFacility, { slug, id, 'If-Match': ifMatch, body: { name } }),
      );
      f.name.markAsPristine();
    }
    if (f.description.dirty && f.description.valid) {
      const body = descriptionBody(f.description.getRawValue());
      await this.detail.saveFacility((target) => this.descriptionStore.update(target, body));
      f.description.markAsPristine();
    }
    if (!partial || f.data.controls.sports.valid) await this.saveDetails();
  }

  /**
   * A tarxeta Datos vai enteira (pasos 1, 3 e 5). Só se está toda ben: se un campo propio
   * obrigatorio aínda está baleiro, agarda ao paso 3.
   */
  private async saveDetails(): Promise<void> {
    const f = this.forms;
    if (!f.data.dirty || !f.data.valid) return;
    const definitions = f.definitions();
    const others = Object.fromEntries(
      Object.entries(this.detail.facility()?.details.customFields ?? {}).filter(
        ([code]) => !definitions.some((field) => field.code === code),
      ),
    );
    const body = detailsBody(f.data.getRawValue(), {
      ...others,
      ...customFieldValuesOf(definitions, f.data.controls.customFields),
    });
    await this.detail.saveFacility((target) => this.dataStore.update(target, body));
    f.data.markAsPristine();
  }

  /** As fotos coa súa descrición; sen fotos, o debuxo escollido (o suxerido, lista baleira). */
  private async savePhotos(): Promise<void> {
    const f = this.forms;
    if (!f.photosDirty()) return;
    const photos = f.images();
    const drawing = f.drawing();
    const body = photos.length
      ? {
          images: photos.map((image) => ({
            photoId: image.id,
            alt: { gl: image.alt.gl.trim(), es: image.alt.es.trim() },
          })),
        }
      : { images: drawing ? [{ drawing }] : [] };
    await this.detail.saveFacility((target) => this.photosStore.arrange(target, body));
    f.photosDirty.set(false);
    for (const image of photos) this.fresh.delete(image.id);
  }

  /** «Todo o ano» (créase a primeira vez) coa semana do paso. */
  private async saveSchedule(): Promise<void> {
    const f = this.forms;
    let season = this.schedule.seasons()[0] ?? null;
    if (season && !f.scheduleDirty) return;
    if (!season) {
      const dates = wholeYear(this.schedule.today() ?? madridToday());
      const name = this.transloco.translate('facility.wizard.schedule.wholeYear');
      season = await this.schedule.create({ name, ...dates });
    }
    await this.schedule.update(season.id, {
      name: season.name,
      startDate: season.startDate,
      endDate: season.endDate ?? undefined,
      week: f.weekBody().map((row) => ({ day: row.day, intervals: [...row.intervals] })),
    });
    f.markSchedulePristine();
    // O que falta para saír de borrador vén da API: tras o horario, vólvese ler.
    await this.detail.reload();
  }

  private async saveRules(): Promise<void> {
    const f = this.forms;
    if (f.houseRules.dirty) {
      const body = descriptionBody(f.houseRules.getRawValue());
      await this.detail.saveFacility((target) => this.descriptionStore.updateHouseRules(target, body));
      f.houseRules.markAsPristine();
    }
    if (f.scope() === 'FACILITY' && f.payment.dirty) {
      const { payment, refund } = f.payment.getRawValue();
      const body = { payment: descriptionBody(payment), refund: descriptionBody(refund) };
      await this.detail.saveFacility((target) => this.paymentNotes.update(target, body));
      f.payment.markAsPristine();
    }
  }

  private slug(): string {
    const slug = this.municipality.slug();
    if (!slug) throw new Error('Sen concello');
    return slug;
  }
}
