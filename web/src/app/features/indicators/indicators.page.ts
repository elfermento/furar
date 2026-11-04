// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { OnInit } from '@angular/core';
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
import { LanguageService } from '../../core/i18n/language.service';
import { placeOf } from '../../shared/i18n/place-name';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { preparePanelPrint, printWithChartImages } from '../../shared/print/print-charts';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { downloadCsv } from '../../shared/ui/export-menu/csv';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import type { Translate } from './conclusions';
import { EMPTY } from './conclusions';
import type { ChartExport } from './indicator-charts.component';
import { IndicatorChartsComponent } from './indicator-charts.component';
import type { FilterTexts } from './indicator-filters.component';
import { IndicatorFiltersComponent } from './indicator-filters.component';
import type { FilterOption, IndicatorFilters } from './indicator-filters';
import { facilityOptions, filtersFromParams, filtersToParams, sameFilters, useOptions } from './indicator-filters';
import type { EvolutionDialogData } from './indicator-evolution.component';
import { IndicatorEvolutionComponent } from './indicator-evolution.component';
import { evolutionView } from './indicator-evolution';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { IndicatorFormats } from './indicator-format';
import type { KpiOpen } from './indicator-kpis.component';
import { IndicatorKpisComponent } from './indicator-kpis.component';
import { kpiCsvHeader, kpiViews } from './indicator-kpis';
import { csvName, panelCsv, periodTitle } from './indicator-period';
import { chartViews, emptyPanel } from './indicator-views';
import { IndicatorsStore } from './indicators.store';

/**
 * «Indicadores» do concello (`/:concello/xestion/indicadores`, D3-08; maqueta 31, opción A; ADR-0026):
 * a fila de filtros (no URL: volver atrás restaura), as cinco cifras e os seis gráficos, cada un coa
 * súa táboa. Só a xestión (`managerGuard`). Ao cambiar un filtro, o resultado anúnciase nun
 * `role="status"`; a cabeceira impresa (`.f-print-header`, oculta en pantalla) leva os filtros escritos.
 */
