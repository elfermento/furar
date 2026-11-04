// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { MediaCardComponent } from '../../../shared/ui/media-card/media-card.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { HomeNavigation } from './home-navigation';
import { addressLine, sportKey, sportName } from '../../../shared/facility-search/facility-search';
import type { PublicFacilityResponse } from './home.store';

/**
 * Tarxeta dunha instalación na portada (`furar-media-card`): imaxe de portada (a primeira da súa
 * lista, foto ou debuxo), «N ocos hoxe» enriba se lle quedan, nome, enderezo e os deportes en
 * etiquetas `soft`. Toda a tarxeta leva á ficha pública (HomeNavigation).
 */
@Component({
  selector: 'furar-facility-card',
  imports: [MediaCardComponent, TagComponent, TranslocoPipe],
  host: { class: 'contents' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let cover = facility().cover;
    <furar-media-card
      [src]="cover.thumb"
      [alt]="lang() === 'es' ? cover.alt.es : cover.alt.gl"
      [heading]="facility().name"
      [place]="address()"
      [link]="link()"
    >
      @if (freeToday() > 0) {
        <furar-tag cardBadge tone="success">{{
          (freeToday() === 1 ? 'home.facilities.freeOne' : 'home.facilities.freeOther')
            | transloco: { count: freeToday() }
        }}</furar-tag>
      }
      @if (facility().temporarilyClosed) {
        <p>
          <furar-tag tone="muted">{{ 'home.facilities.closed' | transloco }}</furar-tag>
        </p>
      }
      @if (facility().sports.length) {
        <ul class="f-tag-list" [attr.aria-label]="'home.facilities.sports' | transloco">
          @for (sport of facility().sports; track key(sport)) {
            <li>
              <furar-tag look="soft">{{ name(sport) }}</furar-tag>
            </li>
          }
        </ul>
      }
    </furar-media-card>
  `,
})
export class FacilityCardComponent {
  private readonly navigation = inject(HomeNavigation);
  private readonly language = inject(LanguageService);

  readonly slug = input.required<string>();
  readonly facility = input.required<PublicFacilityResponse>();
  /** Ocos libres hoxe (0: sen chip). */
  readonly freeToday = input(0);

  protected readonly lang = this.language.lang;
  protected readonly link = computed(() => this.navigation.facilityLink(this.slug(), this.facility().id));
  protected readonly address = computed(() => addressLine(this.facility()));
  protected readonly key = sportKey;

  protected name(sport: PublicFacilityResponse['sports'][number]): string {
    return sportName(sport, this.lang());
  }
}
