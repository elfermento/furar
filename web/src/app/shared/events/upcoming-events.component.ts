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
import type { IsoDate } from '../ui/calendar/local-date';
import { inMadrid } from '../ui/calendar/madrid-time';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { CarouselComponent, CarouselItemDirective } from '../ui/carousel/carousel.component';
import { MediaCardComponent } from '../ui/media-card/media-card.component';
import { SeatsChipComponent } from '../ui/seats-chip/seats-chip.component';
import { activityDates, dayMonthTime, inLang, isScheduled, scheduleWhen } from './event-format';
import type { PublicEventResponse } from './public-events.store';
import { PublicEvents } from './public-events.store';

/** Un evento ou unha actividade xa calculados para a tarxeta. */
interface Card {
  readonly id: string;
  readonly src: string;
  readonly alt: string;
  readonly name: string;
  readonly when: string;
  /** As datas dunha actividade; baleiro nun evento dun día. */
  readonly period: string;
  readonly place: string;
  readonly free: number | null;
  /** O concello do evento (a ligazón vai á súa ficha nel). */
  readonly slug: string;
}

/** Un concello da portada común. */
export interface EventsMunicipality {
  readonly slug: string;
  readonly name: string;
}

/** Un evento co seu concello (o nome, só na portada común). */
interface Entry {
  readonly event: PublicEventResponse;
  readonly slug: string;
  readonly municipality: string | null;
}

/**
 * «Eventos e actividades» na portada do concello (ADR-0038, ADR-0041): a mesma tarxeta
 * (`furar-media-card`) e o mesmo carrusel ca «Instalacións». Un evento dun día leva o día e a hora
 * («20 de decembro · 11:00»); unha actividade, o horario resumido («Luns e mércores · 17:00») e as
 * datas. No pé, en todas, o chip común das prazas (`furar-seats-chip`; sen límite, ningún). A
 * tarxeta leva á ficha. Sen nada publicado (ou se a lectura falla), a sección non se pinta.
 *
 * Na portada común (`municipalities`, sen `slug`), os de todos eses concellos nun só carrusel, do
 * que empeza antes ao que empeza despois, co concello despois do lugar («Pavillón · Verín»).
 */
@Component({
  selector: 'furar-upcoming-events',
  imports: [CarouselComponent, CarouselItemDirective, MediaCardComponent, SeatsChipComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (cards().length) {
      <section class="f-wrap f-home__section" aria-labelledby="eventos" data-home-events>
        <h2 id="eventos" class="f-home__heading">
          {{ 'event.public.upcoming' | transloco }}
          @if (counted()) {
            <span class="f-home__count">· {{ cards().length }}</span>
          }
        </h2>
        <furar-carousel
          class="f-rail"
          scrollFocusable
          [label]="'event.public.upcoming' | transloco"
          [items]="cards()"
          [key]="key"
        >
          <ng-template furarCarouselItem let-card>
            <furar-media-card
              [src]="card.src"
              [alt]="card.alt"
              [heading]="card.name"
              [when]="card.when"
              [period]="card.period"
              [place]="card.place"
              [link]="['/', card.slug, 'eventos', card.id]"
              [attr.data-event-card]="card.id"
            >
              @if (card.free !== null) {
                <span cardFoot><furar-seats-chip [free]="card.free" /></span>
              }
            </furar-media-card>
          </ng-template>
        </furar-carousel>
      </section>
    }
  `,
})
export class UpcomingEventsComponent {
  private readonly store = inject(PublicEvents);
  private readonly language = inject(LanguageService);

  /** O concello; sen el, os dos `municipalities` (portada común). */
  readonly slug = input<string | null>(null);
  readonly municipalities = input<readonly EventsMunicipality[]>([]);
  /** Só os das instalacións con estes ids (as que pasan os filtros da portada); `null`, todos. */
  readonly facilityIds = input<ReadonlySet<string> | null>(null);
  /** Só os deste concello (o seu slug; o escollido no buscador da portada común); `null`, todos. */
  readonly only = input<string | null>(null);
  /** Só os que se fan ese día (o primeiro día ata o último, inclusive); `null`, todos os próximos. */
  readonly day = input<IsoDate | null>(null);
  /** Cun filtro posto: o título leva cantos hai. */
  readonly counted = input(false);
  /** Tras cada lectura: se hai algo que mostrar (a portada ofrece entón velo na folla «Filtros»). */
  readonly found = output<boolean>();

  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  private readonly entries = signal<readonly Entry[]>([]);
  protected readonly key = (card: Card) => card.id;
  protected readonly cards = computed<Card[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const [ids, day, only] = [this.facilityIds(), this.day(), this.only()];
    const entries = this.entries().filter(
      ({ event, slug }) =>
        (!only || slug === only) &&
        (!ids || ids.has(event.facility?.id ?? '')) &&
        (!day || (inMadrid(event.startsAt).date <= day && day <= inMadrid(event.endsAt).date)),
    );
    return entries.map(({ event, slug, municipality }) => ({
      id: event.id,
      src: event.cover.thumb,
      alt: inLang(event.cover.alt, lang),
      name: inLang(event.name, lang),
      when: isScheduled(event) ? scheduleWhen(event.schedule, lang, t, true) : dayMonthTime(event.startsAt, lang),
      period: isScheduled(event) ? activityDates(event, lang, t, true) : '',
      place: [event.facility?.name ?? event.place, municipality].filter((part): part is string => !!part).join(' · '),
      free: event.free ?? null,
      slug,
    }));
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      const municipalities = this.municipalities();
      untracked(() => void this.load(slug, municipalities));
    });
  }

  private async load(slug: string | null, municipalities: readonly EventsMunicipality[]): Promise<void> {
    const entries = slug ? await this.readOne(slug) : await this.readAll(municipalities);
    this.entries.set(entries);
    this.found.emit(entries.length > 0);
  }

  private async readOne(slug: string): Promise<Entry[]> {
    try {
      return (await this.store.upcoming(slug)).map((event) => ({ event, slug, municipality: null }));
    } catch {
      return [];
    }
  }

  /** Os de varios concellos, do que empeza antes ao que empeza despois; un que falla non quita os demais. */
  private async readAll(municipalities: readonly EventsMunicipality[]): Promise<Entry[]> {
    const read = await Promise.allSettled(
      municipalities.map(async (m) =>
        (await this.store.upcoming(m.slug)).map((event) => ({ event, slug: m.slug, municipality: m.name })),
      ),
    );
    return read
      .flatMap((result) => (result.status === 'fulfilled' ? result.value : []))
      .sort((x, y) => x.event.startsAt.localeCompare(y.event.startsAt));
  }
}
