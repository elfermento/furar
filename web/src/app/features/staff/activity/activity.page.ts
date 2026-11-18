// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { ChipListComponent } from '../../../shared/ui/chip-list/chip-list.component';
import { ColumnWidths, columnWidthsKey } from '../../../shared/ui/column-resize/column-widths';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { SearchBarComponent } from '../../../shared/ui/search-bar/search-bar.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type { ActivityFilters } from './activity-filters';
import { apiParams, matchesLocally, matchesText } from './activity-filters';
import { ActivityColumnComponent } from './activity-column.component';
import { ActivityTableComponent } from './activity-table.component';
import { ActivityFiltersState } from './activity-filters.state';
import type { ActivityDetail } from './activity-detail-dialog.component';
import { ActivityDetailDialogComponent, buildDetail } from './activity-detail-dialog.component';
import { firstVisibleDay, periodLabel, retentionPeriods } from './activity-retention';
import type { Translate } from './activity-rows';
import { groupEvents } from './activity-rows';
import type { ActivityRow } from './activity-view';
import { buildColumns, buildRow, filterChips, peopleOptions, periodText } from './activity-view';
import type { ActivityRequest } from './activity.store';
import { ActivityStore } from './activity.store';
import { exportBlockedFlow } from './export-blocked-dialog.component';

/**
 * «Rexistro de actividade»: o do concello
 * (`/:concello/xestion/concello/rexistro`) e o da plataforma (`/plataforma/rexistro`,
 * `data.scope`). Táboa densa, unha cousa por columna (Data, Persoa, Rol, Acción, Onde, Resultado),
 * do máis novo ao máis vello.
 *
 * Filtros só nas cabeceiras (`furar-column-filter`): cada unha abre un panel coas opcións e cantas
 * hai nas filas cargadas; con filtro, un punto na cabeceira e un chip enriba («Borrar filtros»
 * quítaos todos). Ademais, «Buscar no rexistro» (o texto das filas cargadas). Todo vai no URL. No
 * concello a API filtra período, persoa, rol, acción, instalación e resultado; na plataforma, só o
 * período, e o resto fíltrase nas filas cargadas.
 * No móbil, tarxetas de dúas liñas e os filtros nunha fila de pílulas.
 *
 * Agrupado (`groupEvents`): as accións seguidas da mesma persoa na mesma instalación con menos de
 * 30 minutos entre elas son unha fila. Premer unha fila (ou o seu resumo, co teclado) abre o
 * detalle nunha capa (folla no móbil). «Cargar máis» trae a páxina seguinte e leva o foco á fila
 * da primeira acción nova.
 *
 * Columnas: só o día na Data (a hora, no detalle), o correo na Persoa e os roles en chip. Cada
 * cabeceira ensánchase arrastrando o seu bordo (ou co teclado); os anchos gárdanse por persoa e
 * por táboa e un dobre clic no bordo devolve o ancho desa columna.
 *
 * Bloqueo (ADR-0020, revisión do 2026-10-29): unha liña discreta debaixo da táboa di que o
 * anterior ao tramo visible non se mostra e o calendario non vai máis atrás. No concello,
 * «Exportar bloqueados».
 */
