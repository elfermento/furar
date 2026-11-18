// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DeploymentSettings } from '../../../../core/deployment/deployment-settings';
import { mediaMatches } from '../../../../core/layout/language-menu';
import { LanguageService } from '../../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import type { SearchCriteria } from '../../../../shared/facility-search/facility-search';
import {
  dayLabel,
  matchesText,
  splitSportFilter,
  sportFilterOptions,
  sportFilterValue,
} from '../../../../shared/facility-search/facility-search';
import { openSearchFilters } from '../../../../shared/facility-search/search-filters-dialog.component';
import { DialogService } from '../../../../shared/ui/dialog/dialog.service';
import { FacilitySearchComponent } from '../../../../shared/facility-search/facility-search.component';
import { AllMunicipalitiesLinkComponent } from '../../../../shared/ui/all-municipalities-link/all-municipalities-link.component';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { ChipListComponent } from '../../../../shared/ui/chip-list/chip-list.component';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import type { MapMarker } from '../../../../shared/ui/map/map-point';
import { pointOf } from '../../../../shared/ui/map/map-point';
import { StatusStateComponent } from '../../../../shared/ui/status-state/status-state.component';
import { CatalogMapComponent, NearNoticeComponent } from '../common/catalog-map.component';
import { CatalogMapFloatComponent } from '../common/catalog-map-float.component';
import { SPLIT_QUERY, byDistance } from '../common/common-home';
import { MapZone } from '../common/map-zone';
import { NearMe } from '../common/near-me';
import type { ListFilters } from './public-list';
import { filterParams, hasCoveredChip, matchesCovered, matchesSports, readFilters, sportChips } from './public-list';
import { FacilityCardComponent } from '../../../../shared/facility-card/facility-card.component';
import type { FacilityFreeSlotsResponse, PublicFacilityResponse } from './public-list.store';
import { PublicListStore } from './public-list.store';

/**
 * Listado público «Instalacións» (`/:concello/instalacions`): o buscador das
 * portadas (que, día e o filtro «Deporte» cos deportes das instalacións e «Cuberta», se algunha o
 * di; no móbil, día e deporte na folla «Filtros»), recuento e Lista · Mapa. A
 * lista é unha reixa de tarxetas con «N ocos hoxe» e as tres primeiras horas; o mapa, unha marca
 * por instalación que leva á súa ficha (a alternativa ao mapa é a propia lista). Os filtros viven
 * no URL (`busca`, `deporte`, `cuberta`, `data`, `vista`), así que volver atrás e compartir a ligazón manteñen o estado.
 * Se o despregue non ten mapa (`FURAR_MAP=off`), só lista: sen Lista · Mapa e `vista=mapa` dá a lista.
 */
