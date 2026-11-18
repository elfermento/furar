// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { preparePanelPrint, printWithChartImages } from '../../../shared/print/print-charts';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import type { ColumnFilterOption } from '../../../shared/ui/column-filter/column-filter.component';
import { ColumnFilterComponent } from '../../../shared/ui/column-filter/column-filter.component';
import type { PeriodValue } from '../../../shared/ui/date-filter/period-filter';
import { PeriodFilterComponent } from '../../../shared/ui/date-filter/period-filter.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { downloadCsv } from '../../../shared/ui/export-menu/csv';
import { ExportMenuComponent } from '../../../shared/ui/export-menu/export-menu.component';
import { FilterBarComponent, FilterFieldComponent } from '../../../shared/ui/filter-bar/filter-bar.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type { Translate } from '../conclusions';
import type { EvolutionDialogData } from '../indicator-evolution.component';
import { IndicatorEvolutionComponent } from '../indicator-evolution.component';
import { evolutionView } from '../indicator-evolution';
import type { IndicatorFilters } from '../indicator-filters';
import { MAX_CUSTOM_DAYS, filtersFromParams, filtersToParams } from '../indicator-filters';
import { IndicatorFormats } from '../indicator-format';
import type { KpiOpen } from '../indicator-kpis.component';
import { IndicatorKpisComponent } from '../indicator-kpis.component';
import { kpiCsvHeader, kpiViews } from '../indicator-kpis';
import { periodFile, periodTitle } from '../indicator-period';
import type { ColumnId, Sort, SortDirection } from './platform-indicators';
import {
  COLUMNS,
  DEFAULT_SORT,
  columnLabel,
  columnSortOptions,
  mobileSortOptions,
  platformCsv,
  rowViews,
  sortRows,
  totalsPanel,
} from './platform-indicators';
import { PlatformIndicatorsStore } from './platform-indicators.store';

/**
 * «Indicadores da plataforma» (`/plataforma/indicadores`; ADR-0026, revisión
 * do 2026-11-11): só a administración da plataforma (`platformGuard`). O período co mesmo chip ca
 * os indicadores do concello (no URL), «Exportar» (PDF e CSV, no navegador), as catro cifras
 * sumadas (cada unha abre a súa evolución) e unha fila por concello, ordenable desde a cabeceira
 * (no móbil, tarxetas e «Ordenar»). A fila non leva a ningunha parte: só cifras.
 */
