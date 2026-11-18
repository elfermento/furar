// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
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
 * Sección «Servizos e accesibilidade» da ficha: fóra da edición, a lista do que ofrece a
 * instalación coa súa icona; no modo edición da ficha, un interruptor por elemento do catálogo
 * nun `fieldset` por grupo. Garda a lista enteira pola cola da ficha (`If-Match` da última versión)
 * cando a barra garda.
 */
@Component({
  selector: 'furar-facility-amenities',
  imports: [EditSectionComponent, IconComponent, ReactiveFormsModule, SwitchComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Opcional: sen servizos, fóra da edición non se ve a sección.
  host: { '[attr.hidden]': '!session.editing() && !offered().length ? "" : null' },
  template: `
    @let t = 'facility.amenities.';
    <furar-edit-section [formGroup]="form" [heading]="t + 'title' | transloco" [save]="save">
      @if (session.editing()) {
        <div class="gap-form grid">
          @for (group of groups; track group.key) {
            <!-- Un interruptor por elemento, coma no paso «Contacto e servizos» do asistente. -->
            <fieldset class="grid" [attr.data-group]="group.key">
              <legend class="mb-unit font-bold">{{ t + 'group.' + group.key | transloco }}</legend>
              @for (item of group.items; track item) {
                <furar-switch [formControlName]="item" [label]="t + 'item.' + item | transloco" />
              }
            </fieldset>
          }
        </div>
      } @else {
        <ul class="gap-x-item gap-y-unit grid sm:grid-cols-2" [attr.aria-label]="t + 'offers' | transloco">
          @for (item of offered(); track item) {
            <li class="gap-unit flex items-center">
              <furar-icon [name]="iconOf(item)" [size]="24" class="flex-none text-primary-text" />
              {{ t + 'item.' + item | transloco }}
            </li>
          }
        </ul>
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