@Component({
  selector: 'furar-public-list-page',
  imports: [
    AllMunicipalitiesLinkComponent,
    ButtonDirective,
    ChipListComponent,
    FacilitySearchComponent,
    CatalogMapComponent,
    CatalogMapFloatComponent,
    NearNoticeComponent,
    NgTemplateOutlet,
    FacilityCardComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [PublicListStore, NearMe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './public-list.page.html',
})
export class PublicListPage {
  private readonly store = inject(PublicListStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly dialogs = inject(DialogService);
  protected readonly language = inject(LanguageService);
  private readonly nearMe = inject(NearMe);

  protected readonly today = madridToday();
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  private readonly params = toSignal(this.route.queryParams, { initialValue: {} });
  protected readonly filters = computed(() => readFilters(this.params(), this.today));
  private readonly date = computed(() => this.filters().date);
  /** Os deportes do filtro, como texto: os ocos pídense de novo só se cambian. */
  private readonly sportsKey = computed(() => this.filters().sports.join(','));
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  /** «Zona do mapa»: co mapa movido, só as que caen no que se ve (coma nas portadas). */
  protected readonly zone = new MapZone();
  /** Lista e mapa caben un ao lado do outro. */
  protected readonly wide = mediaMatches(inject(DOCUMENT).defaultView, SPLIT_QUERY, inject(DestroyRef));
  /** O botón flotante «Mapa» está pulsado (non vai no URL). */
  protected readonly mapOpen = signal(false);
  /** Escritorio co mapa aberto: a grella á esquerda e o mapa á dereita. */
  protected readonly split = computed(() => this.mapEnabled() && this.mapOpen() && this.wide());
  /** Móbil co mapa aberto: o listado vai na folla que asoma debaixo del. */
  protected readonly inSheet = computed(() => this.mapEnabled() && this.mapOpen() && !this.wide());
  /** A tarxeta baixo o rato ou co foco: o seu punto resáltase no mapa. */
  protected readonly pointed = signal<string | null>(null);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly facilities = signal<PublicFacilityResponse[]>([]);
  private readonly slots = signal<ReadonlyMap<string, FacilityFreeSlotsResponse>>(new Map());

  /** Os textos do idioma activo xa cargados («Cuberta» e o día tradúcense cando chegan). */
  private readonly translation = toSignal(
    this.transloco.langChanges$.pipe(switchMap((lang) => this.transloco.selectTranslation(lang))),
  );
  /** O filtro «Deporte» da barra: os deportes das instalacións e, ao final, «Cuberta» (se algunha o di). */
  protected readonly filterOptions = computed(() => {
    const facilities = this.facilities();
    this.translation();
    const covered = hasCoveredChip(facilities) ? this.transloco.translate('facility.publicList.covered') : null;
    return sportFilterOptions(sportChips(facilities, this.language.lang()), covered);
  });
  protected readonly filterValue = computed(() => sportFilterValue(this.filters().sports, this.filters().covered));
  /** Filtros da folla postos (deportes e «Cuberta»); o día cóntao o buscador. */
  protected readonly filterCount = computed(() => {
    const { sports, covered } = this.filters();
    return sports.length + (covered ? 1 : 0);
  });
  /** As que pasan os filtros (as marcas do mapa). */
  protected readonly matched = computed(() => this.matching(this.filters()));
  /** As do listado: as que pasan os filtros e, con «Zona do mapa», caen nela. */
  protected readonly shown = computed(() => this.zone.keep(this.matched()));
  /** As tarxetas: na orde de sempre ou, con «Preto de min», da máis próxima á máis lonxana. */
  protected readonly cards = computed(() => {
    const here = this.nearMe.here();
    return here ? byDistance(this.shown(), here) : this.shown().map((facility) => ({ facility, km: null }));
  });
  /** «N instalacións» ou, coa zona, «N instalacións nesta zona». */
  protected readonly countText = computed(() => this.zone.countText(this.shown().length));
  /** O buscador abre co día do URL e co texto buscado ou, se non hai, co deporte marcado (se é un só). */
  protected readonly criteria = computed((): SearchCriteria => {
    const { sports, date, text } = this.filters();
    return { choice: !text && sports.length === 1 ? { kind: 'sport', key: sports[0] } : null, date, text };
  });
  /** O texto buscado, como chip que se quita («torre» ×). */
  protected readonly textChips = computed(() => this.zone.chips(this.filters().text));
  /** «hoxe», «mañá» ou «xov 29»: o día do chip das tarxetas. */
  protected readonly day = computed(() => {
    const lang = this.language.lang();
    this.translation();
    const words = {
      today: this.transloco.translate('facility.publicList.today'),
      tomorrow: this.transloco.translate('facility.publicList.tomorrow'),
    };
    return dayLabel(this.date(), this.today, lang, words);
  });
  /** O punto de cada instalación vén no enderezo da lista: o mapa non pide nada máis. */
  protected readonly markers = computed((): MapMarker[] =>
    this.matched().flatMap((f) => {
      const point = pointOf(f.address);
      return point ? [{ id: f.id, point, label: f.name }] : [];
    }),
  );

  constructor() {
    // A orde alfabética é a do idioma: ao cambialo, pídense outra vez.
    effect(() => {
      const slug = this.slug();
      this.language.lang();
      if (slug) untracked(() => this.load(slug));
    });
    effect(() => {
      const slug = this.slug();
      const date = this.date();
      const sports = this.sportsKey();
      if (slug) untracked(() => this.loadSlots(slug, date, sports));
    });
  }

  protected slotsOf(id: string): FacilityFreeSlotsResponse | null {
    return this.slots().get(id) ?? null;
  }

  protected retry(): void {
    if (this.slug()) this.load(this.slug());
  }

  /** A folla «Filtros» (deportes e, no móbil, o día); aplícase ao pulsar «Ver N instalacións». */
  protected async openFilters(): Promise<void> {
    const filters = this.filters();
    const result = await openSearchFilters(this.dialogs, {
      options: this.filterOptions(),
      value: this.filterValue(),
      date: filters.date !== this.today ? filters.date : null,
      today: this.today,
      count: (value) => this.matching({ ...filters, ...splitSportFilter(value) }).length,
    });
    if (result) this.navigate({ ...splitSportFilter(result.value), date: result.date ?? this.today });
  }

  protected clearFilters(): void {
    this.zone.clear();
    this.navigate({ sports: [], covered: false, text: '' });
  }

  /** Quitouse un chip: o do texto buscado (volven todas) ou o da zona do mapa. */
  protected removeChip(index: number): void {
    if (this.textChips()[index]?.key === 'zone') this.zone.clear();
    else this.navigate({ text: '' });
  }

  /**
   * Buscar: unha instalación leva á súa ficha; un deporte deixa só ese marcado; o texto escrito sen
   * escoller suxestión filtra por el; sempre co día.
   */
  protected search(criteria: SearchCriteria): void {
    const choice = criteria.choice;
    if (choice?.kind === 'facility') {
      void this.router.navigate(['/', this.slug(), 'instalacions', choice.id]);
      return;
    }
    this.navigate({
      date: criteria.date,
      text: choice ? '' : (criteria.text?.trim() ?? ''),
      ...(choice?.kind === 'sport' ? { sports: [choice.key] } : {}),
    });
  }

  /** Picouse unha marca soa: á ficha, coma a súa tarxeta (co deporte do filtro). */
  protected openFacility(id: string): void {
    const sports = this.filters().sports;
    void this.router.navigate(['/', this.slug(), 'instalacions', id], {
      queryParams: sports.length ? { deporte: [...sports] } : {},
    });
  }

  /** As instalacións que pasan uns filtros (os do URL ou os da folla «Filtros»). */
  private matching({ sports, covered, text }: ListFilters): PublicFacilityResponse[] {
    const lang = this.language.lang();
    return this.facilities().filter(
      (f) => matchesSports(f, sports) && matchesCovered(f, covered) && matchesText(f, text, lang),
    );
  }

  protected navigate(change: Partial<ListFilters>): void {
    const queryParams = filterParams({ ...this.filters(), ...change }, this.today);
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }

  private load(slug: string): void {
    this.state.set(this.facilities().length ? 'ready' : 'loading');
    this.store.facilities(slug).then(
      (facilities) => {
        this.facilities.set(facilities);
        this.state.set('ready');
      },
      () => this.state.set('error'),
    );
  }

  /**
   * Sen ocos (tamén se fallan), as tarxetas vense igual, sen chip nin horas. Cun filtro de
   * deportes, os do primeiro uso de cada instalación con algún deles.
   */
  private loadSlots(slug: string, date: string, sports: string): void {
    this.store.availability(slug, date, sports ? sports.split(',') : []).then(
      (slots) => {
        if (this.date() === date && this.sportsKey() === sports) this.slots.set(slots);
      },
      () => this.slots.set(new Map()),
    );
  }
}
