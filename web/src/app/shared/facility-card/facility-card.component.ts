// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { MediaCardComponent } from '../ui/media-card/media-card.component';
import { TagComponent } from '../ui/tag/tag.component';
import type { SearchFacility, SearchSport } from '../facility-search/facility-search';
import { addressLine, placeLine, sportKey, sportName } from '../facility-search/facility-search';
import type { FreeSlots } from './facility-card';
import { formatDistance, slotTime } from './facility-card';

/** O que pinta a tarxeta dunha instalación (a forma de `PublicFacilityResponse`). */
export interface FacilityCardData extends SearchFacility {
  readonly cover: { readonly thumb: string; readonly alt: { readonly gl: string; readonly es: string } };
  readonly temporarilyClosed: boolean;
}

/**
 * Tarxeta dunha instalación (`furar-media-card`), a mesma en todas partes: os carruseis das
 * portadas, os resultados, a folla do mapa e o listado dun concello. Imaxe de portada co chip «N
 * ocos hoxe» (ou do día escollido) se lle quedan, nome, lugar e os deportes en etiquetas `soft`;
 * abaixo, as tres primeiras horas libres, cada unha unha ligazón a reservar con esa hora
 * (`…/reservar?data=&hora=`, e `&deporte=` cos deportes do filtro). O resto leva á ficha.
 *
 * O lugar leva o concello diante («Narón · Rúa Buxo, 12») se a instalación o trae, salvo con
 * `street` (nun carrusel que xa ten o concello por título). Con «Preto de min», a distancia (`km`).
 */
@Component({
  selector: 'furar-facility-card',
  imports: [MediaCardComponent, RouterLink, TagComponent, TranslocoPipe],
  host: { class: 'contents' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let f = facility();
    <furar-media-card
      [src]="f.cover.thumb"
      [alt]="lang() === 'es' ? f.cover.alt.es : f.cover.alt.gl"
      [heading]="f.name"
      [headingLevel]="headingLevel()"
      [place]="address()"
      [distance]="km() === null ? '' : ('facility.catalog.distance' | transloco: { distance: distance() })"
      [link]="['/', slug(), 'instalacions', f.id]"
      [linkParams]="sportParams()"
    >
      @if (free(); as free) {
        <furar-tag cardBadge tone="success">{{
          (free.freeCount === 1 ? 'home.facilities.freeOne' : 'home.facilities.freeOther')
            | transloco: { count: free.freeCount, day: day() }
        }}</furar-tag>
      }
      @if (f.temporarilyClosed) {
        <p>
          <furar-tag tone="muted">{{ 'home.facilities.closed' | transloco }}</furar-tag>
        </p>
      }
      @if (f.sports.length) {
        <ul class="f-tag-list" [attr.aria-label]="'home.facilities.sports' | transloco">
          @for (sport of f.sports; track key(sport)) {
            <li>
              <furar-tag look="soft">{{ name(sport) }}</furar-tag>
            </li>
          }
        </ul>
      }
      @if (times().length) {
        <ul class="f-slot-links" cardActions [attr.aria-label]="'facility.publicList.times' | transloco">
          @for (time of times(); track time) {
            <li>
              <a
                class="f-slot-links__link"
                [routerLink]="['/', slug(), 'instalacions', f.id, 'reservar']"
                [queryParams]="{ data: date(), hora: time, deporte: sportParams()?.deporte ?? null }"
                >{{ time }}</a
              >
            </li>
          }
        </ul>
      }
    </furar-media-card>
  `,
})
export class FacilityCardComponent {
  readonly slug = input.required<string>();
  /** Na portada común trae o seu concello, que vai diante do enderezo. */
  readonly facility = input.required<FacilityCardData>();
  /** Ocos do día (sen eles, a instalación non se reserva por internet ou non se puideron ler). */
  readonly slots = input<FreeSlots | null>(null);
  /** Día dos ocos (ISO) e o seu texto no chip («hoxe», «mañá», «xov 29»). */
  readonly date = input.required<string>();
  readonly day = input.required<string>();
  /** «Preto de min»: a distancia (km) a quen mira, «a 1,2 km» diante do lugar. */
  readonly km = input<number | null>(null);
  /** Os deportes do filtro: a ficha e as horas lévanos (`?deporte=`) para reservar nun uso deles. */
  readonly sports = input<readonly string[]>([]);
  /** Nivel do título: 2 se a lista vai directamente baixo o `h1` da páxina (sen saltar niveis). */
  readonly headingLevel = input<2 | 3>(3);
  /** Só a rúa, sen o concello (un carrusel co concello por título). */
  readonly street = input(false, { transform: booleanAttribute });

  protected readonly lang = inject(LanguageService).lang;
  protected readonly key = sportKey;
  protected readonly distance = computed(() => formatDistance(this.km() ?? 0, this.lang()));
  protected readonly address = computed(() =>
    this.street() ? addressLine(this.facility()) : placeLine(this.facility()),
  );
  /** Os ocos, só se queda algún. */
  protected readonly free = computed(() => {
    const slots = this.slots();
    return slots && slots.freeCount > 0 ? slots : null;
  });
  protected readonly sportParams = computed(() => {
    const sports = this.sports();
    return sports.length ? { deporte: [...sports] } : null;
  });
  /** As tres primeiras horas libres (se non caben nunha liña, baixan; nunca máis de tres). */
  protected readonly times = computed(() =>
    (this.free()?.firstStarts ?? []).slice(0, 3).map((s) => slotTime(s.startMinute)),
  );

  protected name(sport: SearchSport): string {
    return sportName(sport, this.lang());
  }
}