@Component({
  selector: 'furar-indicators-page',
  imports: [
    TranslocoPipe,
    IndicatorChartsComponent,
    IndicatorFiltersComponent,
    IndicatorKpisComponent,
    ProblemAlertComponent,
    StatusStateComponent,
  ],
  providers: [IndicatorsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './indicators.page.html',
})
export class IndicatorsPage implements OnInit {
  protected readonly store = inject(IndicatorsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly dialogs = inject(DialogService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.selectTranslation());
  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });

  protected readonly today = madridToday();
  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly filters = computed(() => filtersFromParams(this.params()));
  private readonly ready = signal(false);
  /** Cando se xerou o que se ve (a cabeceira impresa). */
  private readonly generatedAt = signal(new Date());
  /** O anuncio do resultado dun filtro (non o da primeira carga). */
  protected readonly announcement = signal('');
  private changedByUser = false;

  private readonly formats = computed(() => new IndicatorFormats(this.language.lang()));
  private readonly t = computed<Translate>(() => {
    this.translations();
    return (key, params) => this.transloco.translate(key, params);
  });

  protected readonly kpis = computed(() => {
    const panel = this.store.panel();
    return panel ? kpiViews(panel, this.t(), this.formats()) : [];
  });
  protected readonly charts = computed(() => {
    const panel = this.store.panel();
    return panel ? chartViews(panel, this.t(), this.formats()) : [];
  });
  /** Sen nada que contar no período: o panel cámbiase por un estado baleiro (as cifras quedan). */
  protected readonly empty = computed(() => {
    const panel = this.store.panel();
    return panel ? emptyPanel(panel) : false;
  });
  protected readonly emptyText = computed(() => this.t()(EMPTY.key));

  private readonly facilities = computed(() => facilityOptions(this.store.options(), this.language.lang()));
  private readonly uses = computed(() =>
    useOptions(this.store.options(), this.filters().facilityId, this.language.lang()),
  );
  protected readonly filterTexts = computed<FilterTexts>(() => {
    const t = this.t();
    const f = this.formats();
    const { from, to } = this.filters();
    const labelled = (option: FilterOption, key: string) => ({
      value: option.value,
      label: option.retired ? t(key, { name: option.label }) : option.label,
    });
    return {
      month: f.monthTitle(this.today),
      range: from && to ? t('indicators.filters.range', { from: f.shortDate(from), to: f.shortDate(to) }) : '',
      facilities: this.facilities().map((o) => labelled(o, 'indicators.filters.withdrawn')),
      uses: this.uses().map((o) => labelled(o, 'indicators.filters.archived')),
    };
  });

  /** Período, instalación e uso en texto (cabeceira impresa e anuncio). */
  private readonly described = computed(() => {
    const panel = this.store.panel();
    const loaded = this.store.loaded();
    if (!panel || !loaded) return null;
    const t = this.t();
    const facility = this.facilities().find((o) => o.value === loaded.facilityId)?.label;
    return {
      period: periodTitle(panel.period, t, this.formats()),
      facility: facility ?? t('indicators.filters.allFacilities'),
      use: loaded.use ?? t('indicators.filters.allUses'),
    };
  });
  protected readonly printHeader = computed(() => this.headerAt(this.generatedAt()));

  /** A cabeceira impresa cos filtros que se ven e o momento `generated`. */
  private headerAt(generated: Date): string {
    const described = this.described();
    if (!described) return '';
    return this.t()('indicators.print.header', {
      ...described,
      ofName: placeOf(this.municipality.name() ?? '', this.language.lang()),
      generated: this.formats().dateTime(generated),
    });
  }

  constructor() {
    effect(() => {
      const filters = this.filters();
      const slug = this.slug();
      if (!this.ready() || !slug) return;
      untracked(() => void this.load(slug, filters));
    });
    this.printWithoutExport();
  }

  /**
   * Ctrl+P (ou «Imprimir» do navegador) sen pasar por «Exportar»: o mesmo PDF do panel, cos
   * gráficos como imaxes en claro e a hora de agora na cabeceira; desfaise en `afterprint`.
   */
  private printWithoutExport(): void {
    const view = this.document.defaultView;
    if (!view) return;
    let undo: (() => void) | null = null;
    const before = () => {
      if (!this.store.panel()) return;
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

  ngOnInit(): void {
    // As opcións primeiro: o uso do URL vai polo nome e o panel pide os seus ids.
    void this.store.loadOptions(this.slug()).then(() => this.ready.set(true));
  }

  protected change(filters: IndicatorFilters): void {
    if (sameFilters(filters, this.filters())) return;
    this.changedByUser = true;
    void this.router.navigate([], { relativeTo: this.route, queryParams: filtersToParams(filters) });
  }

  /** O modal coa evolución da cifra, cunha transición desde a súa tarxeta (o foco volve a ela). */
  protected openKpi(open: KpiOpen): void {
    const panel = this.store.panel();
    const kpi = this.kpis().find((k) => k.id === open.id);
    if (!panel || !kpi) return;
    const view = evolutionView(panel, kpi, this.t(), this.formats());
    this.store.selectedKpi.set(open.id);
    const ref = this.dialogs.open<unknown, EvolutionDialogData>(IndicatorEvolutionComponent, {
      from: open.element,
      data: {
        view,
        header: (generated) => this.headerAt(generated),
        csvName: csvName(this.slug(), panel.period, view),
      },
    });
    ref.closed.subscribe(() => this.store.selectedKpi.set(null));
  }

  protected readonly exportPanelCsv = (): void => {
    const panel = this.store.panel();
    if (!panel) return;
    const heading = [this.printHeader()];
    downloadCsv(
      this.document,
      csvName(this.slug(), panel.period),
      panelCsv(heading, this.kpis(), this.charts(), this.t(), kpiCsvHeader(panel, this.t(), this.formats())),
    );
  };

  protected readonly exportChartCsv = ({ view }: ChartExport): void => {
    const panel = this.store.panel();
    if (!panel) return;
    downloadCsv(this.document, csvName(this.slug(), panel.period, view), [[this.printHeader()], [], ...view.csv]);
  };

  protected readonly printPanel = (): Promise<void> => this.print('panel');

  protected readonly printChart = ({ card }: ChartExport): Promise<void> => this.print('card', card);

  /** PDF: a hora de agora na cabeceira impresa, os gráficos do alcance como imaxes e imprimir. */
  private print(scope: 'panel' | 'card', target?: HTMLElement): Promise<void> {
    this.generatedAt.set(new Date());
    return printWithChartImages(this.document, scope, target, () => this.cdr.detectChanges());
  }

  private async load(slug: string, filters: IndicatorFilters): Promise<void> {
    const announce = this.changedByUser;
    this.changedByUser = false;
    await this.store.load(slug, filters);
    if (this.store.state() !== 'ready') return;
    this.generatedAt.set(new Date());
    const described = this.described();
    if (announce && described) this.announcement.set(this.announce(described));
  }

  /**
   * «Indicadores actualizados: outubro de 2026 · Todas as instalacións · Todos os usos.», co
   * período en minúscula (vai tras os dous puntos) e, sen nada no período, «Sen reservas…».
   */
  private announce(described: { period: string; facility: string; use: string }): string {
    const lang = this.language.lang();
    const period = described.period.charAt(0).toLocaleLowerCase(lang) + described.period.slice(1);
    return this.t()(this.empty() ? 'indicators.status.empty' : 'indicators.status.ready', { ...described, period });
  }
}
