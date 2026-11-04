// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { EditSectionComponent } from '../../../shared/ui/edit-section/edit-section.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { syncWhenPristine } from '../detail/section-form';
import type { AmenitiesValue } from './amenities';
import { AMENITIES, AMENITY_GROUPS, amenitiesBody, amenitiesForm, amenityIcon, offeredAmenities } from './amenities';
import { AmenitiesStore } from './amenities.store';

/**
 * Sección «Servizos e accesibilidade» da ficha (D1-21b): fóra da edición, a lista do que ofrece a
 * instalación coa súa icona; no modo edición da ficha, unha casilla por elemento do catálogo nun
 * `fieldset` por grupo. Garda a lista enteira pola cola da ficha (`If-Match` da última versión)
 * cando a barra garda.
 */
@Component({
  selector: 'furar-facility-amenities',
  imports: [EditSectionComponent, IconComponent, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.amenities.';
    <furar-edit-section [formGroup]="form" [heading]="t + 'title' | transloco" [save]="save">
      @if (session.editing()) {
        @for (group of groups; track group.key) {
          <fieldset class="gap-unit grid" [attr.data-group]="group.key">
            <legend class="mb-unit font-bold">{{ t + 'group.' + group.key | transloco }}</legend>
            <div class="gap-x-item grid sm:grid-cols-2">
              @for (item of group.items; track item) {
                <label class="gap-unit flex min-h-11 cursor-pointer items-center">
                  <input type="checkbox" class="size-5 flex-none" [formControlName]="item" />
                  {{ t + 'item.' + item | transloco }}
                </label>
              }
            </div>
          </fieldset>
        }
      } @else if (offered().length) {
        <ul class="gap-x-item gap-y-unit grid sm:grid-cols-2" [attr.aria-label]="t + 'offers' | transloco">
          @for (item of offered(); track item) {
            <li class="gap-unit flex items-center">
              <furar-icon [name]="iconOf(item)" [size]="24" class="flex-none text-primary-text" />
              {{ t + 'item.' + item | transloco }}
            </li>
          }
        </ul>
      } @else {
        <p class="text-ink-soft">{{ t + 'empty' | transloco }}</p>
      }
    </furar-edit-section>
  `,
})
export class AmenitiesSectionComponent {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(AmenitiesStore);
  protected readonly session = inject(EditSession);

  protected readonly groups = AMENITY_GROUPS;
  protected readonly form = amenitiesForm([]);
  protected readonly offered = computed(() => offeredAmenities(this.detail.facility()));
  protected readonly iconOf = amenityIcon;

  protected readonly save = () =>
    this.detail.saveFacility((target) => this.store.update(target, amenitiesBody(this.form.getRawValue())));

  constructor() {
    syncWhenPristine(this.form, () => {
      const facility = this.detail.facility();
      if (!facility) return null;
      const selected = new Set(offeredAmenities(facility));
      return Object.fromEntries(AMENITIES.map((item) => [item, selected.has(item)])) as AmenitiesValue;
    });
  }
}
