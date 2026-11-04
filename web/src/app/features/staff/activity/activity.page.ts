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
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { formatShort } from '../../../shared/ui/calendar/local-date';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { downloadCsv } from '../../../shared/ui/export-menu/csv';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { formatInstant, inMadrid, madridToday } from '../../../shared/ui/calendar/madrid-time';
import type { ActivityFilters, ActivityScope } from './activity';
import {
  AREAS,
  NO_FILTERS,
  OUTCOME_TONE,
  actionKey,
  actorView,
  apiParams,
  filtersFromParams,
  filtersToParams,
  hasFilters,
  targetView,
} from './activity';
import { ActivityFiltersComponent } from './activity-filters.component';
import { blockedCsv, blockedDays, firstVisibleDay, periodLabel, retentionPeriods } from './activity-retention';
import type { ActivityRow } from './activity-table.component';
import { ActivityTableComponent } from './activity-table.component';
import type { ActivityRequest } from './activity.store';
import { ActivityStore } from './activity.store';
import type { ExportBlockedData, ExportBlockedResult } from './export-blocked-dialog.component';
import { ExportBlockedDialogComponent } from './export-blocked-dialog.component';

/** Código do 400 dun período que non vale: vai co seu texto, non coma un fallo de carga. */
const RANGE_INVALID = 'audit.range-invalid';

/**
 * «Rexistro de actividade» (D4-16; plan do dominio 4, §1.2.12): o do concello
 * (`/:concello/xestion/rexistro`, só o xestor) e o da plataforma (`/plataforma/rexistro`,
 * `data.scope`). Só de lectura, do máis novo ao máis vello, cos filtros no URL. «Cargar máis» trae
 * a páxina seguinte e leva o foco á primeira fila nova; cantas hai á vista anúnciase en
 * `role="status"`. Datas e horas no reloxo de Europe/Madrid.
 *
 * Bloqueo (ADR-0020, revisión do 2026-10-29): unha liña di que o anterior ao tramo visible (un ano
 * por defecto, da API de privacidade) non se mostra, e o calendario non vai máis atrás. No
 * concello, «Exportar bloqueados» abre o diálogo do motivo e a referencia e descarga o CSV.
 */
