// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { placeParams } from '../../shared/i18n/place-name';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { formatDateLong, formatDayShortMonth, localToday } from '../../shared/ui/calendar/local-date';
import { CalloutComponent } from '../../shared/ui/callout/callout.component';
import { CardComponent } from '../../shared/ui/card/card.component';
import type { DataTableColumn } from '../../shared/ui/data-table/data-table.component';
import { DataTableComponent } from '../../shared/ui/data-table/data-table.component';
import { shortDate } from '../../shared/ui/date-filter/date-filter';
import { DateFilterComponent } from '../../shared/ui/date-filter/date-filter.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { DisclosureComponent } from '../../shared/ui/disclosure/disclosure.component';
import { HelpTipComponent } from '../../shared/ui/help-tip/help-tip.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { RequiredMarkComponent } from '../../shared/ui/required/required.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../shared/ui/tabs/tabs.component';
import type { ValueListItem } from '../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../shared/ui/value-list/value-list.component';
import type { OpenDataDialogData, OpenDataDialogResult } from './open-data-dialog.component';
import { OpenDataDialogComponent } from './open-data-dialog.component';
import type { DatasetKey, DownloadFormat } from './open-data';
import {
  DATASETS,
  columnTables,
  datasetFromUrl,
  datasetUrl,
  detailRows,
  occupancyPresets,
  rangeError,
} from './open-data';
import type { OpenDataSummary } from './open-data.store';
import { OpenDataStore } from './open-data.store';

/** Por baixo deste ancho, a data curta («11 nov 2026»). */
const MOBILE = '(max-width: 639px)';

let nextId = 0;

/**
 * «Datos abertos» (ADR-0034, punto 24): a mesma páxina para un concello
 * (`/:concello/datos-abertos`) e para todos (`/datos-abertos`, `data.scope = 'all'`). Título,
 * frase, tres pestanas de liña (a escollida no URL, `?conxunto=`), a frase do conxunto e unha
 * tarxeta con «Actualizado o…», «Descargar» (na ocupación, co filtro «Datas» ao lado) e os
 * pregables «Detalles» e «Columnas»; ao final, en pequeno, «Sen datos de persoas», «Uso libre» e
 * «Portais de datos abertos».
 */
