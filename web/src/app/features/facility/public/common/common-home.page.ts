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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { switchMap } from 'rxjs';
import { DeploymentSettings } from '../../../../core/deployment/deployment-settings';
import { mediaMatches } from '../../../../core/layout/language-menu';
import { LanguageService } from '../../../../core/i18n/language.service';
import type { SearchCriteria } from '../../../../shared/facility-search/facility-search';
import { dayLabel, sportFilterOptions, sportFilterValue } from '../../../../shared/facility-search/facility-search';
import { FacilitySearchComponent } from '../../../../shared/facility-search/facility-search.component';
import { HeroArtDirective, HeroSearchComponent } from '../../../../shared/facility-search/hero-search.component';
import type { SearchSheetResult } from '../../../../shared/facility-search/search-sheet.component';
import type { SearchSummary } from '../../../../shared/facility-search/search-summary.component';
import { SearchSummaryComponent } from '../../../../shared/facility-search/search-summary.component';
import { HomeSectionsComponent } from '../../../../shared/facility-search/home-sections.component';
import { openSearchSheet } from '../../../../shared/facility-search/search-sheet.component';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { CardComponent } from '../../../../shared/ui/card/card.component';
import { ChipListComponent } from '../../../../shared/ui/chip-list/chip-list.component';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { InstallHintComponent } from '../../../../shared/ui/install-hint/install-hint.component';
import type { SelectOption } from '../../../../shared/ui/select/select.component';
import { StatusStateComponent } from '../../../../shared/ui/status-state/status-state.component';
import { hasCoveredChip } from '../list/public-list';
import { FacilityCardComponent } from '../../../../shared/facility-card/facility-card.component';
import { collageCovers } from '../../../../shared/facility-card/facility-card';
import { ListedKind, openSearchFilters } from '../../../../shared/facility-search/search-filters-dialog.component';
import {
  catalogMarkers,
  catalogSummary,
  filterCount,
  pillCount,
  searchChange,
  sectionIds,
  sheetChange,
  sheetKey,
  splitSportFilter,
} from './catalog-view';
import type { CatalogFacility, CatalogFilters } from './common-home';
import {
  SPLIT_QUERY,
  byDistance,
  catalogCriteria,
  catalogParams,
  catalogSportKeys,
  realSports,
  catalogSportOptions,
  hasCatalogFilter,
  matchesCatalog,
  readCatalogFilters,
} from './common-home';
import { CommonHomeStore } from './common-home.store';
import { CatalogMapComponent, NearNoticeComponent } from './catalog-map.component';
import { CatalogMapFloatComponent } from './catalog-map-float.component';
import { CatalogRailsComponent } from './catalog-rails.component';
import { MapZone } from './map-zone';
import { NearMe } from './near-me';

/** Fotos do collaxe: as portadas das primeiras instalacións. */
const COLLAGE_SIZE = 3;

/**
 * Portada común (`/` cando a instalación ten varios concellos, ADR-0030): «Reserva a túa pista» co
 * buscador (instalación ou concello, día e «Filtros») e, debaixo, as instalacións de todos
 * os concellos que saen nela, sen escoller concello antes. Que concellos saen decídeo a API.
 *
 * Sen filtros: un carrusel por concello (`furar-facility-card`, as tarxetas da portada do
 * concello), despois «Buscan xente para xogar» e «Eventos e actividades» (cada un só se hai algo).
 * Cun filtro (texto, concello, deporte, «Cuberta» ou día), todas as que pasan nunha grella.
 *
 * O botón flotante «Mapa» (abaixo no medio, nos dous casos, `furar-catalog-map-float`): desde
 * 900 px, a grella de todas as que se ven á esquerda e o mapa á dereita, fixo ao baixar (pasar o
 * rato ou o foco por unha tarxeta marca o seu punto); no móbil, o mapa a toda a pantalla, coa
 * folla do listado asomando debaixo. «Lista» (ou Esc no móbil) volve. Picar unha marca leva á
 * ficha da instalación; as marcas cercanas agrúpanse cun número, que achega ao picalo. Sen mapa
 * no despregue (`FURAR_MAP=off`), sen botón.
 *
 * A barra de busca sae arriba ao pulsar «Buscar» na cabeceira (`furar-hero-search`) ou se xa hai filtros;
 * os filtros (tipo, deportes) van na folla «Filtros» (`furar-search-filters-dialog`), que no móbil
 * leva tamén o día.
 *
 * «Preto de min» (dentro do mapa): pide a posición ao navegador, ordena a lista pola
 * distancia, pon «a 1,2 km» en cada tarxeta e centra o mapa. A posición non sae do navegador nin se
 * garda. Sen permiso non cambia nada e sae un aviso que se pode pechar.
 *
 * O texto escrito no buscador sen escoller suxestión («torre») filtra a lista e o mapa polo nome, o
 * concello, o enderezo ou os deportes; sae como chip xunto ao número, co «×» para quitalo.
 *
 * Os filtros viven no URL (`busca`, `deporte`, `cuberta`, `data`, `concello`). Cada tarxeta leva ao seu
 * concello (`/<slug>/instalacions/<id>`) e as súas horas, a reservar nel.
 */