@Component({
  selector: 'furar-activity-page',
  imports: [
    TranslocoPipe,
    ActivityFiltersComponent,
    ActivityTableComponent,
    ButtonDirective,
    CalloutComponent,
    PageHeaderComponent,
    StatusStateComponent,
  ],
  providers: [ActivityStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activity.page.html',
})
export class ActivityPage {
  /** Dentro da sección «Concello» (`data.section`): o h1 é o da sección. */
  protected readonly inSection = inject(ActivatedRoute).snapshot.data['section'] === true;

  protected readonly store = inject(ActivityStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly dialogs = inject(DialogService);
  private readonly staff = inject(StaffAccessService);
  /**
   * Exportar os bloqueados (LOPDGDD art. 32) é só do xestor; a plataforma le o rexistro do concello
   * (ADR-0028, revisión do 2026-11-03) pero non o exporta.
   */
  protected readonly canExportBlocked = computed(() => this.scope === 'municipality' && this.staff.canManage());

  protected readonly scope: ActivityScope =
    this.route.snapshot.data['scope'] === 'platform' ? 'platform' : 'municipality';
  protected readonly today = madridToday();
  private readonly slug = computed(() => this.municipality.slug());
  private readonly params = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });
  protected readonly filters = computed(() => filtersFromParams(this.params(), this.scope));
  protected readonly filtered = computed(() => hasFilters(this.filters()));
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
  protected readonly firstVisible = computed(() => firstVisibleDay(this.store.retention(), this.today));

  protected readonly periodText = computed(() => {
    const { from, to } = this.filters();
    return from && to ? `${formatShort(from)} – ${formatShort(to)}` : null;
  });
  protected readonly areaOptions = computed<SelectOption<string>[]>(() => {
    this.lang();
    const t = (key: string) => this.transloco.translate(key);
    return [
      { value: '', label: t('activity.filters.allAreas') },
      ...AREAS.map((a) => ({ value: a, label: t('activity.areas.' + a) })),
    ];
  });
  /**
   * O persoal que xa entrou (activo ou desactivado): quen nunca aceptou non fixo nada. A
   * anonimizada, sen nome nin correo, sae como «Persoa anonimizada»; a de identidade bloqueada,
   * «Persoa desactivada».
   */
  protected readonly peopleOptions = computed<SelectOption<string>[]>(() => {
    this.lang();
    const people = this.store
      .members()
      .filter((m) => m.status === 'ACTIVE' || (m.status === 'DEACTIVATED' && !!m.acceptedAt));
    return [
      { value: '', label: this.transloco.translate('activity.filters.allPeople') },
      ...people.map((m) => {
        const name = m.anonymized
          ? this.transloco.translate('activity.actor.anonymized')
          : m.blocked
            ? this.transloco.translate('activity.actor.blocked')
            : m.name?.trim() || m.email || '';
        return {
          value: m.id,
          label: m.status === 'DEACTIVATED' ? this.transloco.translate('activity.filters.deactivated', { name }) : name,
        };
      }),
    ];
  });
  protected readonly rows = computed<ActivityRow[]>(() =>
    this.store.items().map((event) => ({
      id: event.id,
      at: event.at,
      when: formatInstant(event.at) ?? event.at,
      actionKey: actionKey(event.action),
      action: event.action,
      actor: actorView(event.actor),
      target: targetView(event, this.scope, this.slug(), this.store.facilities(), this.staff.canViewBookings()),
      municipality: event.municipality ?? null,
      outcome: event.outcome,
      tone: OUTCOME_TONE[event.outcome],
    })),
  );
  /** Un 400 do período (`audit.range-invalid`): o seu texto, cos días que admite a API. */
  protected readonly rangeError = computed(() => {
    this.lang();
    const problem = this.store.problem();
    if (problem?.code !== RANGE_INVALID) return '';
    return problem.translated || this.transloco.translate(problem.messageKey, problem.params);
  });

  /** «Cargar máis» fallou: a lista que hai queda e avísase. */
  protected readonly moreError = computed(() => {
    const problem = this.store.problem();
    return this.store.state() === 'ready' && !!problem && problem.code !== RANGE_INVALID;
  });

  constructor() {
    void this.store.loadRetention();
    effect(() => {
      const filters = this.filters();
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

  protected onFilters(filters: ActivityFilters): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: filtersToParams(filters) });
  }

  protected retry(): void {
    void this.load(this.filters(), this.slug());
  }

  /** «Cargar máis»: a páxina seguinte e o foco na súa primeira fila. */
  protected async more(): Promise<void> {
    const before = this.store.items().length;
    const added = await this.store.loadMore();
    if (!added) return;
    this.announce(this.store.next() ? 'activity.status.more' : 'activity.status.end', added);
    afterNextRender(() => this.document.querySelector<HTMLElement>(`[data-row="${before}"]`)?.focus(), {
      injector: this.injector,
    });
  }

  /**
   * «Exportar bloqueados»: o diálogo do motivo e, se exporta, o CSV (ou o aviso de que non había
   * ningún). Despois recarga a lista, que xa leva a exportación (`audit.export-blocked`).
   */
  protected async openExport(): Promise<void> {
    const slug = this.slug();
    if (!slug || this.exporting()) return;
    this.exporting.set(true);
    const days = blockedDays(this.store.retention(), this.today);
    const data: ExportBlockedData = {
      first: days.first,
      last: days.last,
      export: (body) => this.store.exportBlocked(slug, body),
    };
    const ref = this.dialogs.open<ExportBlockedResult, ExportBlockedData>(ExportBlockedDialogComponent, { data });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    this.exporting.set(false);
    if (!result) return;
    if (result.items.length) {
      const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
      const name = `rexistro-bloqueados-${slug}-${inMadrid(result.from).date}-${inMadrid(result.to).date}.csv`;
      downloadCsv(this.document, name, blockedCsv(result.items, t, this.store.facilities(), slug));
    }
    await this.load(this.filters(), slug);
    this.announcement.set(
      result.items.length
        ? this.transloco.translate('activity.exportBlocked.done', { count: result.items.length })
        : this.transloco.translate('activity.exportBlocked.empty'),
    );
  }

  private async load(filters: ActivityFilters, slug: string | null): Promise<void> {
    this.announcement.set('');
    const params = apiParams(filters);
    const request: ActivityRequest =
      this.scope === 'platform'
        ? { scope: 'platform', params: apiParams({ ...NO_FILTERS, from: filters.from, to: filters.to }) }
        : { scope: 'municipality', params: { slug: slug ?? '', ...params } };
    await this.store.load(request);
    if (this.store.state() === 'ready' && this.store.items().length) {
      this.announce('activity.status.count', this.store.items().length);
    }
  }

  private announce(key: string, count: number): void {
    this.announcement.set(this.transloco.translate(key, { count }));
  }
}
