// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FacilityCardComponent } from '../../../../shared/facility-card/facility-card.component';
import { CarouselComponent, CarouselItemDirective } from '../../../../shared/ui/carousel/carousel.component';
import type { FacilityFreeSlotsResponse } from '../list/public-list.store';
import type { CatalogFacility } from './common-home';
import { railsByMunicipality } from './common-home';

/**
 * A portada común sen filtros: un carrusel por concello (`furar-facility-card`, as tarxetas da
 * portada do concello), unha fila cada un. O botón «Mapa» e o mapa van na páxina.
 */
@Component({
  selector: 'furar-catalog-rails',
  imports: [CarouselComponent, CarouselItemDirective, FacilityCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @for (rail of rails(); track rail.municipality.slug) {
      @let slug = rail.municipality.slug;
      <section class="f-home__rail" [attr.aria-labelledby]="'rail-' + slug" [attr.data-rail]="slug">
        <h3 class="f-home__heading" [id]="'rail-' + slug">{{ rail.municipality.name }}</h3>
        <furar-carousel
          class="f-rail"
          scrollFocusable
          [label]="rail.municipality.name"
          [items]="rail.facilities"
          [key]="key"
        >
          <ng-template furarCarouselItem let-facility>
            <furar-facility-card
              street
              [slug]="slug"
              [facility]="facility"
              [slots]="slots().get(facility.id) ?? null"
              [date]="date()"
              [day]="day()"
            />
          </ng-template>
        </furar-carousel>
      </section>
    }
  `,
})
export class CatalogRailsComponent {
  readonly facilities = input.required<readonly CatalogFacility[]>();
  /** Os ocos do día por instalación. */
  readonly slots = input.required<ReadonlyMap<string, FacilityFreeSlotsResponse>>();
  /** O día dos ocos (ISO; as horas levan a reservar nel) e o seu texto («hoxe», «mañá», «xov 29»). */
  readonly date = input.required<string>();
  readonly day = input.required<string>();

  /** Un carrusel por concello, na orde na que veñen. */
  protected readonly rails = computed(() => railsByMunicipality(this.facilities()));
  protected readonly key = (facility: CatalogFacility) => facility.id;
}