@Component({
  selector: 'furar-common-home-page',
  imports: [
    ButtonDirective,
    CardComponent,
    ChipListComponent,
    FacilitySearchComponent,
    IconComponent,
    CatalogMapComponent,
    CatalogMapFloatComponent,
    CatalogRailsComponent,
    HeroArtDirective,
    HeroSearchComponent,
    SearchSummaryComponent,
    HomeSectionsComponent,
    InstallHintComponent,
    NgTemplateOutlet,
    NearNoticeComponent,
    FacilityCardComponent,
    RouterLink,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [CommonHomeStore, NearMe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './common-home.page.html',
})
export class CommonHomePage {
  private readonly store = inject(CommonHomeStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly dialogs = inject(DialogService);
  protected readonly nearMe = inject(NearMe);
  protected readonly language = inject(LanguageService);

  protected readonly today = madridToday();
  private readonly params = toSignal(this.route.queryParams, { initialValue: {} });
  protected readonly filters = computed(() => readCatalogFilters(this.params(), this.today));
  private readonly date = computed(() => this.filters().date);
  /** Os deportes do filtro para a API, como texto: os ocos pídense de novo só se cambian. */
  private readonly sportsKey = computed(() =>
    realSports(this.facilities(), this.filters().sports, this.sportKeys()).join(','),
  );
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  protected readonly joinRequests = inject(DeploymentSettings).joinRequests; // «¿Es un concello?», ADR-0042

  /** «Zona do mapa»: co mapa movido, só as que caen no que se ve. */
  protected readonly zone = new MapZone();
  /** Lista e mapa caben un ao lado do outro. */
  protected readonly wide = mediaMatches(inject(DOCUMENT).defaultView, SPLIT_QUERY, inject(DestroyRef));
  /** O botón flotante «Mapa» está pulsado (non vai no URL). */
  protected readonly mapOpen = signal(false);
  /** Co mapa aberto no escritorio: a grella á esquerda e o mapa á dereita. */
  protected readonly split = computed(() => this.mapEnabled() && this.mapOpen() && this.wide());

  /** Os textos do idioma activo xa cargados («Cuberta» e o día tradúcense cando chegan). */
  private readonly translation = toSignal(
    this.transloco.langChanges$.pipe(switchMap((lang) => this.transloco.selectTranslation(lang))),
  );

  protected readonly state = this.store.state;
  protected readonly facilities = this.store.list;
  protected readonly slots = this.store.slots;
  /** A tarxeta baixo o rato ou co foco. */
  protected readonly pointed = signal<string | null>(null);

  /** A chave de cada deporte no filtro: os propios dos concellos únense polo nome. */
  protected readonly sportKeys = computed(() => catalogSportKeys(this.facilities(), this.language.lang()));
  /** As que pasan os filtros (as marcas do mapa). */
  protected readonly matched = computed(() => this.matching(this.filters()));
  /** As do listado: as que pasan os filtros e, con «Zona do mapa», caen nela. */
  protected readonly shown = computed(() => this.zone.keep(this.matched()));
  /** As actividades e as partidas, coas mesmas instalacións cás do listado (só cun filtro delas). */
  protected readonly sectionIds = computed(() => sectionIds(this.filters(), !!this.zone.bounds(), this.shown()));
  /** E só as dese día, se non é hoxe. */
  protected readonly sectionDay = computed(() => (this.date() !== this.today ? this.date() : null));
  /** Hai un filtro posto: os resultados; sen el, a portada cos carruseis. */
  protected readonly filtered = computed(() => hasCatalogFilter(this.filters(), this.today));
  /** Os concellos das instalacións: os seus eventos van nun só carrusel. */
  protected readonly municipalities = computed(() => [
    ...new Map(this.facilities().map((f) => [f.municipality.slug, f.municipality])).values(),
  ]);
  /** Co mapa do móbil aberto, o listado vai na folla que asoma debaixo del (non na páxina). */
  protected readonly inSheet = computed(() => this.mapEnabled() && this.mapOpen() && !this.wide());
  /** O título desa folla: «60 instalacións» ou, cun filtro, «14 resultados». */
  protected readonly sheetLabel = computed(() =>
    this.say(sheetKey(this.shown().length, !!this.zone.bounds(), this.filtered()), this.shown().length),
  );
  /** As tarxetas: na orde de sempre ou, con «Preto de min», da máis próxima á máis lonxana. */
  protected readonly cards = computed(() => {
    const here = this.nearMe.here();
    return here ? byDistance(this.shown(), here) : this.shown().map((facility) => ({ facility, km: null }));
  });
  /** «Mostrar» da folla do móbil: as instalacións ou, se hai algunha, as partidas abertas. */
  protected readonly show = new ListedKind(['events', 'matches']);
  /** Os filtros da folla (tipo, deportes e «Cuberta»); o día cóntao o buscador. */
  protected readonly filterCount = computed(() => filterCount(this.filters()) + this.show.count());
  /** O número da pílula «Buscar» ao baixar: o texto ou o concello, o día e os da folla. */
  protected readonly pillCount = computed(() => pillCount(this.filters(), this.filterCount(), this.today));
  /** Os deportes de todas as instalacións e, ao final, «Cuberta» (se algunha o di). */
  protected readonly filterOptions = computed((): SelectOption<string>[] => {
    const facilities = this.facilities();
    const covered = hasCoveredChip(facilities) ? this.say('facility.publicList.covered', 0) : null;
    return sportFilterOptions(catalogSportOptions(facilities, this.language.lang(), this.sportKeys()), covered);
  });
  private readonly filterValue = computed(() => sportFilterValue(this.filters().sports, this.filters().covered));
  /** O buscador abre co día do URL e, se hai un concello escollido ou un texto buscado, con el escrito. */
  protected readonly criteria = computed(() => catalogCriteria(this.filters()));
  /** O texto buscado, como chip que se quita («torre» ×). */
  protected readonly textChips = computed(() => this.zone.chips(this.filters().text));
  /** Hai algo buscado (tamén «Zona do mapa»): sen cabeceira, os resultados arriba. */
  protected readonly searching = computed(() => this.filtered() || !!this.zone.bounds());
  /** O resumo do buscado (a pílula do móbil). */
  protected readonly summary = computed((): SearchSummary => ({
    ...catalogSummary(this.filters(), this.municipalities(), this.filterOptions(), this.filterValue(), this.today),
    kind: this.show.type(),
    zone: !!this.zone.bounds(),
  }));
  protected readonly countText = computed(() => this.zone.countText(this.shown().length));
  /** «Quitar os filtros»: todos os concellos, deportes e textos (o día queda). */
  protected readonly noFilters: Partial<CatalogFilters> = { sports: [], covered: false, municipality: null, text: '' };
  /** «hoxe», «mañá» ou «xov 29»: o día do chip das tarxetas. */
  protected readonly day = computed(() => {
    const [today, tomorrow] = [this.say('facility.publicList.today', 0), this.say('facility.publicList.tomorrow', 0)];
    return dayLabel(this.date(), this.today, this.language.lang(), { today, tomorrow });
  });
  /** O punto de cada instalación vén no enderezo: o mapa non pide nada máis. */
  protected readonly markers = computed(() => catalogMarkers(this.matched()));
  protected readonly collage = computed(() => collageCovers(this.facilities(), COLLAGE_SIZE).map((c) => c.large));
  constructor() {
    // A orde alfabética é a do idioma: ao cambialo, pídense outra vez.
    effect(() => {
      this.language.lang();
      untracked(() => this.load());
    });
    effect(() => {
      const date = this.date();
      const sports = this.sportsKey();
      untracked(() => this.loadSlots(date, sports));
    });
  }

  /** Móbil: a folla «Filtros» (que mostrar, data e deportes); aplícase ao pulsar «Ver …». */
  protected async openFilters(): Promise<void> {
    const filters = this.filters();
    const result = await openSearchFilters(this.dialogs, {
      ...this.sheetData(),
      date: filters.dateChosen || filters.date !== this.today ? filters.date : null,
      count: (value) => this.matching({ ...filters, ...splitSportFilter(value) }).length,
    });
    if (result) void this.navigate(sheetChange(this.show.apply(result), this.today));
  }

  /** O «✕» da pílula do móbil: sen ningún filtro (nin día nin zona), volven a cabeceira e os carruseis. */
  protected clearAll(): void {
    this.show.reset();
    this.zone.clear();
    void this.navigate({ ...this.noFilters, date: this.today, dateChosen: false });
  }

  /** O «✕» de «Filtros»: volve a «Todo», sen deportes nin «Cuberta» (o día queda). */
  protected clearFilters(): void {
    this.show.reset();
    this.zone.clear();
    void this.navigate({ sports: [], covered: false });
  }

  /** «Quitar os filtros» (sen resultados): tamén a zona do mapa. */
  /** Un texto co número, no idioma activo (cando a tradución xa chegou). */
  private say(key: string, count: number): string {
    this.translation();
    return this.transloco.translate(key, { count });
  }

  /** Buscar (`searchChange`): unha instalación leva á súa ficha, no seu concello; o resto (tamén o texto) filtra. */
  protected async search(criteria: SearchCriteria, sheet?: SearchSheetResult): Promise<void> {
    // Buscar na barra é buscar instalacións: a lista volve a elas. Na folla do móbil, co seu «Tipo».
    if (sheet) this.show.apply(sheet);
    else this.show.reset();
    const found = searchChange(criteria, this.filters(), this.facilities(), this.sportKeys());
    if (found && 'facility' in found) {
      void this.router.navigate(['/', found.facility.municipality.slug, 'instalacions', found.facility.id]);
    } else if (found) {
      const chosen = criteria.date !== this.today;
      const change = sheet ? { ...splitSportFilter(sheet.value), ...found.change, dateChosen: chosen } : found.change;
      await this.navigate(change);
    }
  }

  /** Móbil: «Buscar» abre a folla de busca (onde, cando, tipo e deporte) e aplica o escollido. */
  protected async openSheet(event: Event, summary: SearchSummaryComponent): Promise<void> {
    const where = { criteria: this.criteria(), facilities: this.facilities(), sportKeyOf: this.sportKeys() };
    const result = await openSearchSheet(this.dialogs, { ...this.sheetData(), ...where, catalog: true }, event);
    if (result) await this.search(result.criteria, result);
    // Cun filtro, a cabeceira (e o seu «Buscar», onde volvería o foco) escóndese: o foco, á pílula do resumo.
    if (this.searching()) summary.focusSoon();
  }

  /** O común das follas de busca e de filtros: deportes, os marcados, hoxe e «Tipo». */
  private sheetData() {
    return { options: this.filterOptions(), value: this.filterValue(), today: this.today, ...this.show.sheet() };
  }

  /** Picouse unha marca soa: á ficha da instalación, coma a súa tarxeta (co deporte do filtro). */
  protected openFacility(id: string): void {
    const facility = this.matched().find((f) => f.id === id);
    if (!facility) return;
    const sports = this.cardSports(facility);
    void this.router.navigate(['/', facility.municipality.slug, 'instalacions', id], {
      queryParams: sports.length ? { deporte: sports } : {},
    });
  }

  /** Os deportes do filtro dunha instalación, coas súas claves: a tarxeta lévaos a reservar. */
  protected cardSports(facility: CatalogFacility): string[] {
    return realSports([facility], this.filters().sports, this.sportKeys(), false);
  }

  /** As instalacións que pasan uns filtros (os do URL ou os da folla «Filtros»). */
  private matching(filters: CatalogFilters): CatalogFacility[] {
    const [key, lang] = [this.sportKeys(), this.language.lang()];
    return this.facilities().filter((f) => matchesCatalog(f, filters, key, lang));
  }

  /** Cambia os filtros do URL (tamén o chip do texto buscado, que o quita). */
  protected navigate(change: Partial<CatalogFilters>): Promise<boolean> {
    const queryParams = catalogParams({ ...this.filters(), ...change }, this.today);
    return this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }

  /** Le as instalacións (tamén «Tentar de novo» tras un fallo). */
  protected load(): void {
    this.store.load();
  }

  /** Sen ocos (tamén se fallan), as tarxetas vense igual; cun filtro, os dos usos dos deportes. */
  private loadSlots(date: string, sports: string): void {
    this.store.loadSlots(date, sports, () => this.date() === date && this.sportsKey() === sports);
  }
}
