// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Language } from '../../../../core/i18n/languages';
import { addressLine, sportKey, sportName } from '../../../../shared/facility-search/facility-search';
import { MediaCardComponent } from '../../../../shared/ui/media-card/media-card.component';
import { TagComponent } from '../../../../shared/ui/tag/tag.component';
import { slotTime } from './public-list';
import type { FacilityFreeSlotsResponse, PublicFacilityResponse } from './public-list.store';

/**
 * Tarxeta dunha instalación no listado público (maqueta 16): a mesma `furar-media-card` da
 * portada, co chip «N ocos hoxe» enriba da imaxe e as tres primeiras horas libres, cada unha unha
 * ligazón á páxina de reservar con esa hora (`…/reservar?data=&hora=`). Sen ocos (ou se non se
 * reserva por internet), nin chip nin horas. O resto da tarxeta leva á ficha pública.
 */
@Component({
  selector: 'furar-public-list-card',
  imports: [MediaCardComponent, RouterLink, TagComponent, TranslocoPipe],
  host: { class: 'contents' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.publicList.';
    @let f = facility();
    <furar-media-card
      [src]="f.cover.thumb"
      [alt]="lang() === 'es' ? f.cover.alt.es : f.cover.alt.gl"
      [heading]="f.name"
      [place]="address()"
      [link]="['/', slug(), 'instalacions', f.id]"
    >
      @if (free(); as free) {
        <furar-tag cardBadge tone="success">{{
          t + (free.freeCount === 1 ? 'freeOne' : 'freeOther') | transloco: { count: free.freeCount, day: day() }
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
              <furar-tag look="soft">{{ sportLabel(sport) }}</furar-tag>
            </li>
          }
        </ul>
      }
      @if (times().length) {
        <ul class="f-slot-links" cardActions [attr.aria-label]="t + 'times' | transloco">
          @for (time of times(); track time) {
            <li>
              <a
                class="f-slot-links__link"
                [routerLink]="['/', slug(), 'instalacions', f.id, 'reservar']"
                [queryParams]="{ data: date(), hora: time }"
                >{{ time }}</a
              >
            </li>
          }
        </ul>
      }
    </furar-media-card>
  `,
})
export class PublicListCardComponent {
  readonly slug = input.required<string>();
  readonly facility = input.required<PublicFacilityResponse>();
  /** Ocos do día (sen eles, a instalación non se reserva por internet ou non se puideron ler). */
  readonly slots = input<FacilityFreeSlotsResponse | null>(null);
  /** Día dos ocos (ISO) e o seu texto no chip («hoxe», «mañá», «xov 29»). */
  readonly date = input.required<string>();
  readonly day = input.required<string>();
  readonly lang = input.required<Language>();

  protected readonly key = sportKey;
  protected readonly address = computed(() => addressLine(this.facility()));
  /** Os ocos, só se queda algún. */
  protected readonly free = computed(() => {
    const slots = this.slots();
    return slots && slots.freeCount > 0 ? slots : null;
  });
  protected readonly times = computed(() =>
    (this.free()?.firstStarts ?? []).slice(0, 3).map((s) => slotTime(s.startMinute)),
  );

  protected sportLabel(sport: PublicFacilityResponse['sports'][number]): string {
    return sportName(sport, this.lang());
  }
}
