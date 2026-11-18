// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { CustomFieldDefinition } from './municipality.store';
import { PlatformAccessService } from '../../core/auth/platform-access.service';
import { canConfigure } from '../../core/auth/roles';
import { SessionService } from '../../core/auth/session.service';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { CustomFieldsViewComponent } from '../../shared/custom-fields/custom-fields-view.component';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { CarouselComponent, CarouselItemDirective } from '../../shared/ui/carousel/carousel.component';
import { CardComponent } from '../../shared/ui/card/card.component';
import type { SearchCriteria } from '../../shared/facility-search/facility-search';
import {
  dayLabel,
  hasCoveredChip,
  matchesCovered,
  matchesSports,
  sportChips,
  sportFilterOptions,
  splitSportFilter,
} from '../../shared/facility-search/facility-search';
import { FacilitySearchComponent } from '../../shared/facility-search/facility-search.component';
import type { FreeSlots } from '../../shared/facility-card/facility-card';
import { collageCovers } from '../../shared/facility-card/facility-card';
import { HeroArtDirective, HeroSearchComponent } from '../../shared/facility-search/hero-search.component';
import { openSearchSheet } from '../../shared/facility-search/search-sheet.component';
import type { SearchSummary } from '../../shared/facility-search/search-summary.component';
import { SearchSummaryComponent } from '../../shared/facility-search/search-summary.component';
import { HomeSectionsComponent } from '../../shared/facility-search/home-sections.component';
import { ListedKind, openSearchFilters } from '../../shared/facility-search/search-filters-dialog.component';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { InstallHintComponent } from '../../shared/ui/install-hint/install-hint.component';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ExampleCardComponent } from './home/example-card.component';
import { exampleFacilities } from './home/examples';
import { FacilityCardComponent } from '../../shared/facility-card/facility-card.component';
import type { PublicFacilityResponse } from './home/home.store';
import { HomeNavigation } from './home/home-navigation';
import { HomeStore } from './home/home.store';
import { HowItWorksComponent } from './home/how-it-works.component';
import { MunicipalityStore } from './municipality.store';
import { OpeningChecklistComponent } from './section/opening-checklist.component';

/** Fotos do collaxe da portada: as portadas das primeiras instalacións que a teñen. */
export const COLLAGE_SIZE = 3;

/**
 * Portada do concello: título co nome do concello,
 * buscador e collaxe de fotos; tarxetas das instalacións publicadas; «Así de fácil» e, se os hai,
 * os datos públicos do concello (campos personalizados). Cada tarxeta leva «N ocos hoxe» se a
 * instalación se reserva por internet e lle quedan ocos; buscar leva ao listado público.
 *
 * O buscador é o da portada común (sen concello): que, día e «Filtros» (a folla co tipo e os
 * deportes das instalacións e «Cuberta»; no móbil, tamén o día). No escritorio, a barra sae arriba ao
 * pulsar «Buscar» na cabeceira (`HeroSearch`). O día e os filtros aplícanse ao momento ás tarxetas
 * (os ocos son os dese día: «N ocos mañá») e van co resto ao listado ao buscar.
 *
 * Exemplos (ADR-0028): mentres o concello non ten ningunha instalación publicada, o
 * collaxe, as tarxetas e «Así de fácil» levan instalacións de exemplo coa etiqueta «Exemplo» e sen
 * ligazón. Con `showcase` (a raíz dunha instalación sen ningún concello) non hai concello: título
 * «Reserva a túa pista», o buscador desactivado e só os exemplos.
 *
 * Aviso para instalar (`furar-install-hint`): enriba de todo, debaixo da barra, só no móbil coa app
 * sen instalar e se o concello o ten aceso.
 *
 * Pendentes: a quen configura o concello (Xestión e a plataforma), mentres lle falte
 * algún paso de «Para abrir as reservas», a lista de «Xeral» pregada enriba do título. Vai á parte
 * (`@defer`), así o público non carga nada da xestión.
 */
