// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { IsoDate } from '../ui/calendar/local-date';
import { inMadrid } from '../ui/calendar/madrid-time';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { eventWhen } from '../events/event-format';
import { CarouselComponent, CarouselItemDirective } from '../ui/carousel/carousel.component';
import { MediaCardComponent } from '../ui/media-card/media-card.component';
import { SeatsChipComponent } from '../ui/seats-chip/seats-chip.component';
import { TagComponent } from '../ui/tag/tag.component';
import { openMatchCounts, openMatchTitle, openMatchUseDetail } from './open-match-format';
import type { OpenMatchCardResponse } from './open-matches.store';
import { OpenMatches } from './open-matches.store';

/** Unha partida xa calculada para pintar: a mesma nas portadas e na ficha. */
interface Card {
  readonly id: string;
  readonly link: readonly string[];
  readonly name: string;
  readonly src: string;
  readonly alt: string;
  readonly when: string;
  readonly place: string;
  /** O deporte (se o título fai de nome) e o nivel. */
  readonly tags: readonly string[];
  /** Prazas libres (o chip común das prazas). */
  readonly free: number;
}

/** Unha partida cos datos do seu concello (na portada común veñen de varios). */
interface Entry {
  readonly match: OpenMatchCardResponse;
  readonly slug: string;
  readonly municipality: string | null;
}

/**
 * «Buscan xente para xogar» (ADR-0040). Sen partidas (ou se a lectura falla), a
 * sección non existe. As prazas libres van sempre no mesmo chip (`furar-seats-chip`); quen organiza
 * vese só na páxina da partida.
 *
 * - Portadas (`variant="home"`; co `slug`, as dun concello; sen el, as de todos na portada común):
 *   o carrusel de «Instalacións» e «Eventos» coa mesma `furar-media-card`: imaxe, día e hora, a
 *   instalación (e o concello na portada común), título, o deporte e o nivel en etiquetas `soft` e,
 *   no pé, as prazas.
 * - Ficha da instalación (`[slug]` e `[facilityId]`, `variant="facility"`): filas coma as de «Que se
 *   pode reservar»: o nome (ligazón á partida), o día e a hora co nivel e, á dereita, as prazas.
 */
@Component({
  selector: 'furar-open-matches',
  imports: [
    CarouselComponent,
    CarouselItemDirective,
    MediaCardComponent,
    RouterLink,
    SeatsChipComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (cards().length) {
      @if (variant() === 'home') {
        <section class="f-wrap f-home__section" [attr.aria-labelledby]="headingId()" data-open-matches>
          <h2 [id]="headingId()" class="f-home__heading">
            {{ 'openMatch.section' | transloco }}
            @if (counted()) {
              <span class="f-home__count">· {{ cards().length }}</span>
            }
          </h2>
          <furar-carousel
            class="f-rail"
            scrollFocusable
            [label]="'openMatch.section' | transloco"
            [items]="cards()"
            [key]="cardKey"
          >
            <ng-template furarCarouselItem let-card>
              <furar-media-card
                [src]="card.src"
                [alt]="card.alt"
                [heading]="card.name"
                [when]="card.when"
                [place]="card.place"
                [link]="card.link"
                [attr.data-open-match]="card.id"
              >
                @if (card.tags.length) {
                  <ul class="f-tag-list" [attr.aria-label]="'openMatch.tags' | transloco">
                    @for (tag of card.tags; track tag) {
                      <li>
                        <furar-tag look="soft">{{ tag }}</furar-tag>
                      </li>
                    }
                  </ul>
                }
                <span cardFoot><furar-seats-chip [free]="card.free" /></span>
              </furar-media-card>
            </ng-template>
          </furar-carousel>
        </section>
      } @else {
        <section class="f-public__section" [attr.aria-labelledby]="headingId()" data-open-matches>
          <h2 [id]="headingId()">{{ 'openMatch.section' | transloco }}</h2>
          <ul class="f-public__uses">
            @for (card of cards(); track card.id) {
              <li [attr.data-open-match]="card.id">
                <div class="min-w-0">
                  <h3>
                    <a class="f-public-card__link" [routerLink]="card.link">{{ card.name }}</a>
                  </h3>
                  <p class="text-ink-soft">{{ card.when }}</p>
                </div>
                <furar-seats-chip [free]="card.free" />
              </li>
            }
          </ul>
        </section>
      }
    }
  `,
})
export class OpenMatchesComponent {
  private readonly store = inject(OpenMatches);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  /** O concello; sen el, as de todos (portada común). */
  readonly slug = input<string | null>(null);
  /** Só as das instalacións con estes ids (as que pasan os filtros da portada); `null`, todas. */
  readonly facilityIds = input<ReadonlySet<string> | null>(null);
  /** Só as deste concello (o seu slug; o escollido no buscador da portada común); `null`, todas. */
  readonly only = input<string | null>(null);
  /** Só as dese día; `null`, todas as próximas. */
  readonly day = input<IsoDate | null>(null);
  /** Cun filtro posto: o título leva cantas hai. */
  readonly counted = input(false);
  /** Só as dunha instalación (a súa ficha). */
  readonly facilityId = input<string | null>(null);
  /** `home`: carrusel da portada; `facility`: filas da ficha. */
  readonly variant = input<'home' | 'facility'>('home');
  /** Tras cada lectura: se hai algo que mostrar (a portada ofrece entón velo na folla «Filtros»). */
  readonly found = output<boolean>();

  protected readonly headingId = computed(() => (this.facilityId() ? 'facility-open-matches' : 'open-matches'));
  protected readonly cardKey = (card: Card) => card.id;
  private readonly entries = signal<readonly Entry[]>([]);

  protected readonly cards = computed<Card[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    const t = (key: string) => this.transloco.translate(key);
    const inRow = this.variant() === 'facility';
    const [ids, day, only] = [this.facilityIds(), this.day(), this.only()];
    const entries = this.entries().filter(
      ({ match, slug }) =>
        (!only || slug === only) && (!ids || ids.has(match.facilityId)) && (!day || inMadrid(match.start).date === day),
    );
    return entries.map(({ match, slug, municipality }) => {
      const level = match.level ? t(`openMatch.level.${match.level}`) : null;
      const when = eventWhen(match.start, lang, true);
      return {
        id: match.id,
        link: ['/', slug, 'partidas', match.id],
        name: openMatchTitle(match),
        src: match.cover?.thumb ?? '',
        alt: match.cover ? (lang === 'es' ? match.cover.alt.es : match.cover.alt.gl) : '',
        // Na fila da ficha, o nivel vai co día e a hora.
        when: inRow && level ? `${when} · ${level}` : when,
        place: [match.facilityName, municipality].filter((part): part is string => !!part).join(' · '),
        tags: [openMatchUseDetail(match), level].filter((tag): tag is string => !!tag),
        free: openMatchCounts(match).free,
      };
    });
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      const facilityId = this.facilityId();
      untracked(() => void this.load(slug, facilityId));
    });
  }

  private async load(slug: string | null, facilityId: string | null): Promise<void> {
    try {
      if (slug) {
        const items = await this.store.list(slug, facilityId ?? undefined);
        this.entries.set(items.map((match) => ({ match, slug, municipality: null })));
      } else {
        const catalog = await this.store.catalog();
        this.entries.set(
          catalog.municipalities.flatMap((m) =>
            m.items.map((match) => ({ match, slug: m.slug, municipality: m.name })),
          ),
        );
      }
    } catch {
      this.entries.set([]);
    }
    this.found.emit(this.entries().length > 0);
  }
}