@Component({
  selector: 'furar-activity-page',
  imports: [
    TranslocoPipe,
    ActivityColumnComponent,
    ActivityTableComponent,
    ButtonDirective,
    CalloutComponent,
    ChipListComponent,
    PageHeaderComponent,
    SearchBarComponent,
    StatusStateComponent,
  ],
  providers: [ActivityStore, ActivityFiltersState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activity.page.html',
})
export class ActivityPage {
  protected readonly f = inject(ActivityFiltersState);
  /** Dentro da sección «Concello» (`data.section`): o h1 é o da sección. */
  protected readonly inSection = inject(ActivatedRoute).snapshot.data['section'] === true;

  protected readonly store = inject(ActivityStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  protected readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  protected readonly dialogs = inject(DialogService);
  private readonly staff = inject(StaffAccessService);
  /**
   * Exportar os bloqueados (LOPDGDD art. 32) é só do xestor; a plataforma le o rexistro do concello
   * (ADR-0028, revisión do 2026-11-03) pero non o exporta.
   */
  protected readonly canExportBlocked = computed(() => this.scope === 'municipality' && this.staff.canManage());

  protected readonly scope = this.f.scope;
  private readonly session = inject(SessionService);
  /** Anchos das columnas (px): a Acción colle o que sobra; gárdanse por persoa e por rexistro. */
  protected readonly widths = new ColumnWidths({
    columns: [
      { key: 'date', width: 120, min: 96 },
      { key: 'person', width: 224, min: 112 },
      { key: 'role', width: 136, min: 96 },
      { key: 'action', width: null },
      { key: this.scope === 'platform' ? 'municipality' : 'place', width: 160, min: 96 },
      { key: 'outcome', width: 120, min: 96 },
    ],
    storageKey: () => columnWidthsKey(this.session.subject(), 'activity-' + this.scope),
    flexMin: 200,
  });
  protected readonly today = this.f.today;
  protected readonly filters = this.f.filters;
  private readonly slug = computed(() => this.municipality.slug());
  /** Para que os textos cambien co idioma (cando a tradución xa cargou). */
  private readonly lang = toSignal(this.transloco.selectTranslation());
  protected readonly announcement = signal('');
  protected readonly exporting = signal(false);
  /** «Os eventos de hai máis de 1 ano non se mostran»: o período visible en texto. */
  protected readonly visibleText = computed(() => {
    this.lang();
    const label = periodLabel(retentionPeriods(this.store.retention()).visible);
    return this.transloco.translate(label.key, { count: label.count });
  });
  /** O período para o filtro da columna Data: o calendario non vai antes do primeiro día visible. */
  protected readonly range = computed(() => ({
    from: this.filters().from,
    to: this.filters().to,
    today: this.today,
    min: firstVisibleDay(this.store.retention(), this.today),
  }));

  protected readonly t: Translate = (key, params) => this.transloco.translate(key, params);

  /** As accións cargadas que pasan os filtros que a API non fai. */
  private readonly events = computed(() =>
    this.store.items().filter((event) => matchesLocally(event, this.filters(), this.scope)),
  );

  protected readonly rows = computed<ActivityRow[]>(() => {
    this.lang();
    const facilities = this.store.facilities();
    const text = this.filters().text;
    return groupEvents(this.events())
      .map((events) => buildRow(events, this.scope, facilities, this.t))
      .filter((row) =>
        matchesText([row.person, row.name, row.email, row.role, row.summary, row.place, row.when, ...row.search], text),
      );
  });

  protected readonly columns = computed(() => {
    this.lang();
    return buildColumns({
      filters: this.filters(),
      scope: this.scope,
      today: this.today,
      events: this.events(),
      people: peopleOptions(this.store.members(), this.t),
      facilities: this.store.facilities(),
      t: this.t,
    });
  });

  protected readonly chips = computed(() => {
    this.lang();
    return filterChips(this.columns(), periodText(this.filters(), this.today, this.t));
  });

  /** O botón da folla do móbil: «Ver 12 accións». */
  /** Columnas con filtro posto (o número sobre «Filtros» no móbil). */
  protected readonly filterCount = computed(() => this.columns().filter((column) => column.marked).length);
  protected readonly doneLabel = computed(() => {
    this.lang();
    return this.t('activity.filters.show', { count: this.rows().length });
  });

  /** Un 400 do período: o seu texto, cos días que admite a API. */
  protected readonly rangeError = computed(() => {
    this.lang();
    const problem = this.store.rangeProblem();
    return problem ? problem.translated || this.t(problem.messageKey, problem.params) : '';
  });

  constructor() {
    void this.store.loadRetention();
    effect(() => {
      const filters = this.f.server();
      const slug = this.slug();
      if (this.scope === 'municipality' && !slug) return;
      untracked(() => void this.load(filters, slug));
    });
    if (this.scope === 'municipality') {
      effect(() => {
        const slug = this.slug();
        if (slug) untracked(() => void this.store.loadOptions(slug));
      });
    }
  }

  /** Abre o detalle da fila (un clic na fila; o botón do resumo chámao co teclado). */
  protected open(row: ActivityRow, event?: Event): void {
    if (event && (event.target as HTMLElement | null)?.closest?.('a, button')) return;
    const detail = buildDetail(row, {
      scope: this.scope,
      slug: this.slug(),
      facilities: this.store.facilities(),
      canViewBookings: this.staff.canViewBookings(),
      t: this.t,
    });
    this.dialogs.open<unknown, ActivityDetail>(ActivityDetailDialogComponent, {
      data: detail,
      sheet: true,
      from: this.document.querySelector<HTMLElement>(`[data-open="${row.id}"]`),
    });
  }

  protected retry(): void {
    void this.load(this.f.server(), this.slug());
  }

  /** «Cargar máis»: a páxina seguinte e o foco na fila da súa primeira acción. */
  protected async more(): Promise<void> {
    const before = this.store.items().length;
    const added = await this.store.loadMore();
    if (!added) return;
    this.announce(this.store.next() ? 'activity.status.more' : 'activity.status.end', added);
    const first = this.store.items()[before]?.id;
    afterNextRender(
      () => {
        const row = this.rows().find((r) => r.events.some((e) => e.id === first));
        if (row) this.document.querySelector<HTMLElement>(`[data-open="${row.id}"]`)?.focus();
      },
      { injector: this.injector },
    );
  }

  /** «Exportar bloqueados»; despois recarga a lista, que xa leva a exportación (`audit.export-blocked`). */
  protected async openExport(): Promise<void> {
    const slug = this.slug();
    if (!slug || this.exporting()) return;
    this.exporting.set(true);
    const { dialogs, document, store, today, t } = this;
    const count = await exportBlockedFlow({ dialogs, document, store, slug, today, t });
    this.exporting.set(false);
    if (count === null) return;
    await this.load(this.f.server(), slug);
    this.announcement.set(t(count ? 'activity.exportBlocked.done' : 'activity.exportBlocked.empty', { count }));
  }

  private async load(filters: ActivityFilters, slug: string | null): Promise<void> {
    this.announcement.set('');
    const params = apiParams(filters, this.scope);
    const request: ActivityRequest =
      this.scope === 'platform'
        ? { scope: 'platform', params }
        : { scope: 'municipality', params: { slug: slug ?? '', ...params } };
    await this.store.load(request);
    const count = this.store.items().length;
    if (this.store.state() === 'ready' && count) this.announce('activity.status.count', count);
  }

  private announce(key: string, count: number): void {
    this.announcement.set(this.transloco.translate(key, { count }));
  }
}
