// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ManageNav } from '../../core/layout/manage-nav';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { openIncidentReport } from '../../shared/incident-report/open-incident-report';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { ColumnFilterComponent } from '../../shared/ui/column-filter/column-filter.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { SearchBarComponent } from '../../shared/ui/search-bar/search-bar.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import type { Incident, IncidentColumn, IncidentFilters } from './incidents';
import { COLUMNS, NO_FILTERS, STATUS_TONE, dayOf, optionsOf, reporterOf, toggled, visible } from './incidents';
import { IncidentsStore } from './incidents.store';

/** Query param co que se abre unha incidencia (a ligazón do correo «Nova incidencia»). */
const OPEN_PARAM = 'incidencia';

/**
 * Incidencias da xestión (ADR-0036): pestana de «Reservas» para Xestión e Recepción.
 * Táboa coas regras de Furar: filtros na cabeceira (no móbil, en «Filtros» da busca), busca
 * no texto e fila = detalle. «Nova incidencia» abre o mesmo diálogo ca o aviso da veciñanza, co
 * campo «Instalación».
 */
@Component({
  selector: 'furar-incidents-page',
  imports: [
    ButtonDirective,
    ColumnFilterComponent,
    IconComponent,
    PageHeaderComponent,
    SearchBarComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './incidents.page.html',
})
export class IncidentsPage {
  protected readonly store = inject(IncidentsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly lang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly underTabs = inject(ManageNav).underTabs;

  protected readonly columns = COLUMNS;
  protected readonly tone = STATUS_TONE;
  protected readonly dayOf = dayOf;
  protected readonly reporterOf = reporterOf;
  protected readonly filters = signal<IncidentFilters>(NO_FILTERS);
  protected readonly query = signal('');
  protected readonly creating = signal(false);

  protected readonly rows = computed(() => {
    this.lang();
    return visible(this.store.items(), this.filters(), this.query(), (incident) => this.searchText(incident));
  });
  /** Columnas con filtro posto (o número sobre «Filtros» no móbil). */
  protected readonly filterCount = computed(() => COLUMNS.filter((column) => this.filters()[column].length).length);
  protected readonly filtered = computed(
    () => !!this.query().trim() || COLUMNS.some((column) => this.filters()[column].length),
  );
  protected readonly options = computed(() => {
    this.lang();
    const items = this.store.items();
    const t = (key: string) => this.transloco.translate(key);
    return {
      date: optionsOf(items, 'date', (_, incident) => this.month(incident.createdAt)),
      facility: optionsOf(items, 'facility', (_, incident) => incident.facilityName),
      type: optionsOf(items, 'type', (value) => t('incident.types.' + value)),
      status: optionsOf(
        items,
        'status',
        (value) => t('incident.status.' + value),
        (value) => STATUS_TONE[value as Incident['status']],
      ),
      reporter: optionsOf(items, 'reporter', (value) => value),
    } satisfies Record<IncidentColumn, unknown>;
  });

  constructor() {
    effect(() => {
      const slug = this.municipality.slug();
      if (slug) untracked(() => void this.load(slug));
    });
  }

  protected typed(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected label(column: IncidentColumn): string {
    return this.transloco.translate('incident.list.col.' + column);
  }

  protected toggle(column: IncidentColumn, value: string): void {
    this.filters.update((filters) => toggled(filters, column, value));
  }

  protected clear(column: IncidentColumn): void {
    this.filters.update((filters) => ({ ...filters, [column]: [] }));
  }

  protected reporter(incident: Incident): string {
    if (incident.reporter.anonymized) return this.transloco.translate('incident.detail.anonymized');
    return reporterOf(incident) ?? this.transloco.translate('incident.detail.staff');
  }

  protected async load(slug: string): Promise<void> {
    await this.store.load(slug);
    const id = this.route.snapshot.queryParamMap.get(OPEN_PARAM);
    if (id) {
      void this.router.navigate([], { relativeTo: this.route, queryParams: { [OPEN_PARAM]: null }, replaceUrl: true });
      void this.open(id);
    }
  }

  protected async open(id: string): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    const { openIncidentDetail } = await import('./incident-detail-dialog.component');
    await openIncidentDetail(this.dialogs, { slug, id });
  }

  protected async create(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug || this.creating()) return;
    this.creating.set(true);
    try {
      const facilities = await this.store.facilities(slug);
      const sent = await openIncidentReport(this.dialogs, { slug, mode: 'staff', facilities });
      if (sent) await this.store.changed(slug);
    } finally {
      this.creating.set(false);
    }
  }

  protected retry(): void {
    const slug = this.municipality.slug();
    if (slug) void this.load(slug);
  }

  private month(iso: string): string {
    const date = new Date(iso);
    return new Intl.DateTimeFormat(this.lang(), { month: 'long', year: 'numeric', timeZone: 'Europe/Madrid' }).format(
      date,
    );
  }

  private searchText(incident: Incident): string {
    return [
      dayOf(incident.createdAt),
      incident.facilityName,
      this.transloco.translate('incident.types.' + incident.type),
      this.transloco.translate('incident.status.' + incident.status),
      this.reporter(incident),
      incident.description,
    ].join(' ');
  }
}