@Component({
  selector: 'furar-open-data-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    CardComponent,
    DataTableComponent,
    DateFilterComponent,
    DisclosureComponent,
    HelpTipComponent,
    IconComponent,
    PageHeaderComponent,
    RequiredMarkComponent,
    StatusStateComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  providers: [OpenDataStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './open-data.page.html',
})
export class OpenDataPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(OpenDataStore);
  private readonly dialogs = inject(DialogService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly all = this.route.snapshot.data['scope'] === 'all';
  protected readonly slug = computed(() => (this.all ? null : this.municipality.slug()));
  protected readonly datasets = DATASETS;
  protected readonly ids = { dates: `furar-open-data-${nextId}-dates`, error: `furar-open-data-${nextId++}-error` };

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly summary = signal<OpenDataSummary | null>(null);
  protected readonly today = localToday();

  private readonly query = toSignal(this.route.queryParamMap);
  protected readonly selected = computed<DatasetKey>(() => datasetFromUrl(this.query()?.get('conxunto')));

  protected readonly from = signal<IsoDate | null>(null);
  protected readonly to = signal<IsoDate | null>(null);
  private readonly submitted = signal(false);
  private readonly mobile = signal(false);

  protected readonly placeParams = computed(() => {
    this.langChanges();
    return placeParams(this.municipality.name() ?? '', this.language.lang());
  });

  protected readonly dataset = computed(() => {
    const summary = this.summary();
    return summary?.datasets.find((d) => d.key === this.selected()) ?? null;
  });

  protected readonly updated = computed(() => {
    this.langChanges();
    const day = this.dataset()?.updatedOn;
    if (!day) return null;
    const lang = this.language.lang();
    return this.mobile() ? formatDayShortMonth(day, lang) : formatDateLong(day, lang);
  });

  protected readonly presets = computed(() => {
    const summary = this.summary();
    return summary ? occupancyPresets(this.today, summary.occupancy.availableFrom, this.all) : [];
  });

  /** «Como moito…» sae ao momento; «Escolle…», ao premer «Descargar». */
  protected readonly datesError = computed(() => {
    const summary = this.summary();
    if (!summary || this.selected() !== 'OCCUPANCY') return null;
    const error = rangeError(this.from(), this.to(), summary.occupancy.maxMonths);
    if (error === 'tooLong') return summary.occupancy.maxMonths === 1 ? 'tooLongOne' : 'tooLong';
    return error === 'required' && this.submitted() ? 'required' : null;
  });

  protected readonly details = computed<ValueListItem[]>(() => {
    this.langChanges();
    return detailRows(this.selected(), (key) => this.transloco.translate(key));
  });

  protected readonly columnHeaders = computed<DataTableColumn[]>(() => {
    this.langChanges();
    return [
      { label: this.transloco.translate('openData.columns.name') },
      { label: this.transloco.translate('openData.columns.content') },
    ];
  });

  /** As táboas de «Columnas»: unha (dúas nas instalacións, cos campos propios entre elas). */
  protected readonly columnTables = computed(() => {
    this.langChanges();
    return columnTables(
      this.selected(),
      this.all,
      this.summary()?.customFields ?? [],
      this.language.lang(),
      (key, params) => this.transloco.translate(key, params),
    );
  });

  protected readonly owner = computed(() => {
    this.langChanges();
    const name = this.summary()?.legalEntityName;
    return name || this.transloco.translate('openData.ownerDefault', this.placeParams());
  });

  /** A ficha de catálogo, co URL directo da API (para os portais). */
  protected readonly catalogUrl = computed(() => {
    const slug = this.slug();
    return slug ? `/api/v1/municipalities/${slug}/open-data/dcat.rdf` : '/api/v1/catalog/open-data/dcat.rdf';
  });

  constructor() {
    const view = this.document.defaultView;
    const media = view?.matchMedia?.(MOBILE);
    if (media) {
      this.mobile.set(media.matches);
      const onChange = (e: MediaQueryListEvent) => this.mobile.set(e.matches);
      media.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => media.removeEventListener('change', onChange));
    }
    effect(() => {
      // O concello pode chegar despois (resolvedor): cargar cando xa se sabe.
      if (!this.all && !this.slug()) return;
      void this.load();
    });
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      const summary = await this.store.summary(this.slug());
      this.summary.set(summary);
      this.from.set(summary.occupancy.defaultFrom);
      this.to.set(summary.occupancy.defaultTo);
      this.state.set('ready');
    } catch (error) {
      if (asProblem(error).status === 404) {
        await this.router.navigate(['/erro', '404'], { skipLocationChange: true });
        return;
      }
      this.state.set('error');
    }
  }

  protected select(key: DatasetKey | null): void {
    if (!key) return;
    this.submitted.set(false);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { conxunto: datasetUrl(key) },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected tabLabel(key: DatasetKey): string {
    return 'openData.tabs.' + (DATASETS.find((d) => d.key === key)?.tab ?? 'facilities');
  }

  protected description(key: DatasetKey): string {
    return 'openData.' + (DATASETS.find((d) => d.key === key)?.tab ?? 'facilities') + '.description';
  }

  protected download(): void {
    const summary = this.summary();
    const dataset = this.dataset();
    if (!summary || !dataset) return;
    const key = this.selected();
    let range: { from: IsoDate; to: IsoDate } | null = null;
    if (key === 'OCCUPANCY') {
      this.submitted.set(true);
      const from = this.from();
      const to = this.to();
      if (!from || !to || rangeError(from, to, summary.occupancy.maxMonths)) return;
      range = { from, to };
    }
    const tr = (k: string, p?: Record<string, unknown>) => this.transloco.translate(k, p);
    const name = tr(this.tabLabel(key));
    const period = range
      ? range.from === range.to
        ? tr('openData.dialog.periodOne', { from: shortDate(range.from) })
        : tr('openData.dialog.period', { from: shortDate(range.from), to: shortDate(range.to) })
      : '';
    const withPeriod = period ? `${name} · ${period}` : name;
    const subtitle = this.all
      ? tr('openData.dialog.subtitleAll', { dataset: withPeriod })
      : tr('openData.dialog.subtitle', { dataset: withPeriod, name: this.municipality.name() ?? '' });
    const data: OpenDataDialogData = {
      slug: this.slug(),
      dataset: key,
      formats: dataset.formats as DownloadFormat[],
      subtitle,
      lang: this.language.lang(),
      range,
      bytesPerDay: range ? summary.occupancy.bytesPerDay : null,
      onGone: () => void this.load(),
    };
    this.dialogs.open<OpenDataDialogResult, OpenDataDialogData, OpenDataDialogComponent>(OpenDataDialogComponent, {
      data,
      injector: this.injector,
    });
  }
}