@Component({
  selector: 'furar-platform-indicators-page',
  imports: [
    TranslocoPipe,
    ColumnFilterComponent,
    ExportMenuComponent,
    FilterBarComponent,
    FilterFieldComponent,
    IndicatorKpisComponent,
    PeriodFilterComponent,
    ProblemAlertComponent,
    StatusStateComponent,
  ],
  providers: [PlatformIndicatorsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './platform-indicators.page.html',
})
export class PlatformIndicatorsPage {
  protected readonly store = inject(PlatformIndicatorsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly dialogs = inject(DialogService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.selectTranslation());
  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });

  protected readonly today = madridToday();
  protected readonly maxDays = MAX_CUSTOM_DAYS;
  protected readonly columns = COLUMNS;
  protected readonly filters = computed<IndicatorFilters>(() => {
    const filters = filtersFromParams(this.params());
    return { ...filters, facilityId: null, use: null };
  });
  protected readonly sort = signal<Sort>(DEFAULT_SORT);
  /** O anuncio do resultado dun cambio de período (non o da primeira carga). */
  protected readonly announcement = signal('');
  private readonly generatedAt = signal(new Date());
  private changedByUser = false;

  private readonly formats = computed(() => new IndicatorFormats(this.language.lang()));
  private readonly t = computed<Translate>(() => {
    this.translations();
    return (key, params) => this.transloco.translate(key, params);
  });

  protected readonly month = computed(() => this.formats().monthTitle(this.today));
  protected readonly kpis = computed(() => {
    const data = this.store.data();
    return data ? kpiViews(totalsPanel(data), this.t(), this.formats()) : [];
  });
  private readonly sorted = computed(() =>
    sortRows(this.store.data()?.municipalities ?? [], this.sort(), this.language.lang()),
  );
  protected readonly rows = computed(() => rowViews(this.sorted(), this.t(), this.formats()));

  /** A cabeceira de cada columna: o rótulo e as súas dúas ordes. */
  protected readonly headers = computed(() => {
    const t = this.t();
    const sort = this.sort();
    return COLUMNS.map((column) => ({
      id: column,
      label: columnLabel(column, t),
      numeric: column !== 'name',
      active: sort.column === column,
      options: columnSortOptions(column, t) as ColumnFilterOption[],
      selected: sort.column === column ? [sort.direction] : [],
    }));
  });
  protected readonly mobileOptions = computed<ColumnFilterOption[]>(() => mobileSortOptions(this.t()));
  /** No móbil, a opción marcada é a da columna (as numéricas, de maior a menor). */
  protected readonly mobileSelected = computed(() => {
    const sort = this.sort();
    const matches = mobileSortOptions(this.t()).find(
      (o) => o.sort.column === sort.column && o.sort.direction === sort.direction,
    );
    return matches ? [matches.value] : [];
  });

  private readonly periodText = computed(() => {
    const data = this.store.data();
    return data ? periodTitle(data.period, this.t(), this.formats()) : '';
  });
  protected readonly printHeader = computed(() => this.headerAt(this.generatedAt()));

  constructor() {
    effect(() => {
      const filters = this.filters();
      untracked(() => void this.load(filters));
    });
    this.printWithoutExport();
  }

  /** A cabeceira impresa e do CSV: o período e cando se xerou. */
  private headerAt(generated: Date): string {
    const period = this.periodText();
    if (!period) return '';
    return this.t()('platformIndicators.print.header', {
      period,
      generated: this.formats().dateTime(generated),
    });
  }

  protected setPeriod(period: PeriodValue): void {
    const next: IndicatorFilters = { ...this.filters(), period: period.period, from: period.from, to: period.to };
    if (next.period === this.filters().period && next.from === this.filters().from && next.to === this.filters().to)
      return;
    this.changedByUser = true;
    void this.router.navigate([], { relativeTo: this.route, queryParams: filtersToParams(next) });
  }

  protected sortBy(column: ColumnId, direction: string): void {
    this.sort.set({ column, direction: direction as SortDirection });
  }

  protected sortMobile(value: string): void {
    const option = mobileSortOptions(this.t()).find((o) => o.value === value);
    if (option) this.sort.set(option.sort);
  }

  /** O modal coa evolución da cifra sumada (o foco volve á súa tarxeta). */
  protected openKpi(open: KpiOpen): void {
    const data = this.store.data();
    const kpi = this.kpis().find((k) => k.id === open.id);
    if (!data || !kpi) return;
    const view = evolutionView(totalsPanel(data), kpi, this.t(), this.formats());
    this.dialogs.open<unknown, EvolutionDialogData>(IndicatorEvolutionComponent, {
      from: open.element,
      data: {
        view,
        header: (generated) => this.headerAt(generated),
        csvName: `${this.fileName(data.period)}-${view.file}.csv`,
      },
    });
  }

  protected readonly exportCsv = (): void => {
    const data = this.store.data();
    if (!data) return;
    const t = this.t();
    downloadCsv(
      this.document,
      `${this.fileName(data.period)}.csv`,
      platformCsv(
        this.printHeader(),
        this.kpis(),
        kpiCsvHeader(totalsPanel(data), t, this.formats()),
        this.sorted(),
        t,
      ),
    );
  };

  /** PDF: a hora de agora na cabeceira impresa, as minigráficas como imaxes e imprimir. */
  protected readonly printPanel = (): Promise<void> => {
    this.generatedAt.set(new Date());
    return printWithChartImages(this.document, 'panel', undefined, () => this.cdr.detectChanges());
  };

  /** `indicadores-plataforma-2026-11`. */
  private fileName(period: Parameters<typeof periodFile>[0]): string {
    return ['indicadores', 'plataforma', periodFile(period)].join('-');
  }

  /** Ctrl+P sen pasar por «Exportar»: o mesmo PDF, coa hora de agora na cabeceira. */
  private printWithoutExport(): void {
    const view = this.document.defaultView;
    if (!view) return;
    let undo: (() => void) | null = null;
    const before = () => {
      if (!this.store.data()) return;
      this.generatedAt.set(new Date());
      undo = preparePanelPrint(this.document, () => this.cdr.detectChanges());
    };
    const after = () => {
      undo?.();
      undo = null;
    };
    view.addEventListener('beforeprint', before);
    view.addEventListener('afterprint', after);
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('beforeprint', before);
      view.removeEventListener('afterprint', after);
      after();
    });
  }

  private async load(filters: IndicatorFilters): Promise<void> {
    const announce = this.changedByUser;
    this.changedByUser = false;
    await this.store.load(filters);
    if (this.store.state() !== 'ready') return;
    this.generatedAt.set(new Date());
    if (announce) {
      const period = this.periodText();
      const lang = this.language.lang();
      this.announcement.set(
        this.t()('platformIndicators.status', {
          period: period.charAt(0).toLocaleLowerCase(lang) + period.slice(1),
        }),
      );
    }
  }
}
