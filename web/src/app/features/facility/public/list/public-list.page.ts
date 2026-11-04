// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DeploymentSettings } from '../../../../core/deployment/deployment-settings';
import { LanguageService } from '../../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import type { SearchCriteria } from '../../../../shared/facility-search/facility-search';
import { dayLabel } from '../../../../shared/facility-search/facility-search';
import { FacilitySearchComponent } from '../../../../shared/facility-search/facility-search.component';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { localToday } from '../../../../shared/ui/calendar/local-date';
import { FilterBarComponent } from '../../../../shared/ui/filter-bar/filter-bar.component';
import { FilterChipDirective } from '../../../../shared/ui/filter-chip/filter-chip.directive';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { MapComponent } from '../../../../shared/ui/map/map.component';
import type { MapMarker } from '../../../../shared/ui/map/map-point';
import { pointOf } from '../../../../shared/ui/map/map-point';
import { SegmentedDirective } from '../../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../../shared/ui/status-state/status-state.component';
import type { ListFilters, ListView } from './public-list';
import { filterParams, hasCoveredChip, matchesCovered, matchesSports, readFilters, sportChips } from './public-list';
import { PublicListCardComponent } from './public-list-card.component';
import type { FacilityFreeSlotsResponse, PublicFacilityResponse } from './public-list.store';
import { PublicListStore } from './public-list.store';

/**
 * Listado público «Instalacións» (`/:concello/instalacions`, maqueta 16, opción A): o buscador da
 * portada (que + día), chips de filtro cos deportes das instalacións e «Cuberta» (se algunha o
 * di), recuento e Lista · Mapa. A
 * lista é unha reixa de tarxetas con «N ocos hoxe» e as tres primeiras horas; o mapa, unha marca
 * por instalación que leva á súa ficha (a alternativa ao mapa é a propia lista). Os filtros viven
 * no URL (`deporte`, `cuberta`, `data`, `vista`), así que volver atrás e compartir a ligazón manteñen o estado.
 * Se o despregue non ten mapa (`FURAR_MAP=off`), só lista: sen Lista · Mapa e `vista=mapa` dá a lista.
 */
@Component({
  selector: 'furar-public-list-page',
  imports: [
    ButtonDirective,
    FacilitySearchComponent,
    FilterBarComponent,
    FilterChipDirective,
    IconComponent,
    MapComponent,
    PublicListCardComponent,
    SegmentedDirective,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [PublicListStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './public-list.page.html',
})
export class PublicListPage {
  private readonly store = inject(PublicListStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  protected readonly language = inject(LanguageService);

  protected readonly today = localToday();
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  private readonly params = toSignal(this.route.queryParams, { initialValue: {} });
  protected readonly filters = computed(() => readFilters(this.params(), this.today));
  private readonly date = computed(() => this.filters().date);
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  /** A vista que se pinta: sen mapa no despregue, sempre a lista. */
  protected readonly view = computed<ListView>(() => (this.mapEnabled() ? this.filters().view : 'list'));

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly facilities = signal<PublicFacilityResponse[]>([]);
  private readonly slots = signal<ReadonlyMap<string, FacilityFreeSlotsResponse>>(new Map());

  protected readonly chips = computed(() => sportChips(this.facilities(), this.language.lang()));
  protected readonly coveredChip = computed(() => hasCoveredChip(this.facilities()));
  protected readonly shown = computed(() => {
    const { sports, covered } = this.filters();
    return this.facilities().filter((f) => matchesSports(f, sports) && matchesCovered(f, covered));
  });
  /** O buscador abre co día do URL e, se hai un só deporte marcado, con el escrito. */
  protected readonly criteria = computed((): SearchCriteria => {
    const { sports, date } = this.filters();
    return { choice: sports.length === 1 ? { kind: 'sport', key: sports[0] } : null, date };
  });
  /** «hoxe», «mañá» ou «xov 29»: o día do chip das tarxetas. */
  protected readonly day = computed(() => {
    const lang = this.language.lang();
    const words = {
      today: this.transloco.translate('facility.publicList.today'),
      tomorrow: this.transloco.translate('facility.publicList.tomorrow'),
    };
    return dayLabel(this.date(), this.today, lang, words);
  });
  /** O punto de cada instalación vén no enderezo da lista: o mapa non pide nada máis. */
  protected readonly markers = computed((): MapMarker[] =>
    this.shown().flatMap((f) => {
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
      if (slug) untracked(() => this.loadSlots(slug, date));
    });
  }

  protected slotsOf(id: string): FacilityFreeSlotsResponse | null {
    return this.slots().get(id) ?? null;
  }

  protected retry(): void {
    if (this.slug()) this.load(this.slug());
  }

  protected toggleSport(key: string, pressed: boolean): void {
    const sports = this.filters().sports.filter((s) => s !== key);
    this.navigate({ sports: pressed ? [...sports, key] : sports });
  }

  protected toggleCovered(pressed: boolean): void {
    this.navigate({ covered: pressed });
  }

  protected setView(view: ListView): void {
    this.navigate({ view });
  }

  protected clearFilters(): void {
    this.navigate({ sports: [], covered: false });
  }

  /** Buscar: unha instalación leva á súa ficha; un deporte deixa só ese marcado; sempre co día. */
  protected search(criteria: SearchCriteria): void {
    const choice = criteria.choice;
    if (choice?.kind === 'facility') {
      void this.router.navigate(['/', this.slug(), 'instalacions', choice.id]);
      return;
    }
    this.navigate({ date: criteria.date, ...(choice ? { sports: [choice.key] } : {}) });
  }

  protected openFacility(id: string): void {
    void this.router.navigate(['/', this.slug(), 'instalacions', id]);
  }

  private navigate(change: Partial<ListFilters>): void {
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

  /** Sen ocos (tamén se fallan), as tarxetas vense igual, sen chip nin horas. */
  private loadSlots(slug: string, date: string): void {
    this.store.availability(slug, date).then(
      (slots) => {
        if (this.date() === date) this.slots.set(slots);
      },
      () => this.slots.set(new Map()),
    );
  }
}