@Component({
  selector: 'furar-municipality-home-page',
  imports: [
    TranslocoPipe,
    ExampleCardComponent,
    CustomFieldsViewComponent,
    ButtonDirective,
    CardComponent,
    StatusStateComponent,
    TagComponent,
    IconComponent,
    FacilityCardComponent,
    FacilitySearchComponent,
    HeroArtDirective,
    HeroSearchComponent,
    SearchSummaryComponent,
    HomeSectionsComponent,
    HowItWorksComponent,
    InstallHintComponent,
    CarouselComponent,
    CarouselItemDirective,
    OpeningChecklistComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './municipality-home.page.html',
})
export class MunicipalityHomePage {
  private readonly store = inject(MunicipalityStore);
  private readonly home = inject(HomeStore);
  private readonly session = inject(SessionService);
  private readonly document = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  protected readonly municipality = inject(CurrentMunicipality);
  protected readonly language = inject(LanguageService);
  protected readonly navigation = inject(HomeNavigation);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  /** Quen configura o concello: ve os pasos pendentes. Recepción e a veciñanza, non. */
  protected readonly configures = computed(() => canConfigure(this.staff.role(), this.platform.platformAdmin()));
  protected readonly definitions = signal<CustomFieldDefinition[]>([]);
  protected readonly facilities = signal<PublicFacilityResponse[]>([]);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  /** Ocos libres do día escollido por instalación (as que non se reservan por internet non están). */
  protected readonly free = signal<ReadonlyMap<string, FreeSlots>>(new Map());
  private readonly dialogs = inject(DialogService);
  protected readonly today: IsoDate = madridToday();
  /** O día do buscador: o dos ocos das tarxetas. */
  protected readonly date = signal<IsoDate>(this.today);
  /** As opcións marcadas no filtro «Deporte» da folla «Filtros» (deportes e «Cuberta»). */
  protected readonly filterValue = signal<readonly string[]>([]);

  /** Raíz sen ningún concello: só os exemplos. */
  readonly showcase = input(false, { transform: booleanAttribute });

  protected readonly facilityKey = (facility: PublicFacilityResponse) => facility.id;
  /** Os exemplos van mentres non hai ningunha instalación publicada (ou non hai concello). */
  protected readonly showExamples = computed(
    () => this.showcase() || (this.state() === 'ready' && this.facilities().length === 0),
  );
  /** Os textos do idioma activo xa cargados: os exemplos tradúcense cando chegan. */
  private readonly translation = toSignal(
    this.transloco.langChanges$.pipe(switchMap((lang) => this.transloco.selectTranslation(lang))),
  );
  protected readonly examples = computed(() => {
    this.translation();
    return exampleFacilities((key) => this.transloco.translate(key));
  });
  /** O filtro «Deporte» da barra: os deportes das instalacións e, ao final, «Cuberta» (se algunha o di). */
  protected readonly filterOptions = computed(() => {
    const facilities = this.facilities();
    this.translation();
    const covered = hasCoveredChip(facilities) ? this.transloco.translate('facility.publicList.covered') : null;
    return sportFilterOptions(sportChips(facilities, this.language.lang()), covered);
  });
  /** «Tipo» da folla «Filtros»: todo, as instalacións, as partidas abertas ou os eventos. */
  protected readonly show = new ListedKind(['matches', 'events']);
  /** Filtros da folla postos (deportes, «Cuberta» e un tipo que non é «Todo»); o día cóntao o buscador. */
  protected readonly filterCount = computed(() => this.filterValue().length + this.show.count());
  /** Filtros postos (tamén un día que non é hoxe): sen cabeceira, os resultados debaixo da barra. */
  protected readonly filteredHome = computed(
    () => this.filterValue().length > 0 || this.date() !== this.today || this.show.count() > 0,
  );
  /** As actividades e as partidas, coas mesmas instalacións ca as tarxetas (só cun deporte marcado). */
  protected readonly sectionIds = computed(() =>
    this.filterValue().length ? new Set(this.listed().map((f) => f.id)) : null,
  );
  /** E só as dese día, se non é hoxe. */
  protected readonly sectionDay = computed(() => (this.date() !== this.today ? this.date() : null));
  /** O resumo do buscado (a pílula do móbil). */
  protected readonly summary = computed((): SearchSummary => {
    const sports = this.filterOptions().filter((o) => this.filterValue().includes(o.value));
    return { date: this.date(), today: this.today, sports: sports.map((o) => o.label), kind: this.show.type() };
  });
  /** O número da pílula «Buscar» ao baixar: os filtros e o día. */
  protected readonly pillCount = computed(() => this.filterCount() + (this.date() !== this.today ? 1 : 0));
  /** As tarxetas: as publicadas que pasan o filtro «Deporte». */
  protected readonly listed = computed(() => this.matching(this.filterValue()));
  /** «hoxe», «mañá» ou «xov 29»: o día do chip das tarxetas. */
  protected readonly day = computed(() => {
    this.translation();
    return dayLabel(this.date(), this.today, this.language.lang(), {
      today: this.transloco.translate('facility.publicList.today'),
      tomorrow: this.transloco.translate('facility.publicList.tomorrow'),
    });
  });
  /** As que pintan o collaxe e «Así de fácil»: as publicadas ou, sen elas, os exemplos. */
  protected readonly shown = computed(() => (this.showExamples() ? this.examples() : this.facilities()));
  protected readonly collage = computed(() => collageCovers(this.shown(), COLLAGE_SIZE));
  /** «localhost:8080/naron»: o enderezo da web para as capturas debuxadas (sen concello, só o servidor). */
  protected readonly address = computed(() => {
    const host = this.document.defaultView?.location.host ?? '';
    const slug = this.municipality.slug();
    return this.showcase() || !slug ? host : `${host}/${slug}`;
  });

  constructor() {
    // As definicións visibles dependen da sesión (PUBLIC / OWNER / STAFF).
    effect(() => {
      const slug = this.municipality.slug();
      this.session.authenticated();
      if (slug) {
        this.store.definitions(slug).then(
          (definitions) => this.definitions.set(definitions),
          () => this.definitions.set([]),
        );
      }
    });
    // A orde alfabética é a do idioma: ao cambialo, pídense outra vez.
    effect(() => {
      const slug = this.municipality.slug();
      this.language.lang();
      if (slug) untracked(() => this.load(slug));
    });
    // Os ocos son os do día do buscador: ao cambialo, pídense os dese día.
    effect(() => {
      const slug = this.municipality.slug();
      const date = this.date();
      if (slug) untracked(() => this.loadFree(slug, date));
    });
  }

  /** Buscar: ao listado (ou á ficha), co filtro «Deporte» da barra. */
  protected search(slug: string, criteria: SearchCriteria): void {
    void this.navigation.search(slug, criteria, splitSportFilter(this.filterValue()));
  }

  /** O «✕» da pílula do móbil: sen filtros nin día, volven a cabeceira e todas. */
  protected clearAll(): void {
    this.clearFilters();
    this.date.set(this.today);
  }

  /** O «✕» de «Filtros»: volve a «Todo», sen deportes nin «Cuberta» (o día queda). */
  protected clearFilters(): void {
    this.show.reset();
    this.filterValue.set([]);
  }

  /** A folla «Filtros» (tipo, deportes e, no móbil, o día); aplícase ao pulsar «Ver …». */
  protected async openFilters(): Promise<void> {
    const result = await openSearchFilters(this.dialogs, {
      ...this.sheetData(),
      date: this.date() !== this.today ? this.date() : null,
      count: (value) => this.matching(value).length,
    });
    if (result) {
      this.filterValue.set(this.show.apply(result).value);
      this.date.set(result.date ?? this.today);
    }
  }

  /**
   * Móbil: «Buscar» abre a folla de busca. Cun texto ou unha suxestión, ao listado (ou á ficha),
   * coma a barra; sen eles, o día, o tipo e os deportes aplícanse aquí ás tarxetas.
   */
  protected async openSheet(slug: string, event: Event): Promise<void> {
    const result = await openSearchSheet(
      this.dialogs,
      { ...this.sheetData(), facilities: this.facilities(), criteria: { choice: null, date: this.date(), text: '' } },
      event,
    );
    if (!result) return;
    const { criteria, value } = result;
    if (criteria.choice || criteria.text) {
      void this.navigation.search(slug, criteria, splitSportFilter(value));
      return;
    }
    this.filterValue.set(this.show.apply(result).value);
    this.date.set(criteria.date);
  }

  /** O común das follas de busca e de filtros: deportes, os marcados, hoxe e «Tipo». */
  private sheetData() {
    return { options: this.filterOptions(), value: this.filterValue(), today: this.today, ...this.show.sheet() };
  }

  /** As publicadas que pasan unhas opcións do filtro «Deporte». */
  private matching(value: readonly string[]): PublicFacilityResponse[] {
    const { sports, covered } = splitSportFilter(value);
    return this.facilities().filter((f) => matchesSports(f, sports) && matchesCovered(f, covered));
  }

  protected retry(): void {
    const slug = this.municipality.slug();
    if (slug) {
      this.loadFree(slug, this.date());
      this.load(slug);
    }
  }

  private load(slug: string): void {
    this.state.set(this.facilities().length ? 'ready' : 'loading');
    this.home.facilities(slug).then(
      (facilities) => {
        this.facilities.set(facilities);
        this.state.set('ready');
      },
      () => this.state.set('error'),
    );
  }

  /** Sen ocos (fallo incluído), as tarxetas vense igual, sen o chip. */
  private loadFree(slug: string, date: IsoDate): void {
    this.home.freeSlots(slug, date).then(
      (free) => {
        if (this.date() === date) this.free.set(free);
      },
      () => this.free.set(new Map()),
    );
  }
}
